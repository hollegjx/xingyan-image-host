package learn.gjx.tuku1.model.dto.picture;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 图片上传请求
 */
@Data
public class PictureUploadRequest implements Serializable {

    /**
     * 图片 ID（用于更新，新增时为空）
     */
    private Long id;

    /** 新上传的目标空间；替换省略时沿用原归属，不允许跨空间移动。 */
    private Long spaceId;

    /** 仅协同编辑保存使用，由当前 WebSocket 连接持有；不替代 Session / 空间权限。 */
    @lombok.ToString.Exclude
    private String collaborationToken;
    private Long collaborationRevision;

    /**
     * 图片名称
     */
    private String name;

    /**
     * 简介
     */
    private String introduction;

    /**
     * 分类
     */
    private String category;

    /**
     * 标签列表
     */
    private List<String> tags;

    private static final long serialVersionUID = 1L;
}
