package learn.gjx.tuku1.model.entity;

import lombok.Data;
import java.util.Date;

/** Internal row. Never serialize provider IDs, signed URLs or source references to the client. */
@Data
public class AiPictureTask {
    private String id;
    private Long userId;
    private Long pictureId;
    private String requestId;
    private Double xScale;
    private Double yScale;
    private String sourceUrl;
    private String providerTaskId;
    private String providerModel;
    private String providerRequestId;
    private Integer outputWidth;
    private Integer outputHeight;
    private String status;
    private String applyToken;
    private String resultUrl;
    private String message;
    private Date createTime;
    private Date expiresAt;
}
