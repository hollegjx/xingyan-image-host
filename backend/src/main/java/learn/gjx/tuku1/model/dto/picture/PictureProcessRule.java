package learn.gjx.tuku1.model.dto.picture;

import lombok.Data;

import java.io.Serializable;

/**
 * 图片处理规则
 */
@Data
public class PictureProcessRule implements Serializable {

    /**
     * 处理规则，如 rotate/90、thumbnail/!50px等
     */
    private String rule;

    /**
     * 指定保存的文件名，可为空
     */
    private String outputFileName;

    private static final long serialVersionUID = 1L;
}

