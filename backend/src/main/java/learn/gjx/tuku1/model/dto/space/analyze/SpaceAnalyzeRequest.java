package learn.gjx.tuku1.model.dto.space.analyze;

import lombok.Data;
import java.io.Serializable;

/** 统计范围是互斥值；不能通过同时设置多个布尔开关绕过空间权限。 */
@Data
public class SpaceAnalyzeRequest implements Serializable {
    /** SPACE（本人空间）、PUBLIC（管理员公共图库）、ALL（管理员全局数值聚合）。 */
    private String scope = "SPACE";
    private Long spaceId;
    /** day / week / month；周一是每周第一天。 */
    private String timeDimension = "day";
    /** yyyy-MM-dd，包含首尾两天，最多 366 天；不传时按维度选择默认窗口。 */
    private String startDate;
    private String endDate;
    /** 仅用于上传趋势，不影响其他统计；全局范围不允许按用户追踪。 */
    private Long userId;
    /** 空间排行数量，1～20，默认 10。 */
    private Integer topN = 10;
    private static final long serialVersionUID = 1L;
}
