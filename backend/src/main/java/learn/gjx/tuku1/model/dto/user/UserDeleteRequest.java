package learn.gjx.tuku1.model.dto.user;

import lombok.Data;

import java.io.Serializable;

/**
 * 管理员删除用户请求
 */
@Data
public class UserDeleteRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 用户id
     */
    private Long id;
}
