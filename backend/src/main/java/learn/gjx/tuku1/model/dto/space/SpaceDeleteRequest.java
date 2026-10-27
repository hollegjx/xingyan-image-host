package learn.gjx.tuku1.model.dto.space;

import lombok.Data;
import java.io.Serializable;

@Data
public class SpaceDeleteRequest implements Serializable {
    private Long id;
    private static final long serialVersionUID = 1L;
}
