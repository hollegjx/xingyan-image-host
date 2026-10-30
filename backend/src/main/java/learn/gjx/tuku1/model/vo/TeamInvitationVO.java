package learn.gjx.tuku1.model.vo;

import lombok.Getter;
import lombok.Setter;
import java.util.Date;

/** 列表与确认信息不含凭据及哈希。 */
@Getter
@Setter
public class TeamInvitationVO {
    private Long id;
    private Long spaceId;
    private String spaceName;
    private String spaceRole;
    private Date expiresAt;
    private Integer maxUses;
    private Integer usedCount;
}
