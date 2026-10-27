package learn.gjx.tuku1.model.dto.space;
import lombok.Data;
@Data
public class SpaceUserEditRequest {
    private Long id;
    private Long spaceId;
    private String spaceRole;
}
