package learn.gjx.tuku1.model.dto.picture;

import lombok.Data;

@Data
public class SearchPictureByColorRequest {
    private Long spaceId;
    /** Exactly #RRGGBB. This search is independent of the attribute search form. */
    private String picColor;
}
