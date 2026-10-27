package learn.gjx.tuku1.model.dto.space;

import lombok.Data;
import java.io.Serializable;

/** 管理员可调整名称、级别和额度；不可借此篡改归属及已用量。 */
@Data
public class SpaceUpdateRequest implements Serializable {
    private Long id;
    private String spaceName;
    private Integer spaceLevel;
    private Long maxSize;
    private Long maxCount;
    private static final long serialVersionUID = 1L;
}
