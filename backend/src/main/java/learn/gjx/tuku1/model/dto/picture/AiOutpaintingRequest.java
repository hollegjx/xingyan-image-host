package learn.gjx.tuku1.model.dto.picture;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

@Data
public class AiOutpaintingRequest {
    private Long pictureId;
    /** Client UUID reused on network retry; not an identity or authorization token. */
    private String requestId;
    private Boolean externalProcessingConsent;
    @JsonProperty("xScale") private Double xScale;
    @JsonProperty("yScale") private Double yScale;
}
