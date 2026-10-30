package learn.gjx.tuku1.model.dto.picture;
import lombok.Data;
@Data
public class FavoritePageRequest {
    private Integer current = 1;
    private Integer pageSize = 12;
}
