package learn.gjx.tuku1.model.vo;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Data;
@Data @AllArgsConstructor
public class PictureFavoriteStatusVO {
    private long favoriteCount;
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private Boolean favorited;
}
