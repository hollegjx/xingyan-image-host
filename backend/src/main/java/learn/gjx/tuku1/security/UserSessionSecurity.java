package learn.gjx.tuku1.security;

import cn.hutool.crypto.digest.DigestUtil;
import learn.gjx.tuku1.model.entity.User;
import learn.gjx.tuku1.exception.BusinessException;
import learn.gjx.tuku1.exception.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/** 会话只存不可逆版本摘要；每次授权与数据库的最新密码版本比较。 */
@Slf4j
public final class UserSessionSecurity {
    public static final String VERSION = "user_password_version";
    private UserSessionSecurity() { }
    public static String version(User user) {
        if (user == null || user.getUserPassword() == null) {
            log.warn("用户凭据版本缺失");
            throw new BusinessException(ErrorCode.NOT_LOGIN_ERROR, "登录已失效，请重新登录");
        }
        return DigestUtil.sha256Hex(user.getUserPassword());
    }
    public static void requireCurrent(Object storedVersion, User user) {
        if (!(storedVersion instanceof String) || !MessageDigest.isEqual(
                ((String) storedVersion).getBytes(StandardCharsets.UTF_8), version(user).getBytes(StandardCharsets.UTF_8))) {
            log.warn("会话凭据版本已失效");
            throw new BusinessException(ErrorCode.NOT_LOGIN_ERROR, "登录已失效，请重新登录");
        }
    }
}
