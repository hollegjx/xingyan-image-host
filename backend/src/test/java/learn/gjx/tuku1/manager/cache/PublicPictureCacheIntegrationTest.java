package learn.gjx.tuku1.manager.cache;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import learn.gjx.tuku1.config.MybatisPlusConfig;
import learn.gjx.tuku1.controller.PictureController;
import learn.gjx.tuku1.exception.GlobalExceptionHandler;
import learn.gjx.tuku1.manager.BingImageSearchManager;
import learn.gjx.tuku1.manager.FileManager;
import learn.gjx.tuku1.manager.PictureFileCleanupManager;
import learn.gjx.tuku1.model.dto.file.PictureUploadResult;
import learn.gjx.tuku1.model.dto.picture.*;
import learn.gjx.tuku1.model.entity.Picture;
import learn.gjx.tuku1.model.entity.User;
import learn.gjx.tuku1.model.vo.PictureVO;
import learn.gjx.tuku1.model.vo.UserVO;
import learn.gjx.tuku1.service.PictureService;
import learn.gjx.tuku1.service.UserService;
import learn.gjx.tuku1.service.impl.PictureServiceImpl;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.support.TransactionTemplate;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Collections;
import java.util.concurrent.TimeUnit;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Real Redis + H2 (or opt-in isolated MySQL schema) + MyBatis + Spring transactions. */
@EnabledIfEnvironmentVariable(named = "REDIS_SERVER_BIN", matches = ".+")
class PublicPictureCacheIntegrationTest {
    private static Process redisProcess;
    private static ConfigurableApplicationContext app;
    private static String mysqlSchema;
    private static final String MYSQL_URL = System.getenv("MYSQL_TEST_SERVER_URL");

    private static boolean usesMySql() {
        return MYSQL_URL != null && !MYSQL_URL.trim().isEmpty();
    }

    private static java.sql.Connection mysqlAdmin() throws java.sql.SQLException {
        return java.sql.DriverManager.getConnection(MYSQL_URL,
                System.getenv().getOrDefault("MYSQL_TEST_USERNAME", "root"),
                System.getenv().getOrDefault("MYSQL_TEST_PASSWORD", ""));
    }
    private PictureService service;
    private JdbcTemplate jdbc;
    private StringRedisTemplate redis;
    private PictureListRevision revision;
    private PublicPicturePageCache cache;
    private PublicPictureCacheProperties properties;
    private UserService users;
    private final User admin = user(1L, 1);
    private final User owner = user(2L, 0);

    @BeforeAll
    static void start() throws Exception {
        int port;
        try (ServerSocket socket = new ServerSocket(0)) {
            port = socket.getLocalPort();
        }
        Path directory = Files.createTempDirectory("tuku-picture-cache-test-");
        redisProcess = new ProcessBuilder(System.getenv("REDIS_SERVER_BIN"),
                "--bind", "127.0.0.1", "--port", Integer.toString(port),
                "--dir", directory.toString(), "--save", "", "--appendonly", "no")
                .redirectErrorStream(true).redirectOutput(directory.resolve("redis.log").toFile()).start();
        try {
            await().atMost(Duration.ofSeconds(10)).until(() -> {
                try (Socket ignored = new Socket("127.0.0.1", port)) {
                    return true;
                } catch (IOException e) {
                    return false;
                }
            });
            String databaseUrl = "jdbc:h2:mem:picturecache;MODE=MySQL;DB_CLOSE_DELAY=-1";
            if (usesMySql()) {
                // The URL is server-only; a random schema is the only database this suite mutates.
                java.net.URI endpoint = java.net.URI.create(MYSQL_URL.substring("jdbc:".length()));
                if (!"mysql".equals(endpoint.getScheme())
                        || !java.util.Arrays.asList("localhost", "127.0.0.1").contains(endpoint.getHost())
                        || !"/".equals(endpoint.getPath())) {
                    throw new IllegalArgumentException("MYSQL_TEST_SERVER_URL must target a local server with path /");
                }
                mysqlSchema = "tuku_test_" + java.util.UUID.randomUUID().toString().replace("-", "");
                try (java.sql.Connection connection = mysqlAdmin();
                     java.sql.Statement statement = connection.createStatement()) {
                    statement.execute("CREATE DATABASE `" + mysqlSchema
                            + "` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci");
                }
                int query = MYSQL_URL.indexOf('?');
                databaseUrl = query < 0 ? MYSQL_URL + mysqlSchema
                        : MYSQL_URL.substring(0, query) + mysqlSchema + MYSQL_URL.substring(query);
            }
            app = new SpringApplicationBuilder(CacheTestApplication.class).web(WebApplicationType.NONE)
                    .run("--spring.config.location=classpath:application-picture-cache.yml",
                            "--spring.datasource.url=" + databaseUrl,
                            "--spring.datasource.driver-class-name="
                                    + (usesMySql() ? "com.mysql.cj.jdbc.Driver" : "org.h2.Driver"),
                            "--spring.datasource.username="
                                    + (usesMySql() ? System.getenv().getOrDefault("MYSQL_TEST_USERNAME", "root") : "sa"),
                            "--spring.datasource.password="
                                    + (usesMySql() ? System.getenv().getOrDefault("MYSQL_TEST_PASSWORD", "") : ""),
                            "--spring.redis.host=127.0.0.1", "--spring.redis.port=" + port,
                            "--spring.redis.password=", "--spring.redis.timeout=500ms",
                            "--mybatis-plus.configuration.map-underscore-to-camel-case=false",
                            "--mybatis-plus.global-config.db-config.logic-delete-field=isDelete",
                            "--mybatis-plus.global-config.db-config.logic-delete-value=1",
                            "--mybatis-plus.global-config.db-config.logic-not-delete-value=0",
                            "--spring.main.banner-mode=off", "--logging.level.root=WARN");
            JdbcTemplate sql = app.getBean(JdbcTemplate.class);
            if (usesMySql()) {
                // Exercise the real application schema, but remove its hard-coded USE tuku directive.
                String ddl = new String(Files.readAllBytes(java.nio.file.Paths.get("sql/create_sql.sql")),
                        java.nio.charset.StandardCharsets.UTF_8).replaceFirst("(?i)use\\s+tuku\\s*;", "");
                org.springframework.jdbc.datasource.init.ResourceDatabasePopulator populator =
                        new org.springframework.jdbc.datasource.init.ResourceDatabasePopulator();
                populator.setSqlScriptEncoding("UTF-8");
                populator.addScript(new org.springframework.core.io.ByteArrayResource(
                        ddl.getBytes(java.nio.charset.StandardCharsets.UTF_8)));
                populator.execute(app.getBean(javax.sql.DataSource.class));
                return;
            }
            sql.execute("CREATE TABLE picture_list_revision (id TINYINT PRIMARY KEY, revision BIGINT NOT NULL)");
            sql.update("INSERT INTO picture_list_revision VALUES (1, 0)");
            sql.execute("CREATE TABLE picture (id BIGINT AUTO_INCREMENT PRIMARY KEY, url VARCHAR(512),"
                    + "originalUrl VARCHAR(512) NOT NULL, thumbnailUrl VARCHAR(512), sourceUrl VARCHAR(1024),"
                    + "name VARCHAR(128), introduction VARCHAR(512), category VARCHAR(64),"
                    + "tags VARCHAR(512), picSize BIGINT, picWidth INT, picHeight INT, picScale DOUBLE,"
                    + "picFormat VARCHAR(32), picColor VARCHAR(7), userId BIGINT, spaceId BIGINT, uploadStatus INT DEFAULT 0, reviewStatus INT DEFAULT 0,"
                    + "reviewMessage VARCHAR(512), reviewerId BIGINT, reviewTime TIMESTAMP,"
                    + "createTime TIMESTAMP DEFAULT CURRENT_TIMESTAMP, editTime TIMESTAMP, updateTime TIMESTAMP,"
                    + "isDelete INT DEFAULT 0)");
        } catch (Exception e) {
            stop();
            throw e;
        }
    }

    @AfterAll
    static void stop() throws Exception {
        if (app != null) {
            app.close();
        }
        if (redisProcess != null) {
            redisProcess.destroy();
            if (!redisProcess.waitFor(5, TimeUnit.SECONDS)) {
                redisProcess.destroyForcibly();
                redisProcess.waitFor(5, TimeUnit.SECONDS);
            }
        }
        if (mysqlSchema != null) {
            try (java.sql.Connection connection = mysqlAdmin();
                 java.sql.Statement statement = connection.createStatement()) {
                statement.execute("DROP DATABASE `" + mysqlSchema + "`");
            }
            mysqlSchema = null;
        }
    }

    @BeforeEach
    void resetData() {
        service = app.getBean(PictureService.class);
        jdbc = app.getBean(JdbcTemplate.class);
        redis = app.getBean(StringRedisTemplate.class);
        revision = app.getBean(PictureListRevision.class);
        cache = app.getBean(PublicPicturePageCache.class);
        properties = app.getBean(PublicPictureCacheProperties.class);
        users = app.getBean(UserService.class);
        reset(users);
        when(users.isAdmin(any())).thenAnswer(i -> ((User) i.getArgument(0)).getUserRole() == 1);
        when(users.listByIds(anyCollection())).thenReturn(Collections.singletonList(owner));
        when(users.getUserVO(any())).thenAnswer(i -> {
            UserVO vo = new UserVO();
            vo.setUserName("最新用户名");
            return vo;
        });
        jdbc.update("DELETE FROM picture");
        jdbc.update("UPDATE picture_list_revision SET revision = 0");
        // Each test uses a new namespace, no FLUSHDB/KEYS required.
        properties.setNamespace("test:picture:" + java.util.UUID.randomUUID());
        properties.setTtlSeconds(300);
        properties.setJitterSeconds(60);
        properties.setEnabled(true);
        properties.setLocalEnabled(true);
        org.springframework.test.util.ReflectionTestUtils.setField(cache, "retryAfterNanos", 0L);
        addPicture(10, 1, 1);
    }

    @Test
    void publicScopePagingSerializationAndFreshUserEnrichment() throws Exception {
        addPicture(11, 0, 1); // admin draft
        addPicture(12, 1, 0); // pending review
        addPicture(13, 1, 2); // rejected
        PictureQueryRequest malicious = new PictureQueryRequest();
        malicious.setReviewStatus(2);
        malicious.setUploadStatus(0);
        Page<PictureVO> page = service.listPublicPicturePage(malicious, null);
        assertEquals(1, page.getTotal());
        assertEquals(10L, page.getRecords().get(0).getId());
        String key = key();
        assertNotNull(redis.opsForValue().get(key));
        Long ttl = redis.getExpire(key);
        assertNotNull(ttl);
        assertTrue(ttl >= 299 && ttl <= 360);
        // Cache deliberately contains rows only; creator data is hydrated for every response.
        assertFalse(redis.opsForValue().get(key).contains("最新用户名"));
        clearInvocations(users);
        assertEquals("最新用户名", service.listPublicPicturePage(null, null).getRecords().get(0).getUser().getUserName());
        verify(users).listByIds(anyCollection());
    }

    /** Local diagnostic only: real Redis, H2/opt-in MySQL and mocked author enrichment, no HTTP. */
    @Test
    @EnabledIfEnvironmentVariable(named = "RUN_CACHE_BENCHMARK", matches = "true")
    void controlledCacheLatencySample() {
        for (int i = 100; i < 1100; i++) {
            addPicture(i, 1, 1);
        }
        PictureQueryRequest query = new PictureQueryRequest();
        query.setCurrent(1);
        query.setPageSize(12);
        String[] modes = {"database", "redis", "two-level"};
        try {
            for (int round = 0; round < 3; round++) {
                // Rotate execution order to reduce, not eliminate, JVM warmup bias.
                for (int offset = 0; offset < modes.length; offset++) {
                    int mode = (round + offset) % modes.length;
                    properties.setNamespace("test:benchmark:" + java.util.UUID.randomUUID());
                    properties.setEnabled(mode != 0);
                    properties.setLocalEnabled(mode == 2);
                    for (int warmup = 0; warmup < 100; warmup++) {
                        service.listPublicPicturePage(query, null);
                    }
                    long[] samples = new long[300];
                    for (int i = 0; i < samples.length; i++) {
                        long start = System.nanoTime();
                        Page<PictureVO> page = service.listPublicPicturePage(query, null);
                        samples[i] = System.nanoTime() - start;
                        assertEquals(1001, page.getTotal());
                        assertEquals(12, page.getRecords().size());
                    }
                    java.util.Arrays.sort(samples);
                    System.out.printf(java.util.Locale.ROOT,
                            "CACHE_BENCH database=%s round=%d mode=%s samples=300 p50_ms=%.3f p95_ms=%.3f%n",
                            usesMySql() ? "mysql" : "h2", round + 1, modes[mode],
                            samples[149] / 1_000_000.0, samples[284] / 1_000_000.0);
                }
            }
        } finally {
            properties.setEnabled(true);
            properties.setLocalEnabled(true);
        }
    }

    @Test
    void existingPublicHttpContractAndValidationStillWork() throws Exception {
        PictureController controller = new PictureController();
        ReflectionTestUtils.setField(controller, "pictureService", service);
        ReflectionTestUtils.setField(controller, "userService", users);
        MockMvc mvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new GlobalExceptionHandler()).build();
        mvc.perform(post("/picture/list/page/vo").contentType("application/json").content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.records[0].id").value(10));
        mvc.perform(post("/picture/list/page/vo").contentType("application/json")
                        .content("{\"pageSize\":0}"))
                .andExpect(jsonPath("$.code").value(40000));
        verify(users, never()).getLoginUser(any());
    }

    @Test
    void paginationAndFiltersDoNotShareCacheEntries() {
        addPicture(20, 1, 1);
        PictureQueryRequest page1 = new PictureQueryRequest();
        page1.setPageSize(1);
        page1.setSortField("id");
        page1.setSortOrder("asc");
        assertEquals(10L, service.listPublicPicturePage(page1, null).getRecords().get(0).getId());
        page1.setCurrent(2);
        assertEquals(20L, service.listPublicPicturePage(page1, null).getRecords().get(0).getId());
        page1.setCategory("不存在的分类");
        assertEquals(0, service.listPublicPicturePage(page1, null).getTotal());
    }

    @Test
    void reviewRejectionImmediatelyHidesPreviouslyCachedPicture() throws Exception {
        assertEquals(1, service.listPublicPicturePage(null, null).getTotal());
        String oldKey = key();
        PictureReviewRequest review = new PictureReviewRequest();
        review.setId(10L);
        review.setReviewStatus(2);
        service.reviewPicture(review, admin);
        assertEquals(1, revision.current());
        assertNotNull(redis.opsForValue().get(oldKey)); // Logical invalidation, not physical deletion.
        assertEquals(0, service.listPublicPicturePage(null, null).getTotal());
        review.setReviewStatus(1);
        service.reviewPicture(review, admin);
        assertEquals(1, service.listPublicPicturePage(null, null).getTotal());
    }

    @Test
    void ownerEditReturnsToReviewAndInvalidatesAllQueries() {
        service.listPublicPicturePage(null, null);
        PictureQueryRequest filter = new PictureQueryRequest();
        filter.setCategory("风景");
        service.listPublicPicturePage(filter, null);
        PictureEditRequest edit = new PictureEditRequest();
        edit.setId(10L);
        edit.setName("用户编辑");
        service.editPicture(edit, owner);
        assertEquals(0, service.getById(10L).getReviewStatus());
        assertEquals(0, service.listPublicPicturePage(null, null).getTotal());
        assertEquals(0, service.listPublicPicturePage(filter, null).getTotal());
    }

    @Test
    void adminUpdateAndDeleteInvalidateWarmCache() {
        service.listPublicPicturePage(null, null);
        PictureUpdateRequest update = new PictureUpdateRequest();
        update.setId(10L);
        update.setName("新名称");
        service.updatePicture(update, admin);
        assertEquals("新名称", service.listPublicPicturePage(null, null).getRecords().get(0).getName());
        service.deletePicture(10L, admin);
        assertEquals(0, service.listPublicPicturePage(null, null).getTotal());
        assertEquals(2, revision.current());
    }

    @Test
    void replacementUploadInvalidatesAndKeepsFileCleanupBehavior() {
        service.listPublicPicturePage(null, null);
        FileManager files = app.getBean(FileManager.class);
        PictureUploadResult uploaded = new PictureUploadResult();
        uploaded.setUrl("https://example.test/new.jpg");
        uploaded.setPicName("替换图片");
        when(files.uploadPicture(any(), anyString())).thenReturn(uploaded);
        PictureUploadRequest request = new PictureUploadRequest();
        request.setId(10L);
        service.uploadPicture(new MockMultipartFile("file", "new.jpg", "image/jpeg", new byte[]{1}), request, admin);
        Picture saved = service.getById(10L);
        assertEquals(uploaded.getUrl(), saved.getUrl());
        assertEquals(uploaded.getUrl(), saved.getOriginalUrl());
        assertNull(saved.getThumbnailUrl());
        assertEquals(uploaded.getUrl(), service.listPublicPicturePage(null, null).getRecords().get(0).getUrl());
        assertEquals(1, revision.current());
        verify(app.getBean(PictureFileCleanupManager.class), atLeastOnce())
                .deleteQuietly("https://example.test/old.jpg", "replace-picture");
    }

    @Test
    void newDraftIsNotPublicUntilCompleted() {
        PictureUploadResult uploaded = new PictureUploadResult();
        uploaded.setUrl("https://example.test/draft.jpg");
        uploaded.setPicName("草稿");
        when(app.getBean(FileManager.class).uploadPicture(any(), anyString())).thenReturn(uploaded);
        PictureVO draft = service.uploadPicture(
                new MockMultipartFile("file", "draft.jpg", "image/jpeg", new byte[]{1}),
                new PictureUploadRequest(), admin);
        Picture savedDraft = service.getById(draft.getId());
        assertEquals(uploaded.getUrl(), savedDraft.getOriginalUrl());
        assertNull(savedDraft.getThumbnailUrl());
        assertEquals(1, service.listPublicPicturePage(null, null).getTotal());
        PictureEditRequest edit = new PictureEditRequest();
        edit.setId(draft.getId());
        edit.setName("已完成");
        service.editPicture(edit, admin);
        assertEquals(2, service.listPublicPicturePage(null, null).getTotal());
    }

    @Test
    void derivedUrlsSurviveReplacementAndRefreshBothCacheLevels() {
        service.listPublicPicturePage(null, null);
        PictureUploadResult uploaded = new PictureUploadResult();
        uploaded.setUrl("https://example.test/display.webp");
        uploaded.setOriginalUrl("https://example.test/original.jpg");
        uploaded.setThumbnailUrl("https://example.test/thumb.webp");
        uploaded.setPicName("派生图片");
        when(app.getBean(FileManager.class).uploadPicture(any(), anyString())).thenReturn(uploaded);
        PictureUploadRequest request = new PictureUploadRequest();
        request.setId(10L);
        service.uploadPicture(new MockMultipartFile("file", "image.jpg", "image/jpeg", new byte[]{1}), request, admin);
        Picture saved = service.getById(10L);
        assertEquals(uploaded.getOriginalUrl(), saved.getOriginalUrl());
        assertEquals(uploaded.getThumbnailUrl(), saved.getThumbnailUrl());
        for (int i = 0; i < 2; i++) {
            PictureVO visible = service.listPublicPicturePage(null, null).getRecords().get(0);
            assertEquals(uploaded.getUrl(), visible.getUrl());
            assertEquals(uploaded.getThumbnailUrl(), visible.getThumbnailUrl());
        }
        assertEquals(1, revision.current());
        // A later fallback-only upload must clear, not preserve, the previous thumbnail.
        uploaded.setOriginalUrl("https://example.test/fallback.gif");
        uploaded.setUrl(uploaded.getOriginalUrl());
        uploaded.setThumbnailUrl(null);
        service.uploadPicture(new MockMultipartFile("file", "image.gif", "image/gif", new byte[]{1}), request, admin);
        assertNull(service.getById(10L).getThumbnailUrl());
        assertNull(service.listPublicPicturePage(null, null).getRecords().get(0).getThumbnailUrl());
        assertEquals(2, revision.current());
    }

    @Test
    void batchUrlImportsInvalidatePreviouslyCachedEmptySearch() {
        PictureQueryRequest query = new PictureQueryRequest();
        query.setCategory("批量");
        assertEquals(0, service.listPublicPicturePage(query, null).getTotal());
        when(app.getBean(BingImageSearchManager.class).search("猫"))
                .thenReturn(Collections.singletonList("https://example.test/cat.jpg"));
        PictureUploadResult uploaded = new PictureUploadResult();
        uploaded.setUrl("https://example.test/imported.jpg");
        uploaded.setPicName("猫");
        when(app.getBean(FileManager.class).uploadPictureByUrl(anyString(), anyString())).thenReturn(uploaded);
        PictureUploadByBatchRequest batch = new PictureUploadByBatchRequest();
        batch.setSearchText("猫");
        batch.setCategory("批量");
        batch.setCount(1);
        assertEquals(1, service.uploadPictureByBatch(batch, admin));
        assertEquals(1, revision.current());
        assertEquals(1, service.listPublicPicturePage(query, null).getTotal());
    }

    @Test
    void rollbackDoesNotAdvanceVersionOrExposeUncommittedRows() {
        service.listPublicPicturePage(null, null);
        TransactionTemplate transaction = app.getBean(TransactionTemplate.class);
        transaction.execute(status -> {
            jdbc.update("UPDATE picture SET name = '事务中' WHERE id = 10");
            revision.advance();
            assertEquals("事务中", service.listPublicPicturePage(null, null).getRecords().get(0).getName());
            status.setRollbackOnly();
            return null;
        });
        assertEquals(0, revision.current());
        assertEquals("原名称", service.listPublicPicturePage(null, null).getRecords().get(0).getName());
        assertThrows(IllegalTransactionStateException.class, revision::advance);
    }

    @Test
    void failedRevisionUpdateRollsBackPictureMutation() {
        // MySQL 5.7 parses but does not enforce CHECK constraints; use a trigger there.
        jdbc.execute(usesMySql()
                ? "CREATE TRIGGER revision_frozen BEFORE UPDATE ON picture_list_revision FOR EACH ROW "
                    + "SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'test revision failure'"
                : "ALTER TABLE picture_list_revision ADD CONSTRAINT revision_frozen CHECK (revision = 0)");
        try {
            PictureUpdateRequest update = new PictureUpdateRequest();
            update.setId(10L);
            update.setName("不应提交");
            assertThrows(RuntimeException.class, () -> service.updatePicture(update, admin));
            assertEquals("原名称", service.getById(10L).getName());
            assertEquals(0, revision.current());
        } finally {
            jdbc.execute(usesMySql() ? "DROP TRIGGER revision_frozen"
                    : "ALTER TABLE picture_list_revision DROP CONSTRAINT revision_frozen");
        }
    }

    @Test
    void cacheExpiresAndReloadsUsingRealRedis() throws Exception {
        properties.setTtlSeconds(1);
        properties.setJitterSeconds(0);
        service.listPublicPicturePage(null, null);
        String key = key();
        assertNotNull(redis.opsForValue().get(key));
        await().atMost(Duration.ofSeconds(5)).until(() -> !Boolean.TRUE.equals(redis.hasKey(key)));
        assertEquals(1, service.listPublicPicturePage(null, null).getTotal());
        assertNotNull(redis.opsForValue().get(key));
    }

    @Test
    void redisWriteFailureCannotLoseCommittedInvalidation() {
        service.listPublicPicturePage(null, null);
        // Only this isolated test Redis is changed. Force SET to fail with OOM/noeviction.
        org.springframework.data.redis.connection.RedisConnection connection = redis.getConnectionFactory().getConnection();
        try {
            connection.serverCommands().setConfig("maxmemory", "1");
            PictureReviewRequest request = new PictureReviewRequest();
            request.setId(10L);
            request.setReviewStatus(2);
            service.reviewPicture(request, admin);
            assertEquals(1, revision.current());
            assertEquals(0, service.listPublicPicturePage(null, null).getTotal());
        } finally {
            connection.serverCommands().setConfig("maxmemory", "0");
            connection.close();
        }
        org.springframework.test.util.ReflectionTestUtils.setField(cache, "retryAfterNanos", 0L);
        // Recovery still selects the new durable version, never resurrecting the old approved list.
        properties.setLocalEnabled(false); // Prove Redis recovery, not just the newly filled L1.
        assertEquals(0, service.listPublicPicturePage(null, null).getTotal());
    }

    @Test
    void twoIndependentLocalCachesObserveCommittedReviewRejection() {
        PictureQueryRequest query = PublicPictureQuery.normalize(null);
        PublicPicturePageCache secondBackend = new PublicPicturePageCache(redis, revision, properties);
        service.listPublicPicturePage(null, null); // First backend fills Redis and its own L1.
        assertEquals(1, secondBackend.get(query, () -> {
            throw new AssertionError("second backend should read Redis");
        }).getTotal());
        assertEquals(1, secondBackend.get(query, () -> {
            throw new AssertionError("second backend should read L1");
        }).getTotal());

        PictureReviewRequest review = new PictureReviewRequest();
        review.setId(10L);
        review.setReviewStatus(2);
        service.reviewPicture(review, admin); // Real Spring transaction updates the durable revision.
        assertEquals(0, service.listPublicPicturePage(null, null).getTotal());
        assertEquals(0, secondBackend.get(query, () -> {
            throw new AssertionError("new generation should already exist in Redis");
        }).getTotal());
    }

    @Test
    void realRedisRemainingTtlLimitsPromotionIntoLocalCache() throws Exception {
        properties.setLocalEnabled(false);
        service.listPublicPicturePage(null, null);
        String key = key();
        assertTrue(Boolean.TRUE.equals(redis.expire(key, Duration.ofSeconds(1))));
        properties.setLocalEnabled(true);
        PublicPicturePageCache freshBackend = new PublicPicturePageCache(redis, revision, properties);
        PictureQueryRequest query = PublicPictureQuery.normalize(null);
        freshBackend.get(query, () -> { throw new AssertionError("expected Redis hit"); });
        // Wait only for our isolated Redis key to expire; no production key is touched.
        await().atMost(Duration.ofSeconds(5)).until(() -> !Boolean.TRUE.equals(redis.hasKey(key)));
        java.util.concurrent.atomic.AtomicInteger loads = new java.util.concurrent.atomic.AtomicInteger();
        await().atMost(Duration.ofSeconds(2)).until(() -> {
            freshBackend.get(query, () -> {
                loads.incrementAndGet();
                return new Page<>(1, 10, 0);
            });
            return loads.get() > 0;
        });
        assertEquals(1, loads.get());
    }

    private String key() throws Exception {
        return cache.key(PublicPictureQuery.normalize(null), revision.current());
    }

    @Test
    void privateRowsNeverEnterPublicSqlTotalsOrEitherCacheLevel() {
        addPicture(999, 1, 1);
        jdbc.update("UPDATE picture SET spaceId=101 WHERE id=999");
        for (int i = 0; i < 3; i++) {
            Page<PictureVO> page = service.listPublicPicturePage(null, null);
            assertEquals(1, page.getTotal());
            assertTrue(page.getRecords().stream().allMatch(p -> p.getSpaceId() == null));
        }
        properties.setLocalEnabled(false);
        assertEquals(1, service.listPublicPicturePage(null, null).getTotal());
    }

    @Test
    void oldNamespaceAndPoisonedRedisCannotExposePrivateRows() throws Exception {
        String payload = "{\"current\":1,\"size\":10,\"total\":1,\"records\":["
                + "{\"id\":999,\"spaceId\":101,\"reviewStatus\":1,\"uploadStatus\":1}]}";
        String key = key();
        assertTrue(key.contains(":v2:"));
        redis.opsForValue().set(key.replace(":v2:", ":v1:"), payload);
        redis.opsForValue().set(key, payload);
        Page<PictureVO> page = service.listPublicPicturePage(null, null);
        assertEquals(10L, page.getRecords().get(0).getId());
        assertFalse(redis.opsForValue().get(key).contains("\"spaceId\":101"));
    }

    private void addPicture(long id, int upload, int review) {
        jdbc.update("INSERT INTO picture(id,url,originalUrl,name,category,userId,uploadStatus,reviewStatus,isDelete)"
                + " VALUES(?, 'https://example.test/old.jpg', 'https://example.test/old.jpg',"
                + " '原名称', '风景', 2, ?, ?, 0)", id, upload, review);
    }

    private static User user(long id, int role) {
        User user = new User();
        user.setId(id);
        user.setUserRole(role);
        return user;
    }

    @TestConfiguration(proxyBeanMethods = false)
    @EnableAutoConfiguration
    @Import({MybatisPlusConfig.class, PictureServiceImpl.class, PublicPicturePageCache.class,
            PictureListRevision.class, PublicPictureCacheProperties.class,
            learn.gjx.tuku1.manager.space.PictureAccessManager.class})
    static class CacheTestApplication {
        @Bean UserService userService() { return mock(UserService.class); }
        // Prebuilt test doubles must not have their inherited @Resource fields injected.
        @Bean
        static org.springframework.beans.factory.config.BeanFactoryPostProcessor externalCollaborators() {
            return factory -> {
                factory.registerSingleton("fileManager", mock(FileManager.class));
                factory.registerSingleton("pictureFileCleanupManager", mock(PictureFileCleanupManager.class));
                factory.registerSingleton("bingImageSearchManager", mock(BingImageSearchManager.class));
                factory.registerSingleton("privatePictureWriteManager", mock(learn.gjx.tuku1.manager.space.PrivatePictureWriteManager.class));
            };
        }
    }
}
