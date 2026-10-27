package learn.gjx.tuku1.model.dto.picture;

import lombok.Data;
import java.io.Serializable;

@Data
public class SearchPictureByPictureRequest implements Serializable {
    private static final long serialVersionUID = 1L;
    private Long pictureId;
    /** Explicit consent for this request to send the public image URL to Baidu. */
    private Boolean externalSearchConsent;
}
