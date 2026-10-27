package learn.gjx.tuku1.model.dto.file;

import lombok.Data;

import java.io.Serializable;

/**
 * 图片上传结果
 */
@Data
public class PictureUploadResult implements Serializable {

    /**
     * 主展示图 URL；派生图不可用时回退为原图
     */
    private String url;

    /**
     * COS 原始文件 URL
     */
    private String originalUrl;

    /**
     * 列表缩略图 URL；未生成或收益不足时为空
     */
    private String thumbnailUrl;

    /**
     * 图片名称
     */
    private String picName;

    /**
     * 图片体积（字节）
     */
    private Long picSize;

    /**
     * 图片宽度
     */
    private Integer picWidth;

    /**
     * 图片高度
     */
    private Integer picHeight;

    /**
     * 图片宽高比例
     */
    private Double picScale;

    /**
     * 图片格式
     */
    private String picFormat;

    private String picColor;

    private static final long serialVersionUID = 1L;
}
