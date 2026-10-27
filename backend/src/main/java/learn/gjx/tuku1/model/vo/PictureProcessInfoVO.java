package learn.gjx.tuku1.model.vo;

import com.qcloud.cos.model.ciModel.persistence.CIObject;
import com.qcloud.cos.model.ciModel.persistence.ImageInfo;
import com.qcloud.cos.model.ciModel.persistence.OriginalInfo;
import lombok.Data;

import java.io.Serializable;

/**
 * 图片处理后的元信息
 */
@Data
public class PictureProcessInfoVO implements Serializable {

    private String key;
    private String url;
    private String format;
    private Integer width;
    private Integer height;
    private Long size;
    private Integer frameCount;

    public static PictureProcessInfoVO fromOriginal(OriginalInfo originalInfo) {
        if (originalInfo == null) {
            return null;
        }
        PictureProcessInfoVO infoVO = new PictureProcessInfoVO();
        infoVO.setKey(originalInfo.getKey());
        infoVO.setUrl(originalInfo.getLocation());
        ImageInfo imageInfo = originalInfo.getImageInfo();
        if (imageInfo != null) {
            infoVO.setFormat(imageInfo.getFormat());
            infoVO.setWidth(imageInfo.getWidth());
            infoVO.setHeight(imageInfo.getHeight());
            infoVO.setFrameCount(imageInfo.getFrameCount());
        }
        return infoVO;
    }

    public static PictureProcessInfoVO fromProcessObject(CIObject ciObject) {
        if (ciObject == null) {
            return null;
        }
        PictureProcessInfoVO infoVO = new PictureProcessInfoVO();
        infoVO.setKey(ciObject.getKey());
        infoVO.setUrl(ciObject.getLocation());
        infoVO.setFormat(ciObject.getFormat());
        infoVO.setWidth(ciObject.getWidth());
        infoVO.setHeight(ciObject.getHeight());
        if (ciObject.getSize() != null) {
            infoVO.setSize(ciObject.getSize().longValue());
        }
        infoVO.setFrameCount(ciObject.getFrameCount());
        return infoVO;
    }

    private static final long serialVersionUID = 1L;
}
