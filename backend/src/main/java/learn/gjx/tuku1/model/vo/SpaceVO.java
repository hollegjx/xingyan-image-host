package learn.gjx.tuku1.model.vo;

import learn.gjx.tuku1.model.entity.Space;
import lombok.Data;
import org.springframework.beans.BeanUtils;
import java.io.Serializable;
import java.util.Date;

@Data
public class SpaceVO implements Serializable {
    private Long id;
    private String spaceName;
    private Integer spaceLevel;
    private Integer spaceType;
    private java.util.List<String> permissionList = java.util.Collections.emptyList();
    private boolean canManageSpace;
    private Long maxSize;
    private Long maxCount;
    private Long totalSize;
    private Long totalCount;
    private Long userId;
    private Date createTime;
    private Date editTime;
    private Date updateTime;
    private static final long serialVersionUID = 1L;

    public static SpaceVO objToVo(Space space) {
        if (space == null) {
            return null;
        }
        SpaceVO vo = new SpaceVO();
        BeanUtils.copyProperties(space, vo);
        return vo;
    }
}
