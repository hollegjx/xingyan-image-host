package learn.gjx.tuku1.model.dto.space;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class TeamInviteCreateRequest {
    private Long spaceId;
    private String spaceRole;
    private Integer validDays;
    private Integer maxUses;
}
