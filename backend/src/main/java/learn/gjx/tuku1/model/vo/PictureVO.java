package learn.gjx.tuku1.model.vo;

import cn.hutool.json.JSONUtil;
import learn.gjx.tuku1.model.entity.Picture;
import lombok.Data;
import org.springframework.beans.BeanUtils;

import java.io.Serializable;
import java.util.Date;
import java.util.List;

/**
 * 图片视图（脱敏）
 */
@Data
public class PictureVO implements Serializable {

    /**
     * id
     */
    private Long id;

    /**
     * 主展示图 URL；派生图未生成时为原图
     */
    private String url;

    /**
     * 列表缩略图 URL；为空时客户端回退使用 url
     */
    private String thumbnailUrl;

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

    private String picColor;

    /**
     * 创建用户 id
     */
    private Long userId;

    /** 所属空间；NULL 为公共图库。 */
    private Long spaceId;
    /** Computed per request; never cached as a public user's permission grant. */
    private List<String> permissionList = java.util.Collections.emptyList();

    /**
     * 上传状态：0-草稿；1-已完成
     */
    private Integer uploadStatus;

    /**
     * 创建用户信息
     */
    private UserVO user;

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

    /**
     * VO 转实体对象
     *
     * @param pictureVO 图片 VO
     * @return Picture 实体
     */
    public static Picture voToObj(PictureVO pictureVO) {
        if (pictureVO == null) {
            return null;
        }
        Picture picture = new Picture();
        BeanUtils.copyProperties(pictureVO, picture);
        // tags: List<String> → JSON String
        if (pictureVO.getTags() != null) {
            picture.setTags(JSONUtil.toJsonStr(pictureVO.getTags()));
        }
        return picture;
    }

    /**
     * 实体对象转 VO
     *
     * @param picture 图片实体
     * @return PictureVO
     */
    public static PictureVO objToVo(Picture picture) {
        if (picture == null) {
            return null;
        }
        PictureVO pictureVO = new PictureVO();
        BeanUtils.copyProperties(picture, pictureVO);
        // tags: JSON String → List<String>
        if (picture.getTags() != null) {
            pictureVO.setTags(JSONUtil.toList(picture.getTags(), String.class));
        }
        return pictureVO;
    }

    private static final long serialVersionUID = 1L;
}
