package learn.gjx.tuku1.security;

import cn.hutool.crypto.digest.DigestUtil;
import learn.gjx.tuku1.exception.BusinessException;
import learn.gjx.tuku1.exception.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;
import java.text.Normalizer;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

/** Redis 共享失败窗口；代理来源只采用容器已解析的 remoteAddr。 */
@Component
@Slf4j
public class LoginAttemptLimiter {
    public static final String FAILURE_MESSAGE = "账号或密码错误，请稍后重试";
    private final StringRedisTemplate redis;
    private final int accountLimit;
    private final int ipLimit;
    private final long windowSeconds;
    private static final DefaultRedisScript<Long> CHECK = new DefaultRedisScript<>(
            "local a=tonumber(redis.call('GET',KEYS[1]) or '0'); "
            + "local b=tonumber(redis.call('GET',KEYS[2]) or '0'); "
            + "if a>=tonumber(ARGV[1]) or b>=tonumber(ARGV[2]) then return 0 end; return 1", Long.class);
    private static final DefaultRedisScript<Long> FAIL = new DefaultRedisScript<>(
            "for _,k in ipairs(KEYS) do local n=redis.call('INCR',k); "
            + "if n==1 then redis.call('EXPIRE',k,ARGV[1]) end end; return 1", Long.class);

    public LoginAttemptLimiter(StringRedisTemplate redis,
            @Value("${security.login.account-max-failures:5}") int accountLimit,
            @Value("${security.login.ip-max-failures:20}") int ipLimit,
            @Value("${security.login.window-seconds:900}") long windowSeconds) {
        if (accountLimit < 1 || ipLimit < 1 || windowSeconds < 1)
            throw new IllegalArgumentException("登录限制配置必须为正数");
        this.redis = redis; this.accountLimit = accountLimit;
        this.ipLimit = ipLimit; this.windowSeconds = windowSeconds;
    }
    public void check(String account, String ip) {
        try {
            if (!Long.valueOf(1).equals(redis.execute(CHECK, keys(account, ip),
                    String.valueOf(accountLimit), String.valueOf(ipLimit)))) throw rejected();
        } catch (BusinessException e) { throw e; }
        catch (RuntimeException e) { log.warn("登录计数服务不可用"); throw rejected(); }
    }
    public void failed(String account, String ip) {
        try {
            if (!Long.valueOf(1).equals(redis.execute(FAIL, keys(account, ip), String.valueOf(windowSeconds))))
                throw rejected();
        } catch (BusinessException e) { throw e; }
        catch (RuntimeException e) { log.warn("登录失败计数无法写入"); throw rejected(); }
    }
    public void succeeded(String account, String ip) {
        try { redis.delete(keys(account, ip)); }
        catch (RuntimeException e) { log.warn("登录计数无法清除"); throw rejected(); }
    }
    private List<String> keys(String account, String ip) {
        String normalized = Normalizer.normalize(account == null ? "" : account, Normalizer.Form.NFKD)
                .replaceAll("\\p{M}", "").trim().toLowerCase(Locale.ROOT);
        return Arrays.asList("security:login:account:" + DigestUtil.sha256Hex(normalized),
                "security:login:ip:" + DigestUtil.sha256Hex(ip == null ? "unknown" : ip));
    }
    public static BusinessException rejected() {
        log.warn("登录凭据被拒绝");
        return new BusinessException(ErrorCode.NO_AUTH_ERROR, FAILURE_MESSAGE);
    }
}
