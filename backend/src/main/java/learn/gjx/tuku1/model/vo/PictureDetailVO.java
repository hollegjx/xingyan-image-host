package learn.gjx.tuku1.model.vo;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Getter;
import lombok.Setter;
/** 个性化收藏状态仅用于详情，不进入公共列表缓存的 PictureVO。 */
@Getter @Setter
public class PictureDetailVO extends PictureVO {
    private long favoriteCount;
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private Boolean favorited;
}
