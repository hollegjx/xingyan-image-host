package learn.gjx.tuku1.model.dto.user;

import lombok.Getter;
import lombok.Setter;

/** 避免生成会把密码写入日志的 toString。 */
@Getter
@Setter
public class UserPasswordUpdateRequest {
    private String oldPassword;
    private String newPassword;
    private String checkPassword;
}
