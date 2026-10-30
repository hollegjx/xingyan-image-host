package learn.gjx.tuku1.service.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.qcloud.cos.COSClient;
import com.qcloud.cos.model.COSObject;
import com.qcloud.cos.model.GetObjectRequest;
import learn.gjx.tuku1.aop.AuthInterceptor;
import learn.gjx.tuku1.config.*;
import learn.gjx.tuku1.controller.PictureController;
import learn.gjx.tuku1.exception.*;
import learn.gjx.tuku1.manager.*;
import learn.gjx.tuku1.manager.cache.*;
import learn.gjx.tuku1.manager.space.PictureAccessManager;
import learn.gjx.tuku1.manager.space.PrivatePictureWriteManager;
import learn.gjx.tuku1.model.dto.file.PictureUploadResult;
import learn.gjx.tuku1.model.vo.PictureVO;
import learn.gjx.tuku1.model.dto.picture.*;
import learn.gjx.tuku1.model.entity.Picture;
import learn.gjx.tuku1.model.entity.User;
import learn.gjx.tuku1.service.PictureService;
import learn.gjx.tuku1.service.UserService;
import learn.gjx.tuku1.service.SpaceUserService;
import learn.gjx.tuku1.model.dto.space.*;
import learn.gjx.tuku1.manager.space.SpacePermission;
import learn.gjx.tuku1.application.collaboration.*;
import learn.gjx.tuku1.domain.collaboration.EditRoom;
import learn.gjx.tuku1.infrastructure.collaboration.CollaborationQueue;
import learn.gjx.tuku1.infrastructure.collaboration.CollaborationWriteGuard;
import org.junit.jupiter.api.*;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.*;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import javax.servlet.http.HttpServletRequest;
import javax.sql.DataSource;
import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.*;
import java.util.function.Supplier;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Real H2 or isolated MySQL SQL, MVC and authorization. Identity/COS remain test substitutes. */
class PrivatePictureAccessIntegrationTest {
    private static final String MYSQL_URL = System.getenv("MYSQL_TEST_SERVER_URL");
    private static String schema;
    private static ConfigurableApplicationContext app;
    private static JdbcTemplate jdbc;
    private MockMvc mvc;
    private PictureService service;
    private PublicPicturePageCache cache;
    private COSClient cos;
    private final String root = "https://private-test-1250000000.cos.ap-test.myqcloud.com/space/101/";

    private static boolean mysql() { return MYSQL_URL != null && !MYSQL_URL.trim().isEmpty(); }

    private static Connection adminConnection() throws Exception {
        return DriverManager.getConnection(MYSQL_URL,
                System.getenv().getOrDefault("MYSQL_TEST_USERNAME", "root"),
                System.getenv().getOrDefault("MYSQL_TEST_PASSWORD", ""));
    }

    @BeforeAll
    static void start() throws Exception {
        String url = "jdbc:h2:mem:private_" + UUID.randomUUID()
                + ";MODE=MySQL;NON_KEYWORDS=USER;DB_CLOSE_DELAY=-1";
        try {
            if (mysql()) {
                java.net.URI endpoint = java.net.URI.create(MYSQL_URL.substring("jdbc:".length()));
                if (!"mysql".equals(endpoint.getScheme())
                        || !Arrays.asList("localhost", "127.0.0.1").contains(endpoint.getHost())
                        || !"/".equals(endpoint.getPath())) {
                    throw new IllegalArgumentException("Test MySQL URL must be local/tunneled and server-only");
                }
                schema = "tuku_private_test_" + UUID.randomUUID().toString().replace("-", "");
                try (Connection connection = adminConnection(); Statement statement = connection.createStatement()) {
                    statement.execute("CREATE DATABASE `" + schema + "` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci");
                }
                int q = MYSQL_URL.indexOf('?');
                url = q < 0 ? MYSQL_URL + schema : MYSQL_URL.substring(0, q) + schema + MYSQL_URL.substring(q);
            }
            app = new SpringApplicationBuilder(TestApplication.class).web(WebApplicationType.SERVLET).run(
                    "--spring.config.location=optional:classpath:private-picture-empty.yml",
                    "--server.port=0", "--server.address=127.0.0.1", "--spring.session.store-type=none",
                    "--spring.datasource.url=" + url,
                    "--spring.datasource.driver-class-name=" + (mysql() ? "com.mysql.cj.jdbc.Driver" : "org.h2.Driver"),
                    "--spring.datasource.username=" + (mysql() ? System.getenv().getOrDefault("MYSQL_TEST_USERNAME", "root") : "sa"),
                    "--spring.datasource.password=" + (mysql() ? System.getenv().getOrDefault("MYSQL_TEST_PASSWORD", "") : ""),
                    "--mybatis-plus.configuration.map-underscore-to-camel-case=false",
                    "--mybatis-plus.global-config.db-config.logic-delete-field=isDelete",
                    "--spring.main.banner-mode=off", "--logging.level.root=WARN");
            jdbc = app.getBean(JdbcTemplate.class);
            script("sql/create_sql.sql");
            script("sql/alter_ai_picture_task.sql");
        } catch (Exception error) {
            stop();
            throw error;
        }
    }

    private static void script(String path) throws Exception {
        String ddl = new String(Files.readAllBytes(Paths.get(path)), StandardCharsets.UTF_8)
                .replaceFirst("(?i)use\\s+tuku\\s*;", "");
        if (!mysql()) ddl = ddl.replaceAll("(?i)\\s+collate\\s*=?\\s*utf8mb4_unicode_ci", "")
                .replaceAll("(?i)\\s+comment\\s*=?\\s*'[^']*'", "")
                .replaceAll("(?i)NOT NULL\\s+ON UPDATE CURRENT_TIMESTAMP", "ON UPDATE CURRENT_TIMESTAMP NOT NULL")
                .replace("ENGINE=InnoDB", "").replace("DEFAULT CHARSET=utf8mb4", "");
        new ResourceDatabasePopulator(new ByteArrayResource(ddl.getBytes(StandardCharsets.UTF_8)))
                .execute(app.getBean(DataSource.class));
    }

    @AfterAll
    static void stop() throws Exception {
        if (app != null) { app.close(); app = null; }
        if (schema != null) {
            try (Connection connection = adminConnection(); Statement statement = connection.createStatement()) {
                statement.execute("DROP DATABASE `" + schema + "`");
            }
            schema = null;
        }
    }

    @Test
    void cleanupMigrationIsRepeatableAndPreservesExistingPicturesAndUsage() throws Exception {
        List<Map<String, Object>> before = jdbc.queryForList("SELECT * FROM picture ORDER BY id");
        jdbc.execute("DROP TABLE private_file_cleanup");
        script("sql/alter_private_picture_write.sql");
        jdbc.update("INSERT INTO private_file_cleanup(spaceId,url,nextAttemptTime) VALUES(101,'migration-test',CURRENT_TIMESTAMP)");
        script("sql/alter_private_picture_write.sql");
        assertEquals(before, jdbc.queryForList("SELECT * FROM picture ORDER BY id"));
        assertUsage(2, 200);
        assertEquals(1, taskCount());
    }

    @BeforeEach
    void reset() {
        mvc = MockMvcBuilders.webAppContextSetup((WebApplicationContext) app).build();
        service = app.getBean(PictureService.class);
        cache = app.getBean(PublicPicturePageCache.class);
        cos = app.getBean(COSClient.class);
        UserService users = app.getBean(UserService.class);
        org.mockito.Mockito.reset(cache, cos, users, app.getBean(FileManager.class),
                app.getBean(PictureFileCleanupManager.class), app.getBean(PictureListRevision.class));
        // This mock identity is NEVER part of production configuration; real Session/Redis has its own suite.
        when(users.getLoginUser(any())).thenAnswer(invocation -> {
            HttpServletRequest request = invocation.getArgument(0);
            Object user = request == null || request.getSession(false) == null ? null
                    : request.getSession(false).getAttribute("testUser");
            if (!(user instanceof User)) { throw new BusinessException(ErrorCode.NOT_LOGIN_ERROR); }
            return user;
        });
        when(users.isAdmin(any(User.class))).thenAnswer(i -> Objects.equals(((User) i.getArgument(0)).getUserRole(), 1));
        when(users.listByIds(anyCollection())).thenReturn(Collections.emptyList());
        when(cache.get(any(), any())).thenAnswer(i -> ((Supplier<Page<Picture>>) i.getArgument(1)).get());
        PrivatePictureStorageProperties properties = app.getBean(PrivatePictureStorageProperties.class);
        properties.setEnabled(true);
        properties.setBucket("private-test-1250000000");
        jdbc.update("DELETE FROM picture");
        jdbc.update("DELETE FROM space_user");
        jdbc.update("DELETE FROM user");
        jdbc.update("DELETE FROM space");
        jdbc.update("DELETE FROM private_file_cleanup");
        jdbc.update("DELETE FROM ai_picture_task");
        jdbc.update("INSERT INTO space(id,spaceName,userId) VALUES(101,'private',2),(102,'other',3)");
        seed(10, null, 2, 1, 1);
        seed(11, 101L, 3, 1, 1); // Deliberately different creator: space owner, not creator, controls access.
        seed(12, 101L, 2, 0, 0);
        seed(13, 102L, 2, 1, 1);
        seed(14, null, 2, 0, 0);
        jdbc.update("UPDATE space SET totalCount=2,totalSize=200 WHERE id=101");
        jdbc.update("UPDATE space SET totalCount=1,totalSize=100 WHERE id=102");
    }

    private void seed(long id, Long space, long creator, int upload, int review) {
        String url = space == null ? "https://public.test/image.jpg"
                : root.replace("/101/", "/" + space + "/") + "image" + id + ".jpg";
        jdbc.update("INSERT INTO picture(id,url,originalUrl,thumbnailUrl,name,userId,spaceId,uploadStatus,reviewStatus,picSize)"
                        + " VALUES(?,?,?,?,?,?,?,?,?,100)", id, url, url, url, "image", creator, space, upload, review);
    }

    private static User user(long id) {
        User user = new User(); user.setId(id); user.setUserRole(id == 1 ? 1 : 0); return user;
    }

    private static MockHttpSession session(long id) {
        MockHttpSession session = new MockHttpSession(); session.setAttribute("testUser", user(id)); return session;
    }

    @Test
    void approvedPrivateImageStillRequiresLogin() throws Exception {
        mvc.perform(get("/picture/get/vo").param("id", "11")).andExpect(jsonPath("$.code").value(40100));
        mvc.perform(get("/picture/get/vo").param("id", "10"))
                .andExpect(jsonPath("$.code").value(0)).andExpect(jsonPath("$.data.spaceId").doesNotExist());
    }

    @Test
    void creatorAndAdminCannotReadAnotherOwnersPrivateImage() throws Exception {
        for (long id : new long[]{1, 3}) {
            mvc.perform(get("/picture/get/vo").param("id", "11").session(session(id)))
                    .andExpect(jsonPath("$.code").value(40101));
        }
    }

    @Test
    void ownerGetsOnlyProxyUrlsWithContextPath() throws Exception {
        mvc.perform(get("/api/picture/get/vo").contextPath("/api").param("id", "11").session(session(2)))
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.url").value("/api/picture/private/file?id=11&variant=display"))
                .andExpect(jsonPath("$.data.thumbnailUrl").value("/api/picture/private/file?id=11&variant=thumbnail"))
                .andExpect(jsonPath("$.data.originalUrl").doesNotExist())
                .andExpect(header().string("Cache-Control", "private, no-store"));
    }

    @Test
    void deletedSpaceBlocksDetailListAndFile() throws Exception {
        jdbc.update("UPDATE space SET isDelete=1 WHERE id=101");
        mvc.perform(get("/picture/get/vo").param("id", "11").session(session(2)))
                .andExpect(jsonPath("$.code").value(40400));
        mvc.perform(post("/picture/list/page/vo").session(session(2)).contentType("application/json")
                .content("{\"spaceId\":101}")).andExpect(jsonPath("$.code").value(40400));
        mvc.perform(get("/picture/private/file").param("id", "11").session(session(2)))
                .andExpect(jsonPath("$.code").value(40400));
        verifyNoInteractions(cos);
    }

    @Test
    void privateListIncludesOnlyItsSpaceAndBypassesPublicCache() throws Exception {
        mvc.perform(post("/picture/list/page/vo").session(session(2)).contentType("application/json")
                        .content("{\"spaceId\":101}"))
                .andExpect(jsonPath("$.code").value(0)).andExpect(jsonPath("$.data.total").value("2"))
                .andExpect(jsonPath("$.data.records[0].spaceId").value("101"))
                .andExpect(header().string("Cache-Control", "private, no-store"));
        verifyNoInteractions(cache);
    }

    @Test
    void forgedUserIdCannotGrantSpaceAccess() throws Exception {
        for (long id : new long[]{1, 3}) {
            mvc.perform(post("/picture/list/page/vo").session(session(id)).contentType("application/json")
                    .content("{\"spaceId\":101,\"userId\":2}"))
                    .andExpect(jsonPath("$.code").value(40101));
        }
        mvc.perform(post("/picture/list/page/vo").contentType("application/json")
                .content("{\"spaceId\":101,\"userId\":2}"))
                .andExpect(jsonPath("$.code").value(40100));
        verifyNoInteractions(cache);
    }

    @Test
    void publicListsAndTotalsExcludeAllPrivateRows() throws Exception {
        mvc.perform(post("/picture/list/page/vo").contentType("application/json").content("{}"))
                .andExpect(jsonPath("$.data.total").value("1")).andExpect(jsonPath("$.data.records[0].id").value("10"));
        mvc.perform(post("/picture/list/page").session(session(1)).contentType("application/json").content("{}"))
                .andExpect(jsonPath("$.data.total").value("2"));
        mvc.perform(post("/picture/my/list/page/vo").session(session(2)).contentType("application/json").content("{}"))
                .andExpect(jsonPath("$.data.total").value("2"));
        jdbc.update("UPDATE picture SET reviewStatus=0,uploadStatus=1 WHERE id IN (11,12,14)");
        mvc.perform(post("/picture/list/page/review").session(session(1)).contentType("application/json").content("{}"))
                .andExpect(jsonPath("$.data.total").value("1")).andExpect(jsonPath("$.data.records[0].id").value("14"));
    }

    @Test
    void rawAndManagementEndpointsCannotExposePrivateRecords() throws Exception {
        mvc.perform(get("/picture/get").param("id", "11").session(session(1)))
                .andExpect(jsonPath("$.code").value(40101));
        for (String path : new String[]{"/picture/list/page", "/picture/list/page/review", "/picture/my/list/page/vo"}) {
            mvc.perform(post(path).session(session(1)).contentType("application/json").content("{\"spaceId\":101}"))
                    .andExpect(jsonPath("$.code").value(40101));
        }
    }

    @Test
    void invalidQueriesCannotInjectSqlOrBypassScope() throws Exception {
        for (String body : new String[]{"{\"spaceId\":0}", "{\"spaceId\":101,\"pageSize\":1000}",
                "{\"spaceId\":101,\"pageSize\":21}",
                "{\"spaceId\":101,\"current\":0}", "{\"spaceId\":101,\"sortField\":\"id desc; select 1\"}"}) {
            mvc.perform(post("/picture/list/page/vo").session(session(2)).contentType("application/json").content(body))
                    .andExpect(jsonPath("$.code").value(40000));
        }
    }

    private void seedSearchMetadata() {
        java.sql.Timestamp start = java.sql.Timestamp.from(java.time.Instant.parse("2026-10-01T00:00:00Z"));
        java.sql.Timestamp end = java.sql.Timestamp.from(java.time.Instant.parse("2026-10-02T00:00:00Z"));
        jdbc.update("UPDATE picture SET name='海边日落',introduction='旅行海岸',category='风景',tags=?,"
                + "picWidth=1920,picHeight=1080,picFormat='png',editTime=?", "[\"海边\",\"旅行\"]", start);
        jdbc.update("UPDATE picture SET editTime=? WHERE id=12", end);
    }

    @Test
    void searchCombinesAllBasicFiltersAndKeepsSpaceBoundary() throws Exception {
        seedSearchMetadata();
        String filters = "\"searchText\":\"海岸\",\"name\":\"日落\",\"introduction\":\"旅行\","
                + "\"category\":\"风景\",\"tags\":[\"旅行\",\"海边\"],\"picWidth\":1920,\"picHeight\":1080,"
                + "\"picFormat\":\" PNG \",\"startEditTime\":\"2026-10-01T00:00:00.000Z\","
                + "\"endEditTime\":\"2026-10-02T00:00:00.000Z\"";
        mvc.perform(post("/picture/list/page/vo").session(session(2)).contentType("application/json")
                .content("{\"spaceId\":101," + filters + "}"))
                .andExpect(jsonPath("$.code").value(0)).andExpect(jsonPath("$.data.total").value("1"))
                .andExpect(jsonPath("$.data.records[0].id").value("11"));
        verifyNoInteractions(cache);
        mvc.perform(post("/picture/list/page/vo").contentType("application/json").content("{" + filters + "}"))
                .andExpect(jsonPath("$.data.total").value("1")).andExpect(jsonPath("$.data.records[0].id").value("10"));
        verifyNoInteractions(cos);
    }

    @Test
    void editTimeUsesInclusiveStartExclusiveEndAndSupportsOpenBounds() throws Exception {
        seedSearchMetadata();
        mvc.perform(post("/picture/list/page/vo").session(session(2)).contentType("application/json")
                .content("{\"spaceId\":101,\"endEditTime\":\"2026-10-02T00:00:00.000Z\"}"))
                .andExpect(jsonPath("$.data.total").value("1")).andExpect(jsonPath("$.data.records[0].id").value("11"));
        mvc.perform(post("/picture/list/page/vo").session(session(2)).contentType("application/json")
                .content("{\"spaceId\":101,\"startEditTime\":\"2026-10-02T00:00:00.000Z\"}"))
                .andExpect(jsonPath("$.data.total").value("1")).andExpect(jsonPath("$.data.records[0].id").value("12"));
        mvc.perform(post("/picture/list/page/vo").session(session(2)).contentType("application/json")
                .content("{\"spaceId\":101,\"picWidth\":800}"))
                .andExpect(jsonPath("$.code").value(0)).andExpect(jsonPath("$.data.total").value("0"));
    }

    @Test
    void searchCannotBypassOwnerValidationOrPublicVisibility() throws Exception {
        seedSearchMetadata();
        String query = "{\"spaceId\":101,\"searchText\":\"日落\",\"picWidth\":1920} ";
        for (long caller : new long[]{1, 3}) {
            mvc.perform(post("/picture/list/page/vo").session(session(caller)).contentType("application/json")
                    .content(query)).andExpect(jsonPath("$.code").value(40101));
        }
        mvc.perform(post("/picture/list/page/vo").contentType("application/json").content(query))
                .andExpect(jsonPath("$.code").value(40100));
        mvc.perform(post("/picture/list/page/vo").contentType("application/json")
                .content("{\"searchText\":\"日落\",\"reviewStatus\":0,\"uploadStatus\":0}"))
                .andExpect(jsonPath("$.data.total").value("1")).andExpect(jsonPath("$.data.records[0].id").value("10"));
    }

    @Test
    void malformedDatesRangesAndDimensionsAreParameterErrors() throws Exception {
        for (String filters : new String[]{
                "\"startEditTime\":\"not-a-date\"", "\"startEditTime\":\"2026-02-30T00:00:00.000Z\"",
                "\"startEditTime\":\"2026-10-02T00:00:00.000Z\",\"endEditTime\":\"2026-10-01T00:00:00.000Z\"",
                "\"startEditTime\":\"2026-10-01T00:00:00.000Z\",\"endEditTime\":\"2026-10-01T00:00:00.000Z\"",
                "\"picWidth\":0", "\"picHeight\":-1", "\"picWidth\":2147483648"}) {
            mvc.perform(post("/picture/list/page/vo").session(session(2)).contentType("application/json")
                    .content("{\"spaceId\":101," + filters + "}"))
                    .andExpect(jsonPath("$.code").value(40000));
        }
        verifyNoInteractions(cache, cos);
    }

    @Test
    void ownerEditsMetadataWithoutChangingScopeReviewOrUsage() throws Exception {
        mvc.perform(post("/picture/edit").session(session(2)).contentType("application/json")
                .content("{\"id\":12,\"name\":\"new name\",\"spaceId\":null,\"userId\":999,\"reviewStatus\":1}"))
                .andExpect(jsonPath("$.code").value(0));
        Picture picture = service.getById(12);
        assertEquals("new name", picture.getName());
        assertEquals(101L, picture.getSpaceId()); assertEquals(2L, picture.getUserId());
        assertEquals(1, picture.getUploadStatus()); assertEquals(0, picture.getReviewStatus());
        verifyNoInteractions(app.getBean(PictureListRevision.class));
        assertEquals(2L, jdbc.queryForObject("SELECT totalCount FROM space WHERE id=101", Long.class));
    }

    @Test
    void privateEditDeniedToCreatorAndNonOwnerAdmin() throws Exception {
        for (long id : new long[]{1, 3}) {
            mvc.perform(post("/picture/edit").session(session(id)).contentType("application/json")
                    .content("{\"id\":11,\"name\":\"hacked\"}"))
                    .andExpect(jsonPath("$.code").value(40101));
        }
        assertEquals("image", service.getById(11).getName());
    }

    @Test
    void administrativeUpdateReviewDeleteCannotModifyPrivateImages() throws Exception {
        for (String path : new String[]{"/picture/update", "/picture/review", "/picture/delete"}) {
            mvc.perform(post(path).session(session(1)).contentType("application/json")
                    .content("{\"id\":11,\"name\":\"hacked\",\"reviewStatus\":2}"))
                    .andExpect(jsonPath("$.code").value(40101));
        }
        assertNotNull(service.getById(11));
        verifyNoInteractions(app.getBean(FileManager.class), app.getBean(PictureFileCleanupManager.class));
    }

    @Test
    void unauthorizedPrivateUploadsAndCrossSpaceReplacementFailBeforeFileWrite() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "test.jpg", "image/jpeg", new byte[]{1});
        mvc.perform(multipart("/picture/upload").file(file).param("spaceId", "101").session(session(3)))
                .andExpect(jsonPath("$.code").value(40101));
        mvc.perform(multipart("/picture/upload").file(file).param("id", "11").param("spaceId", "102").session(session(2)))
                .andExpect(jsonPath("$.code").value(40000));
        mvc.perform(post("/picture/upload/url").session(session(3)).contentType("application/json")
                .content("{\"spaceId\":101,\"fileUrl\":\"https://example.test/x.jpg\"}"))
                .andExpect(jsonPath("$.code").value(40101));
        mvc.perform(post("/picture/upload/batch").session(session(1)).contentType("application/json")
                .content("{\"spaceId\":101,\"searchText\":\"x\"}"))
                .andExpect(jsonPath("$.code").value(40101));
        verifyNoInteractions(app.getBean(FileManager.class), cos);
    }

    @Test
    void privateProcessingCannotCreateUnaccountedPublicObjects() throws Exception {
        mvc.perform(post("/picture/process").session(session(2)).contentType("application/json")
                .content("{\"pictureId\":11,\"rules\":[],\"outputDir\":\"public/2\"}"))
                .andExpect(jsonPath("$.code").value(40101));
        verifyNoInteractions(app.getBean(FileManager.class));
    }

    @Test
    void draftCleanupReleasesPrivateUsageAndKeepsPublicCleanupRoute() {
        service.cleanExpiredDraftPictures(new Date(System.currentTimeMillis() + 10000), 100);
        assertNull(service.getById(12)); assertNull(service.getById(14));
        assertUsage(1, 100);
        assertEquals(1, taskCount());
        verify(app.getBean(PictureFileCleanupManager.class)).enqueueCommitted(Collections.singletonList("https://public.test/image.jpg"));
    }

    @Test
    void everyFileVariantRechecksSessionAndStreamsWithoutCosRedirect() throws Exception {
        when(cos.getObject(any(GetObjectRequest.class))).thenAnswer(i -> {
            COSObject object = new COSObject();
            object.setObjectContent(new com.qcloud.cos.model.COSObjectInputStream(
                    new ByteArrayInputStream(new byte[]{1, 2, 3}), new org.apache.http.client.methods.HttpGet()));
            return object;
        });
        for (String variant : new String[]{"display", "thumbnail", "original"}) {
            mvc.perform(get("/api/picture/private/file").contextPath("/api").session(session(2))
                    .param("id", "11").param("variant", variant))
                    .andExpect(status().isOk()).andExpect(content().bytes(new byte[]{1, 2, 3}))
                    .andExpect(header().string("Cache-Control", "private, no-store"))
                    .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                    .andExpect(header().doesNotExist("Location"));
        }
        clearInvocations(cos);
        mvc.perform(get("/picture/private/file").param("id", "11")).andExpect(jsonPath("$.code").value(40100));
        mvc.perform(get("/picture/private/file").param("id", "11").session(session(3)))
                .andExpect(jsonPath("$.code").value(40101));
        verifyNoInteractions(cos);
    }

    @Test
    void disabledStorageAndInvalidReferencesFailClosed() throws Exception {
        app.getBean(PrivatePictureStorageProperties.class).setEnabled(false);
        mvc.perform(get("/picture/private/file").param("id", "11").session(session(2)))
                .andExpect(jsonPath("$.code").value(50001));
        app.getBean(PrivatePictureStorageProperties.class).setEnabled(true);
        jdbc.update("UPDATE picture SET url='https://public.test/leaked.jpg' WHERE id=11");
        mvc.perform(get("/picture/private/file").param("id", "11").session(session(2)))
                .andExpect(jsonPath("$.code").value(50001));
        verifyNoInteractions(cos);
    }

    @Test
    void copiedProxyUrlStopsWorkingAfterOwnershipOrPictureDeletionChanges() throws Exception {
        jdbc.update("UPDATE space SET userId=3 WHERE id=101");
        mvc.perform(get("/picture/private/file").param("id", "11").session(session(2)))
                .andExpect(jsonPath("$.code").value(40101));
        jdbc.update("UPDATE picture SET isDelete=1 WHERE id=11");
        mvc.perform(get("/picture/private/file").param("id", "11").session(session(3)))
                .andExpect(jsonPath("$.code").value(40400));
        verifyNoInteractions(cos);
    }

    @Test
    void directServiceCallsCannotBypassPrivateAuthorization() {
        org.springframework.mock.web.MockHttpServletRequest request = new org.springframework.mock.web.MockHttpServletRequest();
        request.setSession(session(1));
        assertThrows(BusinessException.class, () -> service.getPictureVO(service.getById(11), request));
        assertThrows(BusinessException.class, () -> service.getPictureVOList(
                Collections.singletonList(service.getById(11)), request));
        assertThrows(BusinessException.class, () -> service.getPrivatePictureForRead(11L, request));
        PictureQueryRequest query = new PictureQueryRequest(); query.setSpaceId(101L);
        assertThrows(BusinessException.class, () -> service.listPublicPicturePage(query, request));
        verifyNoInteractions(cache, cos);
    }

    @Test
    void disabledOrUnknownRoleCannotAccessPrivateContent() throws Exception {
        MockHttpSession session = session(2);
        User caller = (User) session.getAttribute("testUser");
        caller.setUserRole(99);
        mvc.perform(get("/picture/get/vo").param("id", "11").session(session))
                .andExpect(jsonPath("$.code").value(40101));
        caller.setUserRole(0); caller.setIsDelete(1);
        mvc.perform(get("/picture/get/vo").param("id", "11").session(session))
                .andExpect(jsonPath("$.code").value(40101));
    }

    @Test
    void existingAdminPageSizeOptionsRemainSupportedButOrdinaryListsAreBounded() throws Exception {
        for (int size : new int[]{10, 20, 50, 100}) {
            mvc.perform(post("/picture/list/page").session(session(1)).contentType("application/json")
                    .content("{\"pageSize\":" + size + "}"))
                    .andExpect(jsonPath("$.code").value(0)).andExpect(jsonPath("$.data.total").value("2"));
        }
        for (String path : new String[]{"/picture/list/page/vo", "/picture/my/list/page/vo"}) {
            mvc.perform(post(path).session(session(2)).contentType("application/json").content("{\"pageSize\":21}"))
                    .andExpect(jsonPath("$.code").value(40000));
        }
    }

    private PictureUploadResult uploaded(long size) {
        String url = root + "upload_" + UUID.randomUUID() + ".jpg";
        PictureUploadResult result = new PictureUploadResult();
        result.setUrl(url); result.setOriginalUrl(url); result.setPicSize(size);
        result.setPicWidth(20); result.setPicHeight(10); result.setPicScale(2.0); result.setPicFormat("jpeg");
        result.setPicName("private upload");
        app.getBean(PrivatePictureCleanupManager.class).registerUpload(101L, Collections.singletonList(url));
        return result;
    }

    private MockMultipartFile file() {
        return new MockMultipartFile("file", "photo.jpg", "image/jpeg", new byte[]{1, 2, 3});
    }

    private PictureUploadRequest uploadRequest() {
        PictureUploadRequest request = new PictureUploadRequest(); request.setSpaceId(101L); return request;
    }

    private void stubUpload(long bytes) {
        when(app.getBean(FileManager.class).uploadPrivatePicture(any(), eq(101L)))
                .thenAnswer(invocation -> uploaded(bytes));
    }

    private AiApplyClaim aiClaim(long pictureId) {
        Picture picture=service.getById(pictureId);
        learn.gjx.tuku1.model.entity.AiPictureTask task=new learn.gjx.tuku1.model.entity.AiPictureTask();
        task.setId(UUID.randomUUID().toString());task.setRequestId(UUID.randomUUID().toString());task.setUserId(2L);
        task.setPictureId(pictureId);task.setSourceUrl(picture.getUrl());task.setXScale(2d);task.setYScale(2d);
        task.setProviderModel("qwen-image-3.0");task.setCreateTime(new Date());task.setExpiresAt(new Date(System.currentTimeMillis()+1800000));
        AiTaskStore store=app.getBean(AiTaskStore.class);store.reserve(task);store.claimGeneration(task.getId(),new Date());store.completed(task.getId(),"https://result.invalid/image.png");
        return store.beginApply(store.find(task.getId(),2L),2L);
    }

    @Test void privateAiApplicationCommitsPictureQuotaCleanupAndTaskTogether() {
        stubUpload(350);AiApplyClaim claim=aiClaim(11L);PictureUploadRequest request=uploadRequest();request.setId(11L);
        service.uploadAiPicture(file(),request,user(2),claim);
        AiTaskStore store=app.getBean(AiTaskStore.class);
        assertEquals("APPLIED",store.find(claim.getTaskId(),2L).getStatus());assertUsage(2,450);
        assertNotEquals(claim.getSourceUrl(),service.getById(11L).getUrl());
        assertEquals(1,taskCount());store.applyUncertain(claim);
        assertEquals("APPLIED",store.find(claim.getTaskId(),2L).getStatus());
    }

    @Test void privateAiRollbackKeepsOriginalAndClaimCanBeSafelyRecovered() {
        stubUpload(350);AiApplyClaim claim=aiClaim(11L);PictureUploadRequest request=uploadRequest();request.setId(11L);
        jdbc.update("UPDATE space SET maxSize=200 WHERE id=101");
        assertThrows(BusinessException.class,()->service.uploadAiPicture(file(),request,user(2),claim));
        AiTaskStore store=app.getBean(AiTaskStore.class);
        assertEquals("APPLYING",store.find(claim.getTaskId(),2L).getStatus());assertUsage(2,200);
        assertEquals(claim.getSourceUrl(),service.getById(11L).getUrl());
        store.applyUncertain(claim);assertTrue(store.recoverApply(store.find(claim.getTaskId(),2L)));assertNull(store.nextQueued());
    }

    @Test void publicAiTaskAndPictureRollbackWhenCleanupOutboxCannotBeWritten() {
        AiApplyClaim claim=aiClaim(10L);PictureUploadRequest request=new PictureUploadRequest();request.setId(10L);
        PictureUploadResult result=new PictureUploadResult();result.setUrl("https://public.test/new.jpg");result.setOriginalUrl(result.getUrl());result.setPicSize(300L);
        when(app.getBean(FileManager.class).uploadPicture(any(),eq("public/2"))).thenReturn(result);
        doThrow(new IllegalStateException("outbox unavailable")).when(app.getBean(PictureFileCleanupManager.class)).enqueueCommitted(anyCollection());
        assertThrows(RuntimeException.class,()->service.uploadAiPicture(file(),request,user(2),claim));
        assertEquals(claim.getSourceUrl(),service.getById(10L).getUrl());
        assertEquals("APPLYING",app.getBean(AiTaskStore.class).find(claim.getTaskId(),2L).getStatus());
        org.mockito.Mockito.reset(app.getBean(PictureFileCleanupManager.class));
        service.uploadAiPicture(file(),request,user(2),claim);
        assertEquals(result.getUrl(),service.getById(10L).getUrl());
        assertEquals("APPLIED",app.getBean(AiTaskStore.class).find(claim.getTaskId(),2L).getStatus());
    }

    private void assertUsage(long count, long bytes) {
        assertEquals(count, jdbc.queryForObject("SELECT totalCount FROM space WHERE id=101", Long.class));
        assertEquals(bytes, jdbc.queryForObject("SELECT totalSize FROM space WHERE id=101", Long.class));
    }

    private void makeCleanupTasksDue() {
        // DATETIME has second precision and may round a just-enqueued millisecond
        // timestamp forward. Test due tasks without relying on wall-clock timing.
        jdbc.update("UPDATE private_file_cleanup SET nextAttemptTime=?", new Date(0));
    }

    private int taskCount() { return jdbc.queryForObject("SELECT COUNT(*) FROM private_file_cleanup", Integer.class); }

    @Test
    void ownerUploadsDraftAndResponseContainsOnlyAuthorizedProxy() throws Exception {
        stubUpload(300);
        mvc.perform(multipart("/api/picture/upload").file(file()).param("spaceId", "101")
                        .contextPath("/api").session(session(2)))
                .andExpect(jsonPath("$.code").value(0)).andExpect(jsonPath("$.data.spaceId").value("101"))
                .andExpect(jsonPath("$.data.url").value(org.hamcrest.Matchers.startsWith("/api/picture/private/file?id=")))
                .andExpect(jsonPath("$.data.originalUrl").doesNotExist())
                .andExpect(jsonPath("$.data.uploadStatus").value(0));
        assertUsage(3, 500); assertEquals(0, taskCount());
        verifyNoInteractions(cache, app.getBean(PictureListRevision.class), cos);
    }

    @Test
    void privateUrlImportUsesSameAccountingAndNotPublicUploader() {
        when(app.getBean(FileManager.class).uploadPrivatePictureByUrl(anyString(), eq(101L)))
                .thenAnswer(invocation -> uploaded(300));
        PictureUploadByUrlRequest request = new PictureUploadByUrlRequest();
        request.setSpaceId(101L); request.setFileUrl("https://example.test/source.jpg");
        PictureVO vo = service.uploadPictureByUrl(request, user(2));
        assertUsage(3, 500);
        assertEquals("https://example.test/source.jpg", service.getById(vo.getId()).getSourceUrl());
        verify(app.getBean(FileManager.class), never()).uploadPictureByUrl(anyString(), anyString());
    }

    @Test
    void replacementInheritsPrivateScopeUsesDeltaAndKeepsOriginalCreator() {
        stubUpload(350);
        jdbc.update("UPDATE space SET maxCount=2 WHERE id=101");
        PictureUploadRequest request = new PictureUploadRequest(); request.setId(11L);
        PictureVO vo = service.uploadPicture(file(), request, user(2));
        assertEquals(11L, vo.getId()); assertEquals(3L, vo.getUserId()); assertEquals(101L, vo.getSpaceId());
        assertEquals(1, vo.getUploadStatus()); assertUsage(2, 450); assertEquals(1, taskCount());
        assertNull(service.getById(11).getThumbnailUrl());
        verifyNoInteractions(app.getBean(PictureListRevision.class), cos);
        makeCleanupTasksDue();
        app.getBean(PrivatePictureCleanupManager.class).retryPending();
        verify(cos).deleteObject("private-test-1250000000", "space/101/image11.jpg");
        assertEquals(0, taskCount());
    }

    @Test void collaborationSaveRequiresCurrentLeaseAndRollbackRetainsIt() {
        team(); stubUpload(350);
        CollaborationService rooms = app.getBean(CollaborationService.class);
        CollaborationAccess auth = app.getBean(CollaborationAccess.class);
        when(auth.authorize("editor", 4L, 11L)).thenAnswer(i -> new CollaborationAccess.Actor(4L, "editor", service.getById(11L)));
        final EditRoom.Snapshot[] state = new EditRoom.Snapshot[1];
        rooms.connect(new CollaborationService.Connection() {
            public String id() { return "editor"; } public String sessionId() { return "editor"; }
            public Long userId() { return 4L; } public Long pictureId() { return 11L; }
            public void send(String type, EditRoom.Snapshot s, String message) { state[0] = s; } public void close() { }
        });
        try {
            rooms.message("editor", "ENTER_EDIT", null, null);
            PictureUploadRequest request = uploadRequest(); request.setId(11L);
            assertThrows(BusinessException.class, () -> service.uploadPicture(file(), request, user(2)));
            assertThrows(BusinessException.class, () -> service.deletePicture(11L, user(2)));
            assertUsage(2, 200);
            request.setCollaborationToken(state[0].leaseToken); request.setCollaborationRevision(0L);
            assertThrows(BusinessException.class, () -> service.uploadPicture(file(), request, user(4)));
            request.setCollaborationRevision(Long.valueOf(state[0].revision));
            jdbc.update("UPDATE space SET maxSize=200 WHERE id=101");
            assertThrows(BusinessException.class, () -> service.uploadPicture(file(), request, user(4)));
            rooms.message("editor", "HEARTBEAT", null, null);
            assertFalse(state[0].saving); assertTrue(state[0].editing); assertUsage(2, 200);
            jdbc.update("UPDATE space SET maxSize=1000 WHERE id=101");
            service.uploadPicture(file(), request, user(4));
            rooms.changed(11L); assertFalse(state[0].editing); assertUsage(2, 450);
            assertEquals(0, state[0].rotation);
        } finally { rooms.disconnect("editor"); }
    }

    @Test
    void smallerReplacementReleasesOnlySizeDifference() {
        stubUpload(40);
        PictureUploadRequest request = uploadRequest(); request.setId(11L);
        service.uploadPicture(file(), request, user(2));
        assertUsage(2, 140);
    }

    @Test
    void countLimitRollsBackPictureAndSchedulesOnlyNewFile() {
        stubUpload(300);
        jdbc.update("UPDATE space SET maxCount=2 WHERE id=101");
        assertThrows(BusinessException.class, () -> service.uploadPicture(file(), uploadRequest(), user(2)));
        assertUsage(2, 200); assertEquals(5L, service.count()); assertEquals(1, taskCount());
        makeCleanupTasksDue();
        app.getBean(PrivatePictureCleanupManager.class).retryPending();
        assertEquals(0, taskCount());
        verify(cos, never()).deleteObject(anyString(), eq("space/101/image11.jpg"));
    }

    @Test
    void capacityLimitAndOverflowNeverChangeLedger() {
        stubUpload(300);
        jdbc.update("UPDATE space SET maxSize=250 WHERE id=101");
        PictureUploadRequest request = uploadRequest(); request.setId(11L);
        assertThrows(BusinessException.class, () -> service.uploadPicture(file(), request, user(2)));
        assertUsage(2, 200);
        jdbc.update("UPDATE space SET totalSize=?,maxSize=? WHERE id=101", Long.MAX_VALUE - 10, Long.MAX_VALUE);
        assertThrows(BusinessException.class, () -> service.uploadPicture(file(), uploadRequest(), user(2)));
        assertUsage(2, Long.MAX_VALUE - 10);
    }

    @Test
    void insertFailureRollsBackAdoptionAndQuota() {
        stubUpload(300);
        jdbc.execute(mysql()
                ? "CREATE TRIGGER reject_test_name BEFORE INSERT ON picture FOR EACH ROW BEGIN IF NEW.name = 'reject-insert' THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='test insert failure'; END IF; END"
                : "ALTER TABLE picture ADD CONSTRAINT reject_test_name CHECK(name <> 'reject-insert')");
        try {
            PictureUploadRequest request = uploadRequest(); request.setName("reject-insert");
            assertThrows(RuntimeException.class, () -> service.uploadPicture(file(), request, user(2)));
            assertUsage(2, 200); assertEquals(5L, service.count()); assertEquals(1, taskCount());
        } finally { jdbc.execute(mysql() ? "DROP TRIGGER reject_test_name" : "ALTER TABLE picture DROP CONSTRAINT reject_test_name"); }
    }

    @Test
    void concurrentUploadsCannotBothUseLastSlot() throws Exception {
        stubUpload(300);
        jdbc.update("UPDATE space SET maxCount=3 WHERE id=101");
        ExecutorService pool = Executors.newFixedThreadPool(8);
        CountDownLatch start = new CountDownLatch(1);
        try {
            List<Future<Boolean>> futures = new ArrayList<>();
            for (int i = 0; i < 8; i++) {
                futures.add(pool.submit(() -> {
                    start.await();
                    try { service.uploadPicture(file(), uploadRequest(), user(2)); return true; }
                    catch (BusinessException expected) { return false; }
                }));
            }
            start.countDown(); int success = 0;
            for (Future<Boolean> future : futures) { if (future.get(20, TimeUnit.SECONDS)) { success++; } }
            assertEquals(1, success); assertUsage(3, 500); assertEquals(7, taskCount());
        } finally { pool.shutdownNow(); }
    }

    @Test
    void concurrentReplacementsDetectStaleOriginalRatherThanDoubleSubtract() throws Exception {
        CyclicBarrier uploadedTogether = new CyclicBarrier(2);
        when(app.getBean(FileManager.class).uploadPrivatePicture(any(), eq(101L))).thenAnswer(invocation -> {
            PictureUploadResult result = uploaded(300);
            uploadedTogether.await(10, TimeUnit.SECONDS); return result;
        });
        ExecutorService pool = Executors.newFixedThreadPool(2);
        try {
            List<Future<Boolean>> futures = new ArrayList<>();
            for (int i = 0; i < 2; i++) {
                futures.add(pool.submit(() -> {
                    PictureUploadRequest request = uploadRequest(); request.setId(11L);
                    try { service.uploadPicture(file(), request, user(2)); return true; }
                    catch (BusinessException expected) { return false; }
                }));
            }
            int success = 0;
            for (Future<Boolean> future : futures) { if (future.get(20, TimeUnit.SECONDS)) { success++; } }
            assertEquals(1, success); assertUsage(2, 400); assertEquals(2, taskCount());
        } finally { pool.shutdownNow(); }
    }

    @Test
    void deletionDuringUploadDoesNotResurrectPictureOrLeakQuota() {
        when(app.getBean(FileManager.class).uploadPrivatePicture(any(), eq(101L))).thenAnswer(invocation -> {
            PictureUploadResult result = uploaded(300);
            service.deletePicture(11L, user(2)); return result;
        });
        PictureUploadRequest request = uploadRequest(); request.setId(11L);
        assertThrows(BusinessException.class, () -> service.uploadPicture(file(), request, user(2)));
        assertNull(service.getById(11)); assertUsage(1, 100); assertEquals(2, taskCount());
    }

    @Test
    void ownerDeletionDeduplicatesFilesAndRepeatedDeleteDoesNotReleaseTwice() throws Exception {
        mvc.perform(post("/picture/delete").session(session(2)).contentType("application/json").content("{\"id\":11}"))
                .andExpect(jsonPath("$.code").value(0));
        assertUsage(1, 100); assertEquals(1, taskCount());
        assertThrows(BusinessException.class, () -> service.deletePicture(11L, user(2)));
        assertUsage(1, 100);
        verifyNoInteractions(app.getBean(PictureListRevision.class), cos);
    }

    @Test
    void ordinaryUserStillCannotDeletePublicPicture() throws Exception {
        mvc.perform(post("/picture/delete").session(session(2)).contentType("application/json").content("{\"id\":10}"))
                .andExpect(jsonPath("$.code").value(40101));
        assertNotNull(service.getById(10));
    }

    @Test
    void cleanupEnqueueFailureRollsBackDeletionAndUsage() {
        jdbc.execute(mysql()
                ? "CREATE TRIGGER reject_cleanup BEFORE INSERT ON private_file_cleanup FOR EACH ROW BEGIN IF NEW.url LIKE '%image11.jpg' THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='test cleanup failure'; END IF; END"
                : "ALTER TABLE private_file_cleanup ADD CONSTRAINT reject_cleanup CHECK(url NOT LIKE '%image11.jpg')");
        try {
            assertThrows(RuntimeException.class, () -> service.deletePicture(11L, user(2)));
            assertNotNull(service.getById(11)); assertUsage(2, 200); assertEquals(0, taskCount());
        } finally { jdbc.execute(mysql() ? "DROP TRIGGER reject_cleanup" : "ALTER TABLE private_file_cleanup DROP CONSTRAINT reject_cleanup"); }
    }

    @Test
    void failedCosDeleteKeepsPersistentTaskAndCanRetryWithFreshManager() {
        service.deletePicture(11L, user(2));
        doThrow(new IllegalStateException("network unavailable")).when(cos).deleteObject(anyString(), anyString());
        makeCleanupTasksDue();
        app.getBean(PrivatePictureCleanupManager.class).retryPending();
        assertEquals(1, taskCount()); assertUsage(1, 100);
        assertEquals(1, jdbc.queryForObject("SELECT attempts FROM private_file_cleanup", Integer.class));
        org.mockito.Mockito.reset(cos);
        jdbc.update("UPDATE private_file_cleanup SET nextAttemptTime=?", new Date(0));
        PrivatePictureCleanupManager restarted = new PrivatePictureCleanupManager();
        org.springframework.test.util.ReflectionTestUtils.setField(restarted, "mapper", app.getBean(learn.gjx.tuku1.mapper.PrivateFileCleanupMapper.class));
        org.springframework.test.util.ReflectionTestUtils.setField(restarted, "storage", app.getBean(PrivatePictureFileManager.class));
        org.springframework.test.util.ReflectionTestUtils.setField(restarted, "transactionTemplate", app.getBean(org.springframework.transaction.support.TransactionTemplate.class));
        restarted.retryPending();
        assertEquals(0, taskCount());
        verify(cos).deleteObject("private-test-1250000000", "space/101/image11.jpg");
    }

    @Test
    void uncertainCommitCompensationNeverDeletesLiveReferencedObject() {
        app.getBean(PrivatePictureCleanupManager.class).abandon(101L, Collections.singleton(root + "image11.jpg"));
        makeCleanupTasksDue();
        app.getBean(PrivatePictureCleanupManager.class).retryPending();
        verifyNoInteractions(cos); assertEquals(0, taskCount()); assertNotNull(service.getById(11));
    }

    @Test
    void abandonedUploadIsReclaimedAfterGraceWithoutTouchingUnexpiredUpload() {
        PictureUploadResult orphan = uploaded(300);
        uploaded(400);
        jdbc.update("UPDATE private_file_cleanup SET nextAttemptTime=? WHERE url=?", new Date(0), orphan.getUrl());
        app.getBean(PrivatePictureCleanupManager.class).retryPending();
        assertEquals(1, taskCount());
        verify(cos).deleteObject("private-test-1250000000", orphan.getUrl().substring(orphan.getUrl().indexOf("space/")));
        assertUsage(2, 200);
    }

    @Test
    void expiredUploadLeaseCannotBeAdopted() {
        when(app.getBean(FileManager.class).uploadPrivatePicture(any(), eq(101L))).thenAnswer(invocation -> {
            PictureUploadResult result = uploaded(300);
            jdbc.update("UPDATE private_file_cleanup SET nextAttemptTime=?", new Date(0)); return result;
        });
        assertThrows(BusinessException.class, () -> service.uploadPicture(file(), uploadRequest(), user(2)));
        assertUsage(2, 200); assertEquals(5L, service.count());
    }

    @Test
    void cleanupRetryBackoffCannotRenewAnExpiredPrivateUploadLease() {
        PictureUploadResult orphan = uploaded(300);
        makeCleanupTasksDue();
        doThrow(new IllegalStateException("delete response lost")).when(cos).deleteObject(anyString(), anyString());
        PrivatePictureCleanupManager cleanup = app.getBean(PrivatePictureCleanupManager.class);
        cleanup.retryPending();
        assertTrue(jdbc.queryForObject("SELECT nextAttemptTime FROM private_file_cleanup", Date.class).after(new Date()));
        assertThrows(BusinessException.class, () -> app.getBean(org.springframework.transaction.support.TransactionTemplate.class)
                .execute(status -> { cleanup.adopt(101L, Collections.singleton(orphan.getUrl())); return null; }));
        assertEquals(1, taskCount());
        assertEquals(1, jdbc.queryForObject("SELECT attempts FROM private_file_cleanup", Integer.class));
        assertUsage(2, 200);
    }

    @Test
    void draftCompletedAfterSelectionIsNotDeleted() {
        Picture candidate = service.getById(12);
        PictureEditRequest edit = new PictureEditRequest(); edit.setId(12L); edit.setName("completed");
        service.editPicture(edit, user(2));
        assertFalse(app.getBean(PrivatePictureWriteManager.class).deleteExpiredDraft(candidate,
                new Date(System.currentTimeMillis() + 10000)));
        assertUsage(2, 200); assertNotNull(service.getById(12));
    }

    @Test
    void privateWriteRejectsOuterTransactionBeforeCos() {
        app.getBean(org.springframework.transaction.support.TransactionTemplate.class).execute(status -> {
            assertThrows(BusinessException.class, () -> service.uploadPicture(file(), uploadRequest(), user(2)));
            return null;
        });
        verifyNoInteractions(app.getBean(FileManager.class)); assertUsage(2, 200);
    }

    @Test
    void ownershipIsRecheckedAfterSlowUpload() {
        when(app.getBean(FileManager.class).uploadPrivatePicture(any(), eq(101L))).thenAnswer(invocation -> {
            PictureUploadResult result = uploaded(300);
            jdbc.update("UPDATE space SET userId=3 WHERE id=101"); return result;
        });
        assertThrows(BusinessException.class, () -> service.uploadPicture(file(), uploadRequest(), user(2)));
        assertUsage(2, 200); assertEquals(5L, service.count()); assertEquals(1, taskCount());
    }

    @Test
    void changedQuotaIsRecheckedAfterSlowUpload() {
        when(app.getBean(FileManager.class).uploadPrivatePicture(any(), eq(101L))).thenAnswer(invocation -> {
            PictureUploadResult result = uploaded(300);
            jdbc.update("UPDATE space SET maxCount=2 WHERE id=101"); return result;
        });
        assertThrows(BusinessException.class, () -> service.uploadPicture(file(), uploadRequest(), user(2)));
        assertUsage(2, 200); assertEquals(5L, service.count()); assertEquals(1, taskCount());
    }

    @Test
    void publicPictureCannotBeMovedIntoPrivateSpaceByReplacement() {
        PictureUploadRequest request = uploadRequest(); request.setId(10L);
        assertThrows(BusinessException.class, () -> service.uploadPicture(file(), request, user(2)));
        verifyNoInteractions(app.getBean(FileManager.class));
    }

    @Test
    void privateUploadFailsBeforeIoWhenStorageDisabledOrBucketPublic() {
        PrivatePictureStorageProperties props = app.getBean(PrivatePictureStorageProperties.class);
        props.setEnabled(false);
        assertThrows(BusinessException.class, () -> service.uploadPicture(file(), uploadRequest(), user(2)));
        props.setEnabled(true); props.setBucket("public-test-1250000000");
        assertThrows(BusinessException.class, () -> service.uploadPicture(file(), uploadRequest(), user(2)));
        verifyNoInteractions(app.getBean(FileManager.class));
    }

    @Test
    void colorSearchRequiresOwnerEvenForAdminAndNeverUsesPublicCache() throws Exception {
        for (long id : new long[]{1, 3}) {
            mvc.perform(post("/picture/search/color").session(session(id)).contentType("application/json")
                    .content("{\"spaceId\":101,\"picColor\":\"#ff0000\"}"))
                    .andExpect(jsonPath("$.code").value(40101));
        }
        mvc.perform(post("/picture/search/color").contentType("application/json")
                .content("{\"spaceId\":101,\"picColor\":\"#ff0000\"}"))
                .andExpect(jsonPath("$.code").value(40100));
        verifyNoInteractions(cache, cos);
    }

    @Test
    void colorSearchSortsDistanceAndIdSkipsInvalidAndReturnsOnlyPrivateProxyUrls() throws Exception {
        jdbc.update("UPDATE picture SET picColor='#ff0000' WHERE id IN(10,13)");
        jdbc.update("UPDATE picture SET picColor='#00ff00' WHERE id=11");
        jdbc.update("UPDATE picture SET picColor='#ff0000' WHERE id=12");
        seed(15, 101L, 2, 1, 1); jdbc.update("UPDATE picture SET picColor='bad' WHERE id=15");
        seed(16, 101L, 2, 1, 1); jdbc.update("UPDATE picture SET picColor='#ff0000' WHERE id=16");
        seed(17, 101L, 2, 1, 1); jdbc.update("UPDATE picture SET picColor='#ff0000',isDelete=1 WHERE id=17");
        mvc.perform(post("/api/picture/search/color").contextPath("/api").session(session(2))
                .contentType("application/json").content("{\"spaceId\":101,\"picColor\":\"#ff0000\"}"))
                .andExpect(jsonPath("$.code").value(0)).andExpect(jsonPath("$.data.length()").value(3))
                .andExpect(jsonPath("$.data[0].id").value("12"))
                .andExpect(jsonPath("$.data[1].id").value("16"))
                .andExpect(jsonPath("$.data[2].id").value("11"))
                .andExpect(jsonPath("$.data[0].url").value("/api/picture/private/file?id=12&variant=display"))
                .andExpect(jsonPath("$.data[0].originalUrl").doesNotExist())
                .andExpect(header().string("Cache-Control", "private, no-store"));
        verifyNoInteractions(cache, cos);
    }

    @Test
    void colorSearchUsesAllBatchesButReturnsOnlyTwelve() throws Exception {
        for (int i = 100; i < 605; i++) {
            seed(i, 101L, 2, 1, 1);
            jdbc.update("UPDATE picture SET picColor=? WHERE id=?", i == 604 ? "#ff0000" : "#000000", i);
        }
        mvc.perform(post("/picture/search/color").session(session(2)).contentType("application/json")
                .content("{\"spaceId\":101,\"picColor\":\"#ff0000\"}"))
                .andExpect(jsonPath("$.data.length()").value(12)).andExpect(jsonPath("$.data[0].id").value("604"));
    }

    @Test
    void colorSearchValidatesInputAndHasSuccessfulEmptyResult() throws Exception {
        for (String color : new String[]{"red", "#abc", "0xff0000", "#gg0000", "#12345678"}) {
            mvc.perform(post("/picture/search/color").session(session(2)).contentType("application/json")
                    .content("{\"spaceId\":101,\"picColor\":\"" + color + "\"}"))
                    .andExpect(jsonPath("$.code").value(40000));
        }
        mvc.perform(post("/picture/search/color").session(session(2)).contentType("application/json")
                .content("{\"spaceId\":101,\"picColor\":\"#ff0000\"}"))
                .andExpect(jsonPath("$.code").value(0)).andExpect(jsonPath("$.data.length()").value(0));
    }

    private PictureEditByBatchRequest batch(Long... ids) {
        PictureEditByBatchRequest body = new PictureEditByBatchRequest();
        body.setSpaceId(101L); body.setPictureIdList(Arrays.asList(ids)); body.setNameRule("照片_{序号}"); return body;
    }
    private learn.gjx.tuku1.service.PictureExtensionService extensions() {
        return app.getBean(learn.gjx.tuku1.service.PictureExtensionService.class);
    }

    @Test
    void batchRenamesInRequestOrderAndDoesNotChangeQuotaFilesOrDraftState() {
        PictureEditByBatchRequest body = batch(12L, 11L);
        body.setCategory("旅行"); body.setTags(Arrays.asList("海边", " 海边 ", "假期"));
        assertEquals(2, extensions().editBatch(body, user(2)));
        assertEquals("照片_1", service.getById(12).getName());
        assertEquals("照片_2", service.getById(11).getName());
        assertEquals("旅行", service.getById(11).getCategory());
        assertEquals("[\"海边\",\"假期\"]", service.getById(11).getTags());
        assertEquals(0, service.getById(12).getUploadStatus());
        assertUsage(2, 200); assertEquals(0, taskCount()); verifyNoInteractions(cache, cos);
    }

    @Test
    void batchCanExplicitlyClearMetadataWithoutChangingNames() {
        jdbc.update("UPDATE picture SET category='old',tags='[\"old\"]' WHERE id=11");
        PictureEditByBatchRequest body = batch(11L); body.setNameRule(null);
        body.setCategory(""); body.setTags(Collections.emptyList());
        assertEquals(1, extensions().editBatch(body, user(2)));
        assertEquals("", service.getById(11).getCategory()); assertEquals("[]", service.getById(11).getTags());
        assertEquals("image", service.getById(11).getName());
    }

    @Test
    void batchRejectsCrossSpacePublicDeletedMissingAndUnauthorizedRowsWithoutPartialWrites() {
        for (Long invalid : Arrays.asList(10L, 13L, 9999L)) {
            assertThrows(BusinessException.class, () -> extensions().editBatch(batch(11L, invalid), user(2)));
            assertEquals("image", service.getById(11).getName());
        }
        jdbc.update("UPDATE picture SET isDelete=1 WHERE id=12");
        assertThrows(BusinessException.class, () -> extensions().editBatch(batch(11L, 12L), user(2)));
        for (long id : new long[]{1, 3}) {
            assertThrows(BusinessException.class, () -> extensions().editBatch(batch(11L), user(id)));
        }
        assertThrows(BusinessException.class, () -> extensions().editBatch(batch(11L), null));
        assertEquals("image", service.getById(11).getName()); assertUsage(2, 200);
    }

    @Test
    void batchRollsBackFirstUpdateWhenSecondDatabaseWriteFails() {
        jdbc.execute(mysql()
                ? "CREATE TRIGGER reject_batch_name BEFORE UPDATE ON picture FOR EACH ROW BEGIN IF NEW.name = '照片_2' THEN SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT='test batch failure'; END IF; END"
                : "ALTER TABLE picture ADD CONSTRAINT reject_batch_name CHECK(name <> '照片_2')");
        try {
            assertThrows(RuntimeException.class, () -> extensions().editBatch(batch(11L, 12L), user(2)));
            assertEquals("image", service.getById(11).getName());
            assertEquals("image", service.getById(12).getName()); assertUsage(2, 200);
        } finally { jdbc.execute(mysql() ? "DROP TRIGGER reject_batch_name" : "ALTER TABLE picture DROP CONSTRAINT reject_batch_name"); }
    }

    @Test
    void batchRejectsDuplicateIdsOversizedInputsEmptyChangesAndExpressions() {
        assertThrows(BusinessException.class, () -> extensions().editBatch(batch(11L, 11L), user(2)));
        assertThrows(BusinessException.class, () -> extensions().editBatch(batch(), user(2)));
        assertThrows(BusinessException.class, () -> extensions().editBatch(batch(-1L), user(2)));
        PictureEditByBatchRequest body = batch(11L);
        body.setPictureIdList(Collections.nCopies(101, 11L));
        assertThrows(BusinessException.class, () -> extensions().editBatch(body, user(2)));
        body.setPictureIdList(Collections.singletonList(11L)); body.setNameRule(null);
        assertThrows(BusinessException.class, () -> extensions().editBatch(body, user(2)));
        for (String rule : new String[]{" ", "{unknown}", "#{T(java.lang.Runtime)}", "a\nb", String.join("", Collections.nCopies(129, "a"))}) {
            body.setNameRule(rule);
            assertThrows(BusinessException.class, () -> extensions().editBatch(body, user(2)));
        }
        body.setNameRule(null); body.setTags(Arrays.asList((String) null));
        assertThrows(BusinessException.class, () -> extensions().editBatch(body, user(2)));
    }

    @Test
    void batchEndpointBindsLongIdsAndReturnsAffectedCount() throws Exception {
        seed(9007199254740997L, 101L, 2, 1, 1);
        mvc.perform(post("/picture/edit/batch").session(session(2)).contentType("application/json")
                .content("{\"spaceId\":\"101\",\"pictureIdList\":[\"9007199254740997\"],\"nameRule\":\"长ID_{序号}\"}"))
                .andExpect(jsonPath("$.code").value(0)).andExpect(jsonPath("$.data").value(1))
                .andExpect(header().string("Cache-Control", "private, no-store"));
        assertEquals("长ID_1", service.getById(9007199254740997L).getName());
    }

    @Test
    void privateReplacementClearsStaleColorWhenNewUploadHasNoColor() {
        jdbc.update("UPDATE picture SET picColor='#ff0000' WHERE id=11");
        stubUpload(100);
        PictureUploadRequest body = uploadRequest(); body.setId(11L);
        service.uploadPicture(file(), body, user(2));
        assertNull(service.getById(11).getPicColor());
    }

    @Test
    void publicUploadStoresColorAndReplacementClearsStaleColor() {
        PictureUploadResult result = new PictureUploadResult();
        result.setUrl("https://public-test.example.com/public/2/new.webp");
        result.setPicName("new image"); result.setPicColor("#123456");
        when(app.getBean(FileManager.class).uploadPicture(any(), eq("public/2"))).thenReturn(result);
        PictureVO created = service.uploadPicture(file(), new PictureUploadRequest(), user(2));
        assertEquals("#123456", created.getPicColor());
        assertEquals("#123456", service.getById(created.getId()).getPicColor());
        result.setPicColor(null);
        PictureUploadRequest replacement = new PictureUploadRequest(); replacement.setId(created.getId());
        PictureVO updated = service.uploadPicture(file(), replacement, user(2));
        assertNull(updated.getPicColor());
        assertNull(service.getById(created.getId()).getPicColor());
    }

    private SpaceUserService members() { return app.getBean(SpaceUserService.class); }
    private void team() {
        for (int id = 1; id <= 6; id++) jdbc.update("INSERT INTO user(id,userName,userAccount,userPassword,userRole) VALUES(?,?,?,?,?)",
                id, "user" + id, "account" + id, "not-a-real-password", id == 1 ? 1 : 0);
        jdbc.update("UPDATE space SET spaceType=1 WHERE id=101");
        jdbc.update("INSERT INTO space_user(id,spaceId,userId,spaceRole) VALUES(201,101,2,'admin'),(202,101,3,'viewer'),(203,101,4,'editor'),(204,101,5,'admin')");
    }
    private SpaceUserQueryRequest memberQuery() {
        SpaceUserQueryRequest r = new SpaceUserQueryRequest(); r.setSpaceId(101L); return r;
    }
    private SpaceUserEditRequest memberEdit(long id, String role) {
        SpaceUserEditRequest r = new SpaceUserEditRequest(); r.setSpaceId(101L); r.setId(id); r.setSpaceRole(role); return r;
    }

    @Test void teamViewerReadsDraftsAndProxyButCannotWrite() throws Exception {
        team();
        mvc.perform(post("/picture/list/page/vo").session(session(3)).contentType("application/json").content("{\"spaceId\":101}"))
                .andExpect(jsonPath("$.code").value(0)).andExpect(jsonPath("$.data.total").value("2"))
                .andExpect(jsonPath("$.data.records[0].permissionList[0]").value("picture:view"));
        mvc.perform(get("/picture/get/vo").session(session(3)).param("id", "11"))
                .andExpect(jsonPath("$.code").value(0)).andExpect(jsonPath("$.data.originalUrl").doesNotExist());
        assertThrows(BusinessException.class, () -> service.uploadPicture(file(), uploadRequest(), user(3)));
        assertThrows(BusinessException.class, () -> service.deletePicture(11L, user(3)));
        assertThrows(BusinessException.class, () -> extensions().editBatch(batch(11L), user(3)));
        assertThrows(BusinessException.class, () -> members().list(memberQuery(), user(3)));
        verifyNoInteractions(cache, cos, app.getBean(FileManager.class));
        assertUsage(2, 200);
    }

    @Test void editorCanUploadReplaceBatchEditAndDeleteButNotManageMembers() {
        team(); stubUpload(300);
        PictureVO uploaded = service.uploadPicture(file(), uploadRequest(), user(4));
        assertTrue(uploaded.getPermissionList().contains(SpacePermission.EDIT));
        assertFalse(uploaded.getPermissionList().contains(SpacePermission.MEMBERS));
        assertEquals(4L, uploaded.getUserId()); assertUsage(3, 500);
        PictureUploadRequest replace = uploadRequest(); replace.setId(11L);
        PictureVO changed = service.uploadPicture(file(), replace, user(4));
        assertEquals(3L, changed.getUserId()); assertUsage(3, 700);
        assertEquals(1, extensions().editBatch(batch(11L), user(4)));
        assertTrue(service.deletePicture(11L, user(4))); assertUsage(2, 400);
        assertThrows(BusinessException.class, () -> members().list(memberQuery(), user(4)));
        verify(app.getBean(FileManager.class), never()).uploadPicture(any(), anyString());
        verifyNoInteractions(cache);
    }

    @Test void memberCrudRejectsDuplicatesForeignIdsAndProtectsOwner() {
        team();
        SpaceUserAddRequest add = new SpaceUserAddRequest(); add.setSpaceId(101L); add.setUserId(6L);
        long id = members().add(add, user(5));
        assertThrows(BusinessException.class, () -> members().add(add, user(2)));
        assertEquals(5, members().list(memberQuery(), user(2)).size());
        assertTrue(members().edit(memberEdit(id, "editor"), user(2)));
        SpaceUserEditRequest forged = memberEdit(id, "admin"); forged.setSpaceId(102L);
        assertThrows(BusinessException.class, () -> members().edit(forged, user(3)));
        assertThrows(BusinessException.class, () -> members().remove(memberEdit(201, null), user(5)));
        assertThrows(BusinessException.class, () -> members().edit(memberEdit(201, "viewer"), user(5)));
        assertTrue(members().remove(memberEdit(id, null), user(5)));
        assertNotEquals(id, members().add(add, user(2)));
    }

    @Test void invalidRolesAndDeletedOrUnknownUsersAreRejected() {
        team(); SpaceUserAddRequest add = new SpaceUserAddRequest(); add.setSpaceId(101L); add.setUserId(6L);
        for (String role : Arrays.asList(null, "", "owner", "ADMIN", "admin OR 1=1")) {
            add.setSpaceRole(role); assertThrows(BusinessException.class, () -> members().add(add, user(2)));
        }
        add.setSpaceRole("viewer"); add.setUserId(999L);
        assertThrows(BusinessException.class, () -> members().add(add, user(2)));
        add.setUserId(6L); jdbc.update("UPDATE user SET isDelete=1 WHERE id=6");
        assertThrows(BusinessException.class, () -> members().add(add, user(2)));
        assertEquals(4, members().list(memberQuery(), user(2)).size());
    }

    @Test void platformAdminAndNonmembersHaveNoTeamContentOrMembershipAccess() throws Exception {
        team();
        for (long id : new long[]{1, 6}) {
            mvc.perform(get("/picture/get/vo").session(session(id)).param("id", "11")).andExpect(jsonPath("$.code").value(40101));
            assertThrows(BusinessException.class, () -> members().list(memberQuery(), user(id)));
            assertThrows(BusinessException.class, () -> service.deletePicture(11L, user(id)));
        }
        // A guessed permission list/userId in the JSON is not an authorization source.
        mvc.perform(post("/picture/list/page/vo").session(session(6)).contentType("application/json")
                .content("{\"spaceId\":101,\"userId\":2,\"permissionList\":[\"picture:view\"]}"))
                .andExpect(jsonPath("$.code").value(40101));
    }

    @Test void removalRevokesExistingSessionAndAllFileVariantsWithoutCosRequest() throws Exception {
        team(); MockHttpSession loggedIn = session(3);
        mvc.perform(get("/picture/get/vo").session(loggedIn).param("id", "11")).andExpect(jsonPath("$.code").value(0));
        members().remove(memberEdit(202, null), user(2));
        mvc.perform(get("/picture/get/vo").session(loggedIn).param("id", "11")).andExpect(jsonPath("$.code").value(40101));
        for (String variant : Arrays.asList("original", "display", "thumbnail"))
            mvc.perform(get("/picture/private/file").session(loggedIn).param("id", "11").param("variant", variant))
                    .andExpect(jsonPath("$.code").value(40101));
        verifyNoInteractions(cos);
    }

    @Test void roleDowngradePreservesReadAndImmediatelyRevokesWrite() throws Exception {
        team(); members().edit(memberEdit(203, "viewer"), user(2));
        mvc.perform(get("/picture/get/vo").session(session(4)).param("id", "11"))
                .andExpect(jsonPath("$.code").value(0)).andExpect(jsonPath("$.data.permissionList.length()").value(1));
        assertThrows(BusinessException.class, () -> service.deletePicture(11L, user(4)));
        assertThrows(BusinessException.class, () -> extensions().editBatch(batch(11L), user(4)));
    }

    @Test void myTeamsUsesSessionIdentityAndDeletedSpacesDisappear() {
        team(); SpaceUserQueryRequest r = memberQuery(); r.setUserId(2L);
        assertEquals(0, members().myTeams(r, user(6)).getTotal());
        assertEquals(1, members().myTeams(r, user(3)).getTotal());
        assertEquals(1, members().myTeams(r, user(3)).getRecords().get(0).getSpace().getPermissionList().size());
        r.setPageSize(51); assertThrows(BusinessException.class, () -> members().myTeams(r, user(3)));
        r.setPageSize(20); jdbc.update("UPDATE space SET isDelete=1 WHERE id=101");
        assertEquals(0, members().myTeams(r, user(3)).getTotal());
    }

    @Test void teamImageNeverEntersPublicListOrCacheAndDisabledStorageFailsClosed() throws Exception {
        team(); publicListsAndTotalsExcludeAllPrivateRows(); clearInvocations(cache);
        app.getBean(PrivatePictureStorageProperties.class).setEnabled(false);
        assertThrows(BusinessException.class, () -> service.uploadPicture(file(), uploadRequest(), user(4)));
        assertUsage(2, 200);
        verifyNoInteractions(cache, cos, app.getBean(FileManager.class));
    }

    @Test void teamAdminCanAnalyzeButEditorViewerAndPlatformAdminCannot() {
        team(); learn.gjx.tuku1.model.dto.space.analyze.SpaceAnalyzeRequest r = new learn.gjx.tuku1.model.dto.space.analyze.SpaceAnalyzeRequest();
        r.setScope("SPACE"); r.setSpaceId(101L);
        learn.gjx.tuku1.service.SpaceAnalyzeService analysis = app.getBean(learn.gjx.tuku1.service.SpaceAnalyzeService.class);
        assertEquals(200L, analysis.usage(r, user(5)).getUsedSize());
        for (long id : new long[]{1, 3, 4, 6}) assertThrows(BusinessException.class, () -> analysis.usage(r, user(id)));
    }

    @Test void writeRechecksCurrentRoleAfterOldRepeatableReadSnapshot() throws Exception {
        Assumptions.assumeTrue(mysql(), "Requires MySQL current-read semantics; H2 aborts a changed RR row");
        team(); ExecutorService executor = Executors.newSingleThreadExecutor();
        CountDownLatch checked = new CountDownLatch(1), resume = new CountDownLatch(1);
        try {
            Future<Boolean> result = executor.submit(() -> {
                org.springframework.transaction.support.TransactionTemplate tx = new org.springframework.transaction.support.TransactionTemplate(
                        app.getBean(org.springframework.transaction.PlatformTransactionManager.class));
                tx.setIsolationLevel(org.springframework.transaction.TransactionDefinition.ISOLATION_REPEATABLE_READ);
                try {
                    tx.execute(status -> {
                        app.getBean(PictureAccessManager.class).requireSpacePermission(101L, user(4), SpacePermission.EDIT);
                        Picture old = service.getById(11L); checked.countDown();
                        try { assertTrue(resume.await(15, TimeUnit.SECONDS)); } catch (InterruptedException e) { throw new RuntimeException(e); }
                        app.getBean(PrivatePictureWriteManager.class).lockForEdit(old, user(4));
                        return null;
                    });
                    return false;
                } catch (BusinessException e) { return e.getCode() == 40101; }
            });
            assertTrue(checked.await(15, TimeUnit.SECONDS));
            members().edit(memberEdit(203, "viewer"), user(2)); resume.countDown();
            assertTrue(result.get(15, TimeUnit.SECONDS)); assertUsage(2, 200);
        } finally { resume.countDown(); executor.shutdownNow(); }
    }

    @Test void memberEndpointsRequireLoginAndReturnNoStoreSafeVos() throws Exception {
        team();
        for (String path : Arrays.asList("list", "get", "add", "edit", "delete", "my/list/page"))
            mvc.perform(post("/spaceUser/" + path).contentType("application/json").content("{\"spaceId\":101}"))
                    .andExpect(jsonPath("$.code").value(40100));
        mvc.perform(post("/spaceUser/list").session(session(2)).contentType("application/json").content("{\"spaceId\":101}"))
                .andExpect(jsonPath("$.code").value(0)).andExpect(jsonPath("$.data[0].id").isString())
                .andExpect(jsonPath("$.data[0].userPassword").doesNotExist())
                .andExpect(header().string("Cache-Control", "no-store")).andExpect(header().string("Vary", "Cookie"));
    }

    @Test void viewerFileVariantsStreamThroughAuthorizationWithoutCosRedirect() throws Exception {
        team();
        when(cos.getObject(any(GetObjectRequest.class))).thenAnswer(i -> {
            COSObject object = new COSObject();
            object.setObjectContent(new com.qcloud.cos.model.COSObjectInputStream(
                    new ByteArrayInputStream(new byte[]{1, 2, 3}), new org.apache.http.client.methods.HttpGet()));
            return object;
        });
        for (String variant : Arrays.asList("original", "display", "thumbnail"))
            mvc.perform(get("/picture/private/file").session(session(3)).param("id", "11").param("variant", variant))
                    .andExpect(content().bytes(new byte[]{1, 2, 3}))
                    .andExpect(header().string("Cache-Control", "private, no-store"))
                    .andExpect(header().doesNotExist("Location"));
    }

    @Test void revocationDuringCloudUploadPreventsCommitAndSchedulesCompensation() throws Exception {
        team(); CountDownLatch uploaded = new CountDownLatch(1), resume = new CountDownLatch(1);
        when(app.getBean(FileManager.class).uploadPrivatePicture(any(), eq(101L))).thenAnswer(i -> {
            PictureUploadResult result = uploaded(300); uploaded.countDown();
            assertTrue(resume.await(15, TimeUnit.SECONDS)); return result;
        });
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try {
            Future<PictureVO> result = executor.submit(() -> service.uploadPicture(file(), uploadRequest(), user(4)));
            assertTrue(uploaded.await(15, TimeUnit.SECONDS));
            members().remove(memberEdit(203, null), user(2)); resume.countDown();
            ExecutionException error = assertThrows(ExecutionException.class, () -> result.get(15, TimeUnit.SECONDS));
            assertInstanceOf(BusinessException.class, error.getCause());
            assertEquals(40101, ((BusinessException) error.getCause()).getCode());
            assertUsage(2, 200); assertEquals(1, taskCount());
        } finally { resume.countDown(); executor.shutdownNow(); }
    }

    @TestConfiguration(proxyBeanMethods = false)
    @EnableAutoConfiguration
    @EnableAspectJAutoProxy
    @Import({MybatisPlusConfig.class, JsonConfig.class, PictureController.class, PictureServiceImpl.class,
            SpaceUserService.class, learn.gjx.tuku1.controller.SpaceUserController.class,
            learn.gjx.tuku1.service.SpaceAnalyzeService.class, learn.gjx.tuku1.manager.space.SpaceAnalysisRepository.class,
            learn.gjx.tuku1.controller.PictureExtensionController.class, learn.gjx.tuku1.service.PictureExtensionService.class,
            PictureAccessManager.class, PrivatePictureFileManager.class, PrivatePictureStorageProperties.class,
            PrivatePictureWriteManager.class, PrivatePictureCleanupManager.class,
            AiTaskStore.class, AiEditingProperties.class,
            CollaborationService.class, CollaborationQueue.class, CollaborationWriteGuard.class,
            AuthInterceptor.class, GlobalExceptionHandler.class})
    static class TestApplication {
        @Bean CollaborationAccess collaborationAccess() { return mock(CollaborationAccess.class); }
        @Bean UserService users() { return mock(UserService.class); }
        @Bean COSClient cos() { return mock(COSClient.class); }
        @Bean
        static org.springframework.beans.factory.config.BeanFactoryPostProcessor collaborators() {
            return factory -> {
                factory.registerSingleton("pictureFavoriteService", mock(learn.gjx.tuku1.service.PictureFavoriteService.class));
                factory.registerSingleton("fileManager", mock(FileManager.class));
                factory.registerSingleton("pictureFileCleanupManager", mock(PictureFileCleanupManager.class));
                factory.registerSingleton("bingImageSearchManager", mock(BingImageSearchManager.class));
                factory.registerSingleton("publicPicturePageCache", mock(PublicPicturePageCache.class));
                factory.registerSingleton("pictureListRevision", mock(PictureListRevision.class));
            };
        }
        @Bean CosClientConfig cosConfig() {
            CosClientConfig config = mock(CosClientConfig.class);
            when(config.getRegion()).thenReturn("ap-test");
            when(config.getBucket()).thenReturn("public-test-1250000000");
            return config;
        }
    }
}
