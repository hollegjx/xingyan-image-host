package learn.gjx.tuku1.session;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import learn.gjx.tuku1.constant.UserConstant;
import learn.gjx.tuku1.controller.UserController;
import learn.gjx.tuku1.exception.GlobalExceptionHandler;
import learn.gjx.tuku1.mapper.UserMapper;
import learn.gjx.tuku1.model.entity.User;
import learn.gjx.tuku1.model.vo.LoginUserVO;
import learn.gjx.tuku1.application.collaboration.CollaborationService;
import learn.gjx.tuku1.infrastructure.collaboration.*;
import learn.gjx.tuku1.manager.space.PictureAccessManager;
import learn.gjx.tuku1.mapper.PictureMapper;
import learn.gjx.tuku1.mapper.SpaceMapper;
import learn.gjx.tuku1.mapper.SpaceUserMapper;
import learn.gjx.tuku1.model.entity.Picture;
import learn.gjx.tuku1.model.entity.Space;
import learn.gjx.tuku1.model.entity.SpaceUser;
import org.springframework.web.socket.*;
import org.springframework.web.socket.handler.TextWebSocketHandler;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import java.util.concurrent.*;
import learn.gjx.tuku1.service.impl.UserServiceImpl;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.web.servlet.context.ServletWebServerApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.*;
import org.springframework.session.Session;
import org.springframework.session.SessionRepository;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Base64;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Runs the real HTTP/session stack against an isolated Redis process.
 * Only database user queries are stubbed; no production users or Redis keys are touched.
 */
@EnabledIfEnvironmentVariable(named = "REDIS_SERVER_BIN", matches = ".+")
class RedisSessionIntegrationTest {

    private static Process redis;
    private static int redisPort;
    private static final String TEST_ORIGIN = "https://app.example.com";
    private static final ObjectMapper JSON = new ObjectMapper();

    @BeforeAll
    static void startRedis() throws Exception {
        try (ServerSocket socket = new ServerSocket(0)) {
            redisPort = socket.getLocalPort();
        }
        Path directory = Files.createTempDirectory("tuku-session-test-");
        redis = new ProcessBuilder(System.getenv("REDIS_SERVER_BIN"),
                "--bind", "127.0.0.1", "--port", Integer.toString(redisPort),
                "--dir", directory.toString(), "--save", "", "--appendonly", "no")
                .redirectErrorStream(true)
                .redirectOutput(directory.resolve("redis.log").toFile())
                .start();
        try {
            await().atMost(Duration.ofSeconds(10)).until(() -> {
                if (!redis.isAlive()) {
                    throw new IllegalStateException("Redis exited; see " + directory.resolve("redis.log"));
                }
                try (Socket ignored = new Socket("127.0.0.1", redisPort)) {
                    return true;
                } catch (IOException e) {
                    return false;
                }
            });
        } catch (RuntimeException e) {
            redis.destroy();
            throw e;
        }
    }

    @AfterAll
    static void stopRedis() throws Exception {
        if (redis != null) {
            redis.destroy();
            if (!redis.waitFor(5, TimeUnit.SECONDS)) {
                redis.destroyForcibly();
                redis.waitFor(5, TimeUnit.SECONDS);
            }
        }
    }

    @Test
    void passwordChangeRevokesOtherRedisAndWebSocketSessions() throws Exception {
        try (ServletWebServerApplicationContext app = startApplication(namespace())) {
            String currentCookie = login(app, null).getHeaders().getFirst(HttpHeaders.SET_COOKIE).split(";", 2)[0];
            String otherCookie = login(app, null).getHeaders().getFirst(HttpHeaders.SET_COOKIE).split(";", 2)[0];
            UserServiceImpl service = app.getBean(UserServiceImpl.class);
            User currentUser = service.getById(42L);
            when(app.getBean(UserMapper.class).selectById(42L)).thenReturn(currentUser);
            doReturn(true).when(service).update(any(User.class), any());
            SessionCollaborationAccess access = app.getBean(SessionCollaborationAccess.class);
            assertNotNull(access.authorize(decodeId(otherCookie), 42L, 55L));
            java.util.Map<String, String> change = new java.util.HashMap<>();
            change.put("oldPassword", "password123");
            change.put("newPassword", "replacement-fixture"); change.put("checkPassword", "replacement-fixture");
            ResponseEntity<String> response = call(app, HttpMethod.POST, "/password/update", currentCookie, JSON.writeValueAsString(change));
            assertEquals(0, body(response).path("code").asInt());
            String refreshed = response.getHeaders().getFirst(HttpHeaders.SET_COOKIE).split(";", 2)[0];
            assertEquals(0, body(call(app, HttpMethod.GET, "/get/login", refreshed, null)).path("code").asInt());
            assertEquals(40100, body(call(app, HttpMethod.GET, "/get/login", otherCookie, null)).path("code").asInt());
            assertThrows(learn.gjx.tuku1.exception.BusinessException.class,
                    () -> access.authorize(decodeId(otherCookie), 42L, 55L));
            assertNotNull(access.authorize(decodeId(refreshed), 42L, 55L));
            assertTrue(learn.gjx.tuku1.security.UserPasswords.matches("replacement-fixture", currentUser.getUserPassword()));
        }
    }

    @Test
    void loginSurvivesBackendRestartAndLogoutRevokesIt() throws Exception {
        String namespace = namespace();
        String cookie;
        String id;
        try (ServletWebServerApplicationContext first = startApplication(namespace)) {
            ResponseEntity<String> response = login(first, null);
            assertEquals(0, body(response).path("code").asInt());
            String setCookie = response.getHeaders().getFirst(HttpHeaders.SET_COOKIE);
            assertNotNull(setCookie);
            assertTrue(setCookie.startsWith("JSESSIONID="));
            assertTrue(setCookie.contains("Path=/api"));
            assertTrue(setCookie.contains("HttpOnly"));
            assertTrue(setCookie.contains("SameSite=Lax"));
            cookie = setCookie.split(";", 2)[0];
            id = decodeId(cookie);
            Session session = repository(first).findById(id);
            assertNotNull(session);
            assertEquals(Duration.ofMinutes(30), session.getMaxInactiveInterval());
            assertInstanceOf(LoginUserVO.class, session.getAttribute(UserConstant.USER_LOGIN_STATE));
        }

        // A new application context and HTTP server must load the session from Redis.
        try (ServletWebServerApplicationContext restarted = startApplication(namespace)) {
            ResponseEntity<String> response = call(restarted, HttpMethod.GET, "/get/login", cookie, null);
            assertEquals(0, body(response).path("code").asInt());
            assertEquals(42L, body(response).path("data").path("id").asLong());
            response = call(restarted, HttpMethod.POST, "/logout", cookie, null);
            assertEquals(0, body(response).path("code").asInt());
            assertTrue(body(response).path("data").asBoolean());
            assertNull(repository(restarted).findById(id));
            assertTrue(response.getHeaders().getFirst(HttpHeaders.SET_COOKIE).contains("Max-Age=0"));
            assertEquals(40100, body(call(restarted, HttpMethod.GET, "/get/login", cookie, null))
                    .path("code").asInt());
        }
    }

    @Test
    void expiredAndAnonymousRequestsDoNotCreateNewSessions() throws Exception {
        try (ServletWebServerApplicationContext app = startApplication(namespace())) {
            ResponseEntity<String> anonymous = call(app, HttpMethod.GET, "/get/login", null, null);
            assertEquals(40100, body(anonymous).path("code").asInt());
            assertNull(anonymous.getHeaders().getFirst(HttpHeaders.SET_COOKIE));

            String cookie = login(app, null).getHeaders().getFirst(HttpHeaders.SET_COOKIE).split(";", 2)[0];
            String id = decodeId(cookie);
            SessionRepository<Session> repository = repository(app);
            Session session = repository.findById(id);
            session.setMaxInactiveInterval(Duration.ofSeconds(1));
            repository.save(session);
            await().atMost(Duration.ofSeconds(6)).until(() -> repository.findById(id) == null);
            assertEquals(40100, body(call(app, HttpMethod.GET, "/get/login", cookie, null))
                    .path("code").asInt());
        }
    }

    @Test
    void reloginRotatesSessionAndRevokesOldCookie() throws Exception {
        try (ServletWebServerApplicationContext app = startApplication(namespace())) {
            String oldCookie = login(app, null).getHeaders().getFirst(HttpHeaders.SET_COOKIE).split(";", 2)[0];
            String newCookie = login(app, oldCookie).getHeaders().getFirst(HttpHeaders.SET_COOKIE).split(";", 2)[0];
            assertNotEquals(oldCookie, newCookie);
            assertNull(repository(app).findById(decodeId(oldCookie)));
            assertEquals(40100, body(call(app, HttpMethod.GET, "/get/login", oldCookie, null))
                    .path("code").asInt());
            assertEquals(0, body(call(app, HttpMethod.GET, "/get/login", newCookie, null))
                    .path("code").asInt());
        }
    }

    private static ServletWebServerApplicationContext startApplication(String namespace) {
        return startApplication(namespace, TEST_ORIGIN);
    }

    private static ServletWebServerApplicationContext startApplication(String namespace, String allowedOrigin) {
        SpringApplicationBuilder builder = new SpringApplicationBuilder(SessionTestApplication.class);
        if (allowedOrigin != null) {
            builder.properties("picture.collaboration.allowed-origins=" + allowedOrigin);
        }
        return (ServletWebServerApplicationContext) builder
                .run("--spring.config.location=classpath:application-session.yml",
                        "--server.port=0", "--server.address=127.0.0.1", "--server.servlet.context-path=/api",
                        "--spring.redis.host=127.0.0.1", "--spring.redis.port=" + redisPort,
                        "--spring.redis.username=", "--spring.redis.password=", "--spring.redis.database=0",
                        "--spring.session.redis.namespace=" + namespace,
                        "--spring.session.timeout=30m", "--spring.main.banner-mode=off",
                        "--logging.level.root=WARN");
    }

    @Test
    void defaultWebSocketOriginsAllowLocalDevelopmentButRejectExternalSites() throws Exception {
        try (ServletWebServerApplicationContext app = startApplication(namespace(), null)) {
            String cookie = login(app, null).getHeaders().getFirst(HttpHeaders.SET_COOKIE).split(";", 2)[0];
            StandardWebSocketClient client = new StandardWebSocketClient();
            java.net.URI url = java.net.URI.create("ws://127.0.0.1:" + app.getWebServer().getPort()
                    + "/api/ws/picture/edit?pictureId=55");
            WebSocketHttpHeaders headers = new WebSocketHttpHeaders();
            headers.set(HttpHeaders.COOKIE, cookie);
            headers.setOrigin("http://localhost:5173");
            try (WebSocketSession connection = client.doHandshake(new TextWebSocketHandler(), headers, url)
                    .get(5, TimeUnit.SECONDS)) {
                assertTrue(connection.isOpen());
            }
            headers.setOrigin(TEST_ORIGIN);
            assertThrows(ExecutionException.class, () -> client.doHandshake(new TextWebSocketHandler(), headers, url)
                    .get(5, TimeUnit.SECONDS));
        }
    }

    @Test
    void realWebSocketUsesRedisSessionAndRejectsOriginViewerAndRevokedMembership() throws Exception {
        try (ServletWebServerApplicationContext app = startApplication(namespace())) {
            String cookie = login(app, null).getHeaders().getFirst(HttpHeaders.SET_COOKIE).split(";", 2)[0];
            StandardWebSocketClient client = new StandardWebSocketClient();
            java.net.URI url = java.net.URI.create("ws://127.0.0.1:" + app.getWebServer().getPort() + "/api/ws/picture/edit?pictureId=55&userId=999");
            WebSocketHttpHeaders headers = new WebSocketHttpHeaders(); headers.setOrigin(TEST_ORIGIN); headers.set(HttpHeaders.COOKIE, cookie);
            WebSocketHttpHeaders evil = new WebSocketHttpHeaders(); evil.setOrigin("https://evil.example"); evil.set(HttpHeaders.COOKIE, cookie);
            assertThrows(ExecutionException.class, () -> client.doHandshake(new TextWebSocketHandler(), evil, url).get(5, TimeUnit.SECONDS));
            WebSocketHttpHeaders anonymous = new WebSocketHttpHeaders(); anonymous.setOrigin(TEST_ORIGIN);
            assertThrows(ExecutionException.class, () -> client.doHandshake(new TextWebSocketHandler(), anonymous, url).get(5, TimeUnit.SECONDS));
            SocketProbe first = new SocketProbe(), second = new SocketProbe();
            try (WebSocketSession a = client.doHandshake(first, headers, url).get(5, TimeUnit.SECONDS);
                 WebSocketSession b = client.doHandshake(second, headers, url).get(5, TimeUnit.SECONDS)) {
                first.next(s -> s.path("state").path("revision").asText().equals("0"));
                a.sendMessage(new TextMessage("{\"type\":\"ENTER_EDIT\"}"));
                JsonNode owner = first.next(s -> s.path("state").path("editing").asBoolean());
                assertEquals("42", owner.path("state").path("editorId").asText()); // spoofed URL identity ignored
                JsonNode observer = second.next(s -> s.path("state").path("editorId").asText().equals("42"));
                assertTrue(observer.path("state").path("leaseToken").isNull());
                b.sendMessage(new TextMessage("{\"type\":\"ENTER_EDIT\"}")); second.next(s -> s.path("type").asText().equals("ERROR"));
                a.sendMessage(new TextMessage("{\"type\":\"EDIT_ACTION\",\"action\":\"ROTATE_LEFT\",\"revision\":\"1\"}"));
                second.next(s -> s.path("state").path("rotation").asInt() == 3);
                // Downgrade is read from membership on the next message, not cached in the connection.
                SpaceUser member = new SpaceUser(); member.setSpaceRole("viewer");
                when(app.getBean(SpaceUserMapper.class).findMember(66L, 42L)).thenReturn(member);
                a.sendMessage(new TextMessage("{\"type\":\"HEARTBEAT\"}"));
                assertTrue(first.closed.await(5, TimeUnit.SECONDS));
                app.getBean(CollaborationService.class).tick(); assertTrue(second.closed.await(5, TimeUnit.SECONDS));
                assertThrows(ExecutionException.class, () -> client.doHandshake(new TextWebSocketHandler(), headers, url).get(5, TimeUnit.SECONDS));
            }
            SpaceUser editor = new SpaceUser(); editor.setSpaceRole("editor");
            when(app.getBean(SpaceUserMapper.class).findMember(66L, 42L)).thenReturn(editor);
            SocketProbe third = new SocketProbe();
            try (WebSocketSession c = client.doHandshake(third, headers, url).get(5, TimeUnit.SECONDS)) {
                third.next(s -> true); call(app, HttpMethod.POST, "/logout", cookie, null);
                c.sendMessage(new TextMessage("{\"type\":\"HEARTBEAT\"}")); assertTrue(third.closed.await(5, TimeUnit.SECONDS));
            }
        }
    }

    static class SocketProbe extends TextWebSocketHandler {
        final BlockingQueue<JsonNode> messages = new LinkedBlockingQueue<>();
        final CountDownLatch closed = new CountDownLatch(1);
        @Override protected void handleTextMessage(WebSocketSession s, TextMessage m) throws Exception { messages.add(JSON.readTree(m.getPayload())); }
        @Override public void afterConnectionClosed(WebSocketSession s, CloseStatus c) { closed.countDown(); }
        JsonNode next(java.util.function.Predicate<JsonNode> condition) throws Exception {
            long until = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
            while (System.nanoTime() < until) {
                JsonNode value = messages.poll(100, TimeUnit.MILLISECONDS);
                if (value != null && condition.test(value)) return value;
            }
            throw new AssertionError("Expected socket state did not arrive");
        }
    }

    @SuppressWarnings("unchecked")
    private static SessionRepository<Session> repository(ServletWebServerApplicationContext app) {
        return (SessionRepository<Session>) app.getBean(SessionRepository.class);
    }

    private static String namespace() {
        return "tuku1:test:session:" + UUID.randomUUID();
    }

    private static String decodeId(String cookie) {
        return new String(Base64.getDecoder().decode(cookie.substring("JSESSIONID=".length())),
                StandardCharsets.UTF_8);
    }

    private static ResponseEntity<String> login(ServletWebServerApplicationContext app, String cookie) {
        return call(app, HttpMethod.POST, "/login", cookie,
                "{\"userAccount\":\"testuser\",\"userPassword\":\"password123\"}");
    }

    private static ResponseEntity<String> call(ServletWebServerApplicationContext app, HttpMethod method,
                                               String path, String cookie, String body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (cookie != null) {
            headers.set(HttpHeaders.COOKIE, cookie);
        }
        return new RestTemplate().exchange("http://127.0.0.1:" + app.getWebServer().getPort() + "/api/user" + path,
                method, new HttpEntity<>(body, headers), String.class);
    }

    private static JsonNode body(ResponseEntity<String> response) throws IOException {
        return JSON.readTree(response.getBody());
    }

    @TestConfiguration(proxyBeanMethods = false)
    @EnableAutoConfiguration(exclude = DataSourceAutoConfiguration.class)
    @Import({UserController.class, GlobalExceptionHandler.class, CollaborationService.class,
            SessionCollaborationAccess.class, CollaborationQueue.class, PictureWebSocket.class,
            CollaborationWebSocketConfig.class, PictureAccessManager.class})
    static class SessionTestApplication {
        static final String FIXTURE_HASH = learn.gjx.tuku1.security.UserPasswords.hash("password123");
        @Bean learn.gjx.tuku1.security.LoginAttemptLimiter loginAttemptLimiter(org.springframework.data.redis.core.StringRedisTemplate redis) {
            return new learn.gjx.tuku1.security.LoginAttemptLimiter(redis, 5, 20, 900);
        }

        @Bean
        UserMapper userMapper() {
            UserMapper mapper = mock(UserMapper.class);
            User u = new User(); u.setId(42L); u.setUserName("Session test user"); u.setUserRole(0); u.setIsDelete(0);
            u.setUserPassword(FIXTURE_HASH);
            when(mapper.selectById(42L)).thenReturn(u); return mapper;
        }

        @Bean PictureMapper pictureMapper() {
            PictureMapper mapper = mock(PictureMapper.class); Picture p = new Picture();
            p.setId(55L); p.setSpaceId(66L); p.setUrl("test/private.png"); when(mapper.selectById(55L)).thenReturn(p); return mapper;
        }
        @Bean SpaceMapper spaceMapper() {
            SpaceMapper mapper = mock(SpaceMapper.class); Space s = new Space(); s.setId(66L); s.setSpaceType(1);
            when(mapper.selectById(66L)).thenReturn(s); return mapper;
        }
        @Bean SpaceUserMapper spaceUserMapper() {
            SpaceUserMapper mapper = mock(SpaceUserMapper.class); SpaceUser m = new SpaceUser(); m.setSpaceRole("editor");
            when(mapper.findMember(66L, 42L)).thenReturn(m); return mapper;
        }

        @Bean
        UserServiceImpl userService() {
            User user = new User();
            user.setId(42L);
            user.setUserAccount("testuser");
            user.setUserName("Session test user");
            user.setUserRole(0);
            user.setIsDelete(0);
            UserServiceImpl service = spy(new UserServiceImpl());
        org.springframework.test.util.ReflectionTestUtils.setField(service, "loginAttemptLimiter", mock(learn.gjx.tuku1.security.LoginAttemptLimiter.class));
            user.setUserPassword(FIXTURE_HASH);
            doReturn(user).when(service).getOne(any());
            doReturn(user).when(service).getById(42L);
            return service;
        }
    }
}
