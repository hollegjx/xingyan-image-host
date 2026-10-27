package learn.gjx.tuku1.model.dto.space;
import lombok.Data;
@Data
public class SpaceUserAddRequest {
    private Long spaceId;
    private Long userId;
    private String spaceRole = "viewer";
}
