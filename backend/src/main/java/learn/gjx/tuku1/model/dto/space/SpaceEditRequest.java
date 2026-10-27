package learn.gjx.tuku1.model.dto.space;

import lombok.Data;
import java.io.Serializable;

/** 普通用户仅可修改名称；不接受归属、级别、用量或额度字段。 */
@Data
public class SpaceEditRequest implements Serializable {
    private Long id;
    private String spaceName;
    private static final long serialVersionUID = 1L;
}
