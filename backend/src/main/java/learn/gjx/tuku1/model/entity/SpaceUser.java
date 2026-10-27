package learn.gjx.tuku1.model.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;
import java.io.Serializable;
import java.util.Date;

/** Membership is physically removed, allowing later rejoining under the unique (space,user) key. */
@Data
@TableName("space_user")
public class SpaceUser implements Serializable {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private Long spaceId;
    private Long userId;
    private String spaceRole;
    private Date createTime;
    private Date updateTime;
    private static final long serialVersionUID = 1L;
}
