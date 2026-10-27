package learn.gjx.tuku1.model.dto.picture;

import lombok.Data;
import java.util.List;

@Data
public class PictureEditByBatchRequest {
    private Long spaceId;
    /** Ordered selection; numbering follows this order, not database row order. Maximum 100. */
    private List<Long> pictureIdList;
    /** null = unchanged; empty string = clear. */
    private String category;
    /** null = unchanged; [] = clear; otherwise replace the complete tag list. */
    private List<String> tags;
    /** null = unchanged. Only the literal {序号} placeholder is supported. */
    private String nameRule;
}
