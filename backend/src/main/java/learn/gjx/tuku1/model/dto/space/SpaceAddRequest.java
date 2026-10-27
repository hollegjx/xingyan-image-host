package learn.gjx.tuku1.model.dto.space;

import lombok.Data;

import java.io.Serializable;

/** 所有者、额度和已用量只能由服务端赋值。 */
@Data
public class SpaceAddRequest implements Serializable {
    private String spaceName;
    private Integer spaceLevel;
    /** 0=private (default), 1=team. Cannot be changed after creation. */
    private Integer spaceType;
    private static final long serialVersionUID = 1L;
}
