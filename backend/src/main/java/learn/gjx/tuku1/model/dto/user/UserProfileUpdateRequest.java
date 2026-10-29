package learn.gjx.tuku1.model.dto.user;

import lombok.Data;

/** 本人资料白名单，不接收用户 ID、账号或权限字段。 */
@Data
public class UserProfileUpdateRequest {
    private String userName;
    private String userProfile;
}
