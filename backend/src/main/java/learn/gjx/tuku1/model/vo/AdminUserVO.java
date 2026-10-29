package learn.gjx.tuku1.model.vo;

import lombok.Data;
import lombok.EqualsAndHashCode;

/** 仅管理员接口返回的用户信息，不包含密码哈希。 */
@Data
@EqualsAndHashCode(callSuper = true)
public class AdminUserVO extends UserVO {
    private String userAccount;
    private Integer userRole;
}
