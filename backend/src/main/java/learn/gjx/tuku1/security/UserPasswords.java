package learn.gjx.tuku1.security;

import cn.hutool.crypto.digest.BCrypt;
import cn.hutool.crypto.digest.DigestUtil;
import learn.gjx.tuku1.exception.BusinessException;
import learn.gjx.tuku1.exception.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import java.nio.charset.StandardCharsets;

/** 新密码规则与旧凭据的渐进迁移。 */
@Slf4j
public final class UserPasswords {
    private static final String PREHASH_PREFIX = "{bcrypt-sha256}";
    private UserPasswords() { }

    public static void validateNew(String password) {
        if (password == null || password.trim().isEmpty() || password.length() < 6
                || password.getBytes(StandardCharsets.UTF_8).length > 72) {
            log.warn("新密码不符合长度规则");
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "密码至少6位，UTF-8编码不超过72字节");
        }
    }
    public static String hash(String password) {
        validateNew(password);
        return BCrypt.hashpw(password, BCrypt.gensalt(12));
    }
    public static boolean isLegacy(String hash) {
        return hash != null && hash.matches("(?i)[0-9a-f]{32}");
    }
    public static String hashLegacy(String password) {
        // 老系统没有长度上限，预哈希避免 BCrypt 截断超过72字节的旧密码。
        if (password.getBytes(StandardCharsets.UTF_8).length > 72)
            return PREHASH_PREFIX + BCrypt.hashpw(DigestUtil.sha256Hex(password), BCrypt.gensalt(12));
        return hash(password);
    }
    public static boolean matches(String password, String hash) {
        if (password == null || hash == null) return false;
        // NOTICE: 全部旧 MD5 凭据迁移完成后删除此兼容分支和旧盐。
        if (isLegacy(hash)) return DigestUtil.md5Hex("tuku1_salt_2025" + password).equalsIgnoreCase(hash);
        try {
            if (hash.startsWith(PREHASH_PREFIX))
                return BCrypt.checkpw(DigestUtil.sha256Hex(password), hash.substring(PREHASH_PREFIX.length()));
            if (password.getBytes(StandardCharsets.UTF_8).length > 72) return false;
            return BCrypt.checkpw(password, hash);
        } catch (IllegalArgumentException e) {
            return false;
        }
    }
}
