package learn.gjx.tuku1.manager;

import com.qcloud.cos.COSClient;
import com.qcloud.cos.http.HttpMethodName;
import com.qcloud.cos.model.COSObject;
import learn.gjx.tuku1.api.aliyunai.AliyunOutpaintingClient;
import learn.gjx.tuku1.api.imagesearch.ImageSearchUrls;
import learn.gjx.tuku1.config.*;
import learn.gjx.tuku1.exception.*;
import learn.gjx.tuku1.model.entity.Picture;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;
import javax.imageio.*;
import javax.imageio.stream.ImageInputStream;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.net.*;
import java.nio.file.Files;
import java.util.*;
import java.util.concurrent.Semaphore;

@Component
public class AiEditingFiles {
    private final COSClient cos;
    private final CosClientConfig config;
    private final PrivatePictureFileManager privateFiles;
    private final AiEditingProperties ai;
    private final Semaphore transfers = new Semaphore(2);
    private final Semaphore decoders = new Semaphore(2);
    public AiEditingFiles(COSClient cos, CosClientConfig config, PrivatePictureFileManager privateFiles, AiEditingProperties ai) {
        this.cos=cos; this.config=config; this.privateFiles=privateFiles; this.ai=ai;
    }
    private String[] object(Picture p) {
        String url = p.getOriginalUrl() == null ? p.getUrl() : p.getOriginalUrl();
        if (p.getSpaceId()!=null) return new String[]{privateFiles.bucket(p.getSpaceId()), privateFiles.validatedKey(p.getSpaceId(),url)};
        try {
            String host = config.getBucket()+".cos."+config.getRegion()+".myqcloud.com";
            URI uri = URI.create(ImageSearchUrls.source(url,host));
            return new String[]{config.getBucket(),uri.getPath().substring(1)};
        } catch (RuntimeException e) { throw new BusinessException(ErrorCode.OPERATION_ERROR,"图片不是有效的托管文件"); }
    }
    /** Only called after explicit per-request consent; signed source is never returned to the browser. */
    public String providerSource(Picture p) {
        String[] object=object(p);
        return cos.generatePresignedUrl(object[0],object[1],new Date(System.currentTimeMillis()+10*60_000),HttpMethodName.GET).toString();
    }
    /** Same-origin authenticated crop input avoids weakening private bucket CORS/ACL. */
    public byte[] source(Picture p) {
        String[] object=object(p);
        if(!transfers.tryAcquire()) throw new BusinessException(ErrorCode.OPERATION_ERROR,"图片读取繁忙，请稍后重试");
        try (COSObject result=cos.getObject(object[0],object[1])) {
            return AliyunOutpaintingClient.readLimited(result.getObjectContent(),2*1024*1024);
        } catch (Exception e) { throw new BusinessException(ErrorCode.OPERATION_ERROR,"编辑图片读取失败，请检查存储配置"); }
        finally { transfers.release(); }
    }
    public String validateResult(String value) {
        try {
            URI uri=URI.create(value);
            if (value.length()>2048 || !"https".equals(uri.getScheme()) || uri.getUserInfo()!=null
                    || uri.getFragment()!=null || uri.getPort()!=-1 || !ai.getResultHosts().contains(uri.getHost())) throw new IllegalArgumentException();
            return value;
        } catch (RuntimeException e) { throw new BusinessException(ErrorCode.OPERATION_ERROR,"AI 返回了不可信的结果地址"); }
    }
    public byte[] result(String url) {
        validateResult(url);
        if(!transfers.tryAcquire()) throw new BusinessException(ErrorCode.OPERATION_ERROR,"图片读取繁忙，请稍后重试");
        HttpURLConnection c=null;
        try {
            c=openResultConnection(url);
            c.setInstanceFollowRedirects(false); c.setConnectTimeout(3000); c.setReadTimeout(8000); c.setUseCaches(false);
            if (c.getResponseCode()!=200) throw new IOException();
            try(InputStream in=c.getInputStream()) { return AliyunOutpaintingClient.readLimited(in,10*1024*1024); }
        } catch(IOException e) { throw new BusinessException(ErrorCode.OPERATION_ERROR,"AI 结果读取失败或已过期，请稍后重试查询"); }
        finally { if(c!=null)c.disconnect(); transfers.release(); }
    }
    protected HttpURLConnection openResultConnection(String validatedUrl) throws IOException {
        return (HttpURLConnection)new URL(validatedUrl).openConnection();
    }

    /** PNG/JPEG only; inspect dimensions before allocating pixels, then fit the existing 2 MB upload limit. */
    public MultipartFile uploadFile(byte[] bytes) {
        if(!decoders.tryAcquire()) throw new BusinessException(ErrorCode.OPERATION_ERROR,"图片处理繁忙，请稍后重试");
        try (ImageInputStream in=ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
            Iterator<ImageReader> readers=ImageIO.getImageReaders(in);
            if(!readers.hasNext()) throw new IOException();
            ImageReader reader=readers.next();
            try {
                reader.setInput(in,true,true);
                String format=reader.getFormatName().toLowerCase(Locale.ROOT);
                int w=reader.getWidth(0), h=reader.getHeight(0);
                if(!Arrays.asList("png","jpeg","jpg").contains(format) || w<=0 || h<=0 || w>4096 || h>4096 || (long)w*h>16_777_216) throw new IOException();
                if(bytes.length<=2*1024*1024) return new ImageFile(bytes,"png".equals(format)?"png":"jpg");
                BufferedImage original=reader.read(0);
                for(int size=2048;size>=512;size/=2) {
                    double ratio=Math.min(1.0,(double)size/Math.max(w,h));
                    BufferedImage image=new BufferedImage(Math.max(1,(int)(w*ratio)),Math.max(1,(int)(h*ratio)),BufferedImage.TYPE_INT_RGB);
                    Graphics2D graphics=image.createGraphics();
                    try { graphics.setColor(Color.WHITE); graphics.fillRect(0,0,image.getWidth(),image.getHeight());
                        graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION,RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                        graphics.drawImage(original,0,0,image.getWidth(),image.getHeight(),null);
                    } finally {graphics.dispose();}
                    ByteArrayOutputStream out=new ByteArrayOutputStream(); ImageIO.write(image,"jpg",out);
                    if(out.size()<=2*1024*1024) return new ImageFile(out.toByteArray(),"jpg");
                }
                throw new IOException();
            } finally { reader.dispose(); }
        } catch(IOException e) { throw new BusinessException(ErrorCode.OPERATION_ERROR,"AI 结果格式或尺寸不受支持"); }
        finally { decoders.release(); }
    }
    private static final class ImageFile implements MultipartFile {
        private final byte[] data; private final String format;
        private ImageFile(byte[] data,String format){this.data=data;this.format=format;}
        public String getName(){return "file";} public String getOriginalFilename(){return "ai-result."+format;}
        public String getContentType(){return "image/"+("jpg".equals(format)?"jpeg":format);}
        public boolean isEmpty(){return data.length==0;} public long getSize(){return data.length;}
        public byte[] getBytes(){return data;} public InputStream getInputStream(){return new ByteArrayInputStream(data);}
        public void transferTo(File file)throws IOException {Files.write(file.toPath(),data);}
    }
}
