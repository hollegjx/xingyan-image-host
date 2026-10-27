package learn.gjx.tuku1.model.dto.space;

import lombok.Data;
import java.io.Serializable;

@Data
public class SpaceQueryRequest implements Serializable {
    private Long id;
    private String spaceName;
    private Integer spaceLevel;
    private Integer spaceType;
    private Long userId;
    private Integer current = 1;
    private Integer pageSize = 10;
    private String sortField;
    private String sortOrder;
    private static final long serialVersionUID = 1L;
}
