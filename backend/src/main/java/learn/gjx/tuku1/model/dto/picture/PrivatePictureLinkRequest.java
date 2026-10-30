package learn.gjx.tuku1.model.dto.picture;

import lombok.Data;

@Data
public class PrivatePictureLinkRequest {
    private Long pictureId;
    private Integer validHours;
}
