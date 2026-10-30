package learn.gjx.tuku1.model.dto.picture;
import lombok.Data;
@Data
public class PictureFavoriteRequest {
    private Long pictureId;
    private Boolean favorite;
}
