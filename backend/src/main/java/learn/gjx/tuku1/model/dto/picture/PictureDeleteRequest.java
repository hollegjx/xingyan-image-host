package learn.gjx.tuku1.model.dto.picture;

import lombok.Data;

import java.io.Serializable;

/**
 * 图片删除请求
 */
@Data
public class PictureDeleteRequest implements Serializable {

    /**
     * 图片 ID
     */
    private Long id;

    private static final long serialVersionUID = 1L;
}
