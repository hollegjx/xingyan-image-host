package learn.gjx.tuku1.model.dto.picture;
import lombok.Data;
import java.util.List;
@Data
public class PictureFavoriteStatusRequest {
    private List<Long> pictureIds;
}
