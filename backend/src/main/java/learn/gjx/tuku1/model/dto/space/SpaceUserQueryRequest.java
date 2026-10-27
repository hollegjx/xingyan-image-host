package learn.gjx.tuku1.model.dto.space;
import lombok.Data;
@Data
public class SpaceUserQueryRequest {
    private Long spaceId;
    private Long userId;
    private Integer current = 1;
    private Integer pageSize = 20;
}
