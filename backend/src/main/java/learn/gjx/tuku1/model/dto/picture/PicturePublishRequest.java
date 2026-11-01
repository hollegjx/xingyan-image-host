package learn.gjx.tuku1.model.dto.picture;

import lombok.Data;

@Data
public class PicturePublishRequest {
    private Long pictureId;
    /** 未传时沿用原图分类；空字符串表示未分类。 */
    private String category;
}
