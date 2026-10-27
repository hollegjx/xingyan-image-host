package learn.gjx.tuku1.model.dto.picture;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 图片处理请求
 */
@Data
public class PictureProcessRequest implements Serializable {

    /**
     * 需要处理的图片ID
     */
    private Long pictureId;

    /**
     * 处理规则，最多5条
     */
    private List<PictureProcessRule> rules;

    /**
     * 输出目录，可选
     */
    private String outputDir;

    private static final long serialVersionUID = 1L;
}

