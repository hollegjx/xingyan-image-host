package learn.gjx.tuku1.manager;

import com.qcloud.cos.COSClient;
import learn.gjx.tuku1.config.*;
import learn.gjx.tuku1.exception.BusinessException;
import org.junit.jupiter.api.*;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AiEditingFilesTest {
    AiEditingFiles files=new AiEditingFiles(mock(COSClient.class),new CosClientConfig(),mock(PrivatePictureFileManager.class),new AiEditingProperties());
    @Test void rejectArbitraryResultDestinations(){
        for(String url:new String[]{"http://dashscope-result-bj.oss-cn-beijing.aliyuncs.com/x","https://127.0.0.1/x","https://dashscope-result-bj.oss-cn-beijing.aliyuncs.com.evil/x","https://user@dashscope-result-bj.oss-cn-beijing.aliyuncs.com/x","file:///etc/passwd"})
            assertThrows(BusinessException.class,()->files.validateResult(url));
    }
    @Test void preserveSignedQuery(){String url="https://dashscope-result-bj.oss-cn-beijing.aliyuncs.com/x.png?Expires=123&Signature=test";assertEquals(url,files.validateResult(url));}
    @Test void acceptCurrentDocumentedOutpaintingHost(){String url="https://vigen-invi.oss-cn-shanghai.aliyuncs.com/test/output.png?Signature=test";assertEquals(url,files.validateResult(url));}
    @Test void acceptDocumentedQwenAccelerationHosts(){
        for(String bucket:new String[]{"a717","66f3","7c2c","2522","c72b","0484","7e0f","5859","5496","35f9","31d9","7f1f","cc75","64e9"}) {
            String url="https://dashscope-"+bucket+".oss-accelerate.aliyuncs.com/result.png?Signature=test";
            assertEquals(url,files.validateResult(url));
        }
    }
    @Test void accelerationHostDoesNotAllowArbitraryOssBucketsOrSuffixes(){
        for(String url:new String[]{"https://unrelated.oss-accelerate.aliyuncs.com/x", "https://dashscope-abcd.oss-accelerate.aliyuncs.com/x", "https://dashscope-a717.oss-accelerate.aliyuncs.com.evil/x", "https://dashscope-a717.oss-accelerate.aliyuncs.com:8443/x", "https://dashscope-a717.oss-accelerate.aliyuncs.com@evil/x"})
            assertThrows(BusinessException.class,()->files.validateResult(url));
    }
    @Test void rejectsNonImages(){assertThrows(BusinessException.class,()->files.uploadFile("<html>not image</html>".getBytes()));}
    @Test void pngStaysPng()throws Exception {ByteArrayOutputStream out=new ByteArrayOutputStream();ImageIO.write(new BufferedImage(512,512,BufferedImage.TYPE_INT_RGB),"png",out);assertEquals("image/png",files.uploadFile(out.toByteArray()).getContentType());}
    @Test void oversizedDimensionsRejectedBeforeDecode()throws Exception {ByteArrayOutputStream out=new ByteArrayOutputStream();ImageIO.write(new BufferedImage(4097,1,BufferedImage.TYPE_INT_RGB),"png",out);assertThrows(BusinessException.class,()->files.uploadFile(out.toByteArray()));}
}
