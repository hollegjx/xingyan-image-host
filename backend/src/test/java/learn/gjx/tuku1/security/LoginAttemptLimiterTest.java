package learn.gjx.tuku1.security;

import learn.gjx.tuku1.exception.BusinessException;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.*;

@EnabledIfEnvironmentVariable(named = "REDIS_SERVER_BIN", matches = ".+")
class LoginAttemptLimiterTest {
    static Process process;
    static LettuceConnectionFactory factory;
    static StringRedisTemplate redis;
    LoginAttemptLimiter limiter;
    @BeforeAll static void start() throws Exception {
        int port; try (ServerSocket socket = new ServerSocket(0)) { port = socket.getLocalPort(); }
        Path directory = Files.createTempDirectory("user-security-redis-");
        process = new ProcessBuilder(System.getenv("REDIS_SERVER_BIN"), "--bind", "127.0.0.1", "--port", String.valueOf(port),
                "--dir", directory.toString(), "--save", "", "--appendonly", "no")
                .redirectErrorStream(true).redirectOutput(directory.resolve("redis.log").toFile()).start();
        await().atMost(Duration.ofSeconds(10)).until(() -> {
            try (Socket socket = new Socket("127.0.0.1", port)) { return true; } catch (Exception e) { return false; }
        });
        factory = new LettuceConnectionFactory("127.0.0.1", port); factory.afterPropertiesSet();
        redis = new StringRedisTemplate(factory);
    }
    @AfterAll static void stop() throws Exception {
        if (factory != null) factory.destroy();
        if (process != null) { process.destroy(); if (!process.waitFor(5, TimeUnit.SECONDS)) process.destroyForcibly(); }
    }
    @BeforeEach void reset() {
        try (org.springframework.data.redis.connection.RedisConnection c = factory.getConnection()) { c.flushDb(); }
        limiter = new LoginAttemptLimiter(redis, 2, 3, 2);
    }
    @Test void accountLimitAppliesAcrossDifferentIpsAndAccountCase() {
        limiter.failed("FixtureAccount", "ip1"); limiter.failed("fixtureaccount", "ip2");
        assertThrows(BusinessException.class, () -> limiter.check("FIXTUREACCOUNT", "ip3"));
        assertDoesNotThrow(() -> limiter.check("other", "ip3"));
    }
    @Test void ipLimitAppliesAcrossAccounts() {
        for (int i = 0; i < 3; i++) limiter.failed("fixture" + i, "ip1");
        assertThrows(BusinessException.class, () -> limiter.check("other", "ip1"));
        assertDoesNotThrow(() -> limiter.check("other", "ip2"));
    }
    @Test void fixedWindowExpiresAndBlockedChecksDoNotExtendIt() {
        limiter.failed("fixture", "ip1"); limiter.failed("fixture", "ip1");
        assertThrows(BusinessException.class, () -> limiter.check("fixture", "ip1"));
        await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> assertDoesNotThrow(() -> limiter.check("fixture", "ip1")));
    }
    @Test void successfulLoginClearsBothCounters() {
        limiter.failed("fixture", "ip1"); limiter.succeeded("fixture", "ip1");
        limiter.failed("fixture", "ip1"); assertDoesNotThrow(() -> limiter.check("fixture", "ip1"));
    }
    @Test void keysDoNotContainRawAccountOrIp() {
        limiter.failed("fixture-account", "fixture-ip");
        for (String key : redis.keys("security:login:*")) {
            assertFalse(key.contains("fixture-account")); assertFalse(key.contains("fixture-ip"));
        }
    }
    @Test void redisOutageFailsClosedWithSameMessage() {
        StringRedisTemplate failing = org.mockito.Mockito.mock(StringRedisTemplate.class);
        org.mockito.Mockito.when(failing.execute(org.mockito.ArgumentMatchers.any(org.springframework.data.redis.core.script.RedisScript.class),
                org.mockito.ArgumentMatchers.anyList(), org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()))
                .thenThrow(new IllegalStateException());
        LoginAttemptLimiter down = new LoginAttemptLimiter(failing, 2, 3, 2);
        assertEquals(LoginAttemptLimiter.FAILURE_MESSAGE,
                assertThrows(BusinessException.class, () -> down.check("fixture", "ip1")).getMessage());
    }
}
