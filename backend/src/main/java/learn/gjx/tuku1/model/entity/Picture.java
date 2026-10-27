package learn.gjx.tuku1.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.util.Date;
import lombok.Data;

/**
 * 图片
 * @TableName picture
 */
@TableName(value ="picture")
@Data
public class Picture {
    /**
     * id
     */
    @TableId(type = IdType.AUTO)
    private Long id;

    /**
     * 主展示图 URL；派生图未生成时等于 originalUrl
     */
    private String url;

    /**
     * COS 原始文件 URL，用于重新处理和下载
     */
    private String originalUrl;

    /**
     * 列表缩略图 URL；为空时客户端回退使用 url
     */
    private String thumbnailUrl;

    /**
     * 外部图片来源 URL，仅用于追踪来源
     */
    private String sourceUrl;

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
     * 标签（JSON 数组）
     */
    private String tags;

    /**
     * 图片体积
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

    /** Canonical #RRGGBB from upload metadata; null means unavailable. */
    private String picColor;

    /**
     * 创建用户 id
     */
    private Long userId;

    /** 所属空间；NULL 为公共图库，私有写入必须同时维护空间用量。 */
    private Long spaceId;

    /**
     * 上传状态：0-草稿；1-已完成
     */
    private Integer uploadStatus;

    /**
     * 创建时间
     */
    private Date createTime;

    /**
     * 编辑时间
     */
    private Date editTime;

    /**
     * 更新时间
     */
    private Date updateTime;

    /**
     * 是否删除
     */
    private Integer isDelete;

    /**
     * 审核状态：0-待审核；1-通过；2-拒绝
     */
    private Integer reviewStatus;

    /**
     * 审核信息
     */
    private String reviewMessage;

    /**
     * 审核人 ID
     */
    private Long reviewerId;

    /**
     * 审核时间
     */
    private Date reviewTime;
}
