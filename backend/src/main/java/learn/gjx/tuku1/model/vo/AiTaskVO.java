package learn.gjx.tuku1.model.vo;

import lombok.Data;
import java.util.Date;

@Data
public class AiTaskVO {
    private String id;
    private Long pictureId;
    private String status;
    private String message;
    private String resultUrl;
    private Date createTime;
    private Date expiresAt;
}
