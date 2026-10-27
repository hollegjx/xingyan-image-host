package learn.gjx.tuku1.model.dto.picture;

import lombok.Data;

import java.io.Serializable;

/**
 * 图片审核请求
 */
@Data
public class PictureReviewRequest implements Serializable {

    /**
     * 图片 ID
     */
    private Long id;

    /**
     * 审核状态：1-通过；2-拒绝
     */
    private Integer reviewStatus;

    /**
     * 审核信息
     */
    private String reviewMessage;

    private static final long serialVersionUID = 1L;
}
