package learn.gjx.tuku1.model.vo;
import lombok.Data;
import java.util.Date;
@Data
public class SpaceUserVO {
    private Long id;
    private Long spaceId;
    private Long userId;
    private String spaceRole;
    private String userName;
    private String userAvatar;
    private Date createTime;
    private boolean owner;
    private SpaceVO space;
}
