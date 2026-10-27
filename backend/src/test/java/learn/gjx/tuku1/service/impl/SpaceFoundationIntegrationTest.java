package learn.gjx.tuku1.service.impl;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import learn.gjx.tuku1.aop.AuthInterceptor;
import learn.gjx.tuku1.config.JsonConfig;
import learn.gjx.tuku1.config.MybatisPlusConfig;
import learn.gjx.tuku1.controller.SpaceController;
import learn.gjx.tuku1.exception.BusinessException;
import learn.gjx.tuku1.exception.ErrorCode;
import learn.gjx.tuku1.exception.GlobalExceptionHandler;
import learn.gjx.tuku1.manager.space.UserSpaceCreationLock;
import learn.gjx.tuku1.model.dto.space.*;
import learn.gjx.tuku1.model.entity.Space;
import learn.gjx.tuku1.model.entity.User;
import learn.gjx.tuku1.model.enums.SpaceLevelEnum;
import learn.gjx.tuku1.service.SpaceService;
import learn.gjx.tuku1.service.UserService;
import org.junit.jupiter.api.*;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.EnableAspectJAutoProxy;
import org.springframework.context.annotation.Import;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.context.WebApplicationContext;

import javax.servlet.http.HttpServletRequest;
import javax.sql.DataSource;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Real SQL/transactions and MVC+auth aspect. User identity is a test-only mock; never uses business data. */
class SpaceFoundationIntegrationTest {
    private static final String MYSQL_URL = System.getenv("MYSQL_TEST_SERVER_URL");
    private static ConfigurableApplicationContext app;
    private static String schema;
    private static JdbcTemplate jdbc;
    private SpaceService service;
    private MockMvc mvc;
    private final User owner = user(2L, 0);
    private final User admin = user(1L, 1);
    private final User other = user(3L, 0);

    private static boolean mysql() {
        return MYSQL_URL != null && !MYSQL_URL.trim().isEmpty();
    }

    private static Connection adminConnection() throws Exception {
        return DriverManager.getConnection(MYSQL_URL,
                System.getenv().getOrDefault("MYSQL_TEST_USERNAME", "root"),
                System.getenv().getOrDefault("MYSQL_TEST_PASSWORD", ""));
    }

    @BeforeAll
    static void start() throws Exception {
        String url = "jdbc:h2:mem:space_" + UUID.randomUUID()
                + ";MODE=MySQL;NON_KEYWORDS=USER;DB_CLOSE_DELAY=-1";
        try {
            if (mysql()) {
                java.net.URI endpoint = java.net.URI.create(MYSQL_URL.substring("jdbc:".length()));
                if (!"mysql".equals(endpoint.getScheme())
                        || !Arrays.asList("localhost", "127.0.0.1").contains(endpoint.getHost())
                        || !"/".equals(endpoint.getPath())) {
                    throw new IllegalArgumentException("Test MySQL URL must be local and server-only");
                }
                schema = "tuku_space_test_" + UUID.randomUUID().toString().replace("-", "");
                try (Connection connection = adminConnection(); Statement statement = connection.createStatement()) {
                    statement.execute("CREATE DATABASE `" + schema
                            + "` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci");
                }
                int q = MYSQL_URL.indexOf('?');
                url = q < 0 ? MYSQL_URL + schema
                        : MYSQL_URL.substring(0, q) + schema + MYSQL_URL.substring(q);
            }
            app = new SpringApplicationBuilder(TestApplication.class).web(WebApplicationType.SERVLET)
                    .run("--spring.config.location=optional:classpath:space-foundation-empty.yml",
                            "--server.port=0", "--server.address=127.0.0.1", "--spring.session.store-type=none",
                            "--spring.datasource.url=" + url,
                            "--spring.datasource.driver-class-name=" + (mysql() ? "com.mysql.cj.jdbc.Driver" : "org.h2.Driver"),
                            "--spring.datasource.username=" + (mysql()
                                    ? System.getenv().getOrDefault("MYSQL_TEST_USERNAME", "root") : "sa"),
                            "--spring.datasource.password=" + (mysql()
                                    ? System.getenv().getOrDefault("MYSQL_TEST_PASSWORD", "") : ""),
                            "--mybatis-plus.configuration.map-underscore-to-camel-case=false",
                            "--mybatis-plus.global-config.db-config.logic-delete-field=isDelete",
                            "--spring.main.banner-mode=off", "--logging.level.root=WARN");
            jdbc = app.getBean(JdbcTemplate.class);
            script("sql/create_sql.sql");
        } catch (Exception error) {
            stop();
            throw error;
        }
    }

    private static void script(String path) throws Exception {
        String ddl = new String(Files.readAllBytes(Paths.get(path)), StandardCharsets.UTF_8)
                .replaceFirst("(?i)use\\s+tuku\\s*;", "");
        if (!mysql()) {
            ddl = ddl.replaceAll("(?i)\\s+collate\\s*=?\\s*utf8mb4_unicode_ci", "")
                    .replaceAll("(?i)\\s+comment\\s*=?\\s*'[^']*'", "")
                    .replaceAll("(?i)NOT NULL\\s+ON UPDATE CURRENT_TIMESTAMP",
                            "ON UPDATE CURRENT_TIMESTAMP NOT NULL")
                    .replace("ENGINE=InnoDB", "").replace("DEFAULT CHARSET=utf8mb4", "");
        }
        ResourceDatabasePopulator populator = new ResourceDatabasePopulator();
        populator.setSqlScriptEncoding("UTF-8");
        populator.addScript(new ByteArrayResource(ddl.getBytes(StandardCharsets.UTF_8)));
        populator.execute(app.getBean(DataSource.class));
    }

    @AfterAll
    static void stop() throws Exception {
        if (app != null) {
            app.close();
            app = null;
        }
        if (schema != null) {
            try (Connection connection = adminConnection(); Statement statement = connection.createStatement()) {
                statement.execute("DROP DATABASE `" + schema + "`");
            }
            schema = null;
        }
    }

    @BeforeEach
    void reset() {
        app.getBean(ProbedTransactionTemplate.class).resetProbe();
        service = app.getBean(SpaceService.class);
        mvc = MockMvcBuilders.webAppContextSetup((WebApplicationContext) app).build();
        jdbc.update("DELETE FROM picture");
        jdbc.update("DELETE FROM space_user");
        jdbc.update("DELETE FROM space");
        seed(101, 2);
        seed(102, 3);
        UserService users = app.getBean(UserService.class);
        org.mockito.Mockito.reset(users);
        when(users.getLoginUser(any())).thenAnswer(invocation -> {
            HttpServletRequest request = invocation.getArgument(0);
            String identity = request.getHeader("X-Test-User");
            if ("owner".equals(identity)) {
                return owner;
            }
            if ("admin".equals(identity)) {
                return admin;
            }
            if ("other".equals(identity)) {
                return other;
            }
            throw new BusinessException(ErrorCode.NOT_LOGIN_ERROR);
        });
    }

    private void seed(long id, long userId) {
        jdbc.update("INSERT INTO space(id,spaceName,userId) VALUES(?,?,?)", id, "空间" + id, userId);
    }

    @Test
    void levelDefaultsAndPublicHttpContract() throws Exception {
        assertNull(SpaceLevelEnum.getEnumByValue(null));
        assertNull(SpaceLevelEnum.getEnumByValue(99));
        assertEquals(100L * 1024 * 1024, service.getById(101).getMaxSize());
        mvc.perform(get("/space/list/level"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.length()").value(3))
                .andExpect(jsonPath("$.data[0].maxSize").value("104857600"))
                .andExpect(jsonPath("$.data[2].maxCount").value("10000"));
    }

    @Test
    void adminEndpointsRequireLoginAndAdminRole() throws Exception {
        mvc.perform(post("/space/list/page").contentType("application/json").content("{}"))
                .andExpect(jsonPath("$.code").value(40100));
        mvc.perform(post("/space/list/page").header("X-Test-User", "owner")
                        .contentType("application/json").content("{}"))
                .andExpect(jsonPath("$.code").value(40101));
        mvc.perform(post("/space/list/page").header("X-Test-User", "admin")
                        .contentType("application/json").content("{}"))
                .andExpect(jsonPath("$.data.total").value("2"));
        mvc.perform(get("/space/get?id=101").header("X-Test-User", "other"))
                .andExpect(jsonPath("$.code").value(40101));
    }

    @Test
    void metadataReadIsLimitedToOwnerOrAdmin() throws Exception {
        mvc.perform(get("/space/get/vo?id=101").header("X-Test-User", "other"))
                .andExpect(jsonPath("$.code").value(40101));
        mvc.perform(get("/space/get/vo?id=101").header("X-Test-User", "owner"))
                .andExpect(jsonPath("$.data.id").value("101"))
                .andExpect(jsonPath("$.data.isDelete").doesNotExist());
        assertEquals(101L, service.getSpaceVO(101L, admin).getId());
        assertThrows(BusinessException.class, () -> service.getSpaceVO(101L, null));
    }

    @Test
    void myListCannotBeRedirectedToAnotherUser() throws Exception {
        mvc.perform(post("/space/my/list/page/vo").header("X-Test-User", "owner")
                        .contentType("application/json").content("{\"userId\":3}"))
                .andExpect(jsonPath("$.data.total").value("1"))
                .andExpect(jsonPath("$.data.records[0].id").value("101"));
        assertEquals(0, service.listMySpaces(null, admin).getTotal());
    }

    @Test
    void paginationFiltersAndStableSortingWork() {
        seed(103, 2);
        SpaceQueryRequest query = new SpaceQueryRequest();
        query.setPageSize(1);
        query.setUserId(2L);
        query.setSortField("id");
        query.setSortOrder("asc");
        Page<Space> page = service.listSpaces(query, admin);
        assertEquals(2, page.getTotal());
        assertEquals(101L, page.getRecords().get(0).getId());
        query.setCurrent(2);
        assertEquals(103L, service.listSpaces(query, admin).getRecords().get(0).getId());
        query.setSpaceName("missing");
        assertEquals(0, service.listSpaces(query, admin).getTotal());
    }

    @Test
    void invalidPaginationAndSqlSortInjectionAreRejected() {
        SpaceQueryRequest query = new SpaceQueryRequest();
        query.setPageSize(51);
        assertThrows(BusinessException.class, () -> service.listSpaces(query, admin));
        query.setPageSize(10);
        query.setCurrent(0);
        assertThrows(BusinessException.class, () -> service.listSpaces(query, admin));
        query.setCurrent(1);
        query.setSortField("id; DROP TABLE space");
        assertThrows(BusinessException.class, () -> service.listSpaces(query, admin));
        query.setSortField("id");
        query.setSortOrder("DESC; DELETE");
        assertThrows(BusinessException.class, () -> service.listSpaces(query, admin));
        query.setSortOrder("asc");
        query.setSpaceLevel(99);
        assertThrows(BusinessException.class, () -> service.listSpaces(query, admin));
        assertEquals(2, service.count());
    }

    @Test
    void editIgnoresPrivilegedFieldsFromClient() throws Exception {
        mvc.perform(post("/space/edit").header("X-Test-User", "owner")
                        .contentType("application/json")
                        .content("{\"id\":101,\"spaceName\":\"  我的相册  \",\"spaceLevel\":2,"
                                + "\"maxSize\":9999999999,\"userId\":3,\"totalCount\":99,\"isDelete\":1}"))
                .andExpect(jsonPath("$.code").value(0));
        Space saved = service.getById(101);
        assertEquals("我的相册", saved.getSpaceName());
        assertEquals(0, saved.getSpaceLevel());
        assertEquals(104857600L, saved.getMaxSize());
        assertEquals(2L, saved.getUserId());
        assertEquals(0L, saved.getTotalCount());
        assertEquals(0, saved.getIsDelete());
    }

    @Test
    void invalidNamesAndForeignEditsAreRejected() {
        SpaceEditRequest edit = new SpaceEditRequest();
        edit.setId(101L);
        edit.setSpaceName("  ");
        assertThrows(BusinessException.class, () -> service.editSpace(edit, owner));
        edit.setSpaceName(String.join("", Collections.nCopies(31, "a")));
        assertThrows(BusinessException.class, () -> service.editSpace(edit, owner));
        edit.setSpaceName("test");
        assertThrows(BusinessException.class, () -> service.editSpace(edit, other));
        assertThrows(BusinessException.class, () -> service.editSpace(edit, admin));
        edit.setId(-1L);
        assertThrows(BusinessException.class, () -> service.editSpace(edit, owner));
    }

    @Test
    void adminLevelChangeAppliesDefaultsAndPreservesUsage() {
        jdbc.update("UPDATE space SET totalSize=1000,totalCount=2 WHERE id=101");
        SpaceUpdateRequest update = new SpaceUpdateRequest();
        update.setId(101L);
        update.setSpaceLevel(1);
        service.updateSpace(update, admin);
        Space saved = service.getById(101);
        assertEquals(1000L * 1024 * 1024, saved.getMaxSize());
        assertEquals(1000L, saved.getMaxCount());
        assertEquals(1000L, saved.getTotalSize());
        assertEquals(2L, saved.getTotalCount());
        assertThrows(BusinessException.class, () -> service.updateSpace(update, owner));
    }

    @Test
    void explicitQuotaWinsAndUnchangedLevelPreservesCustomQuota() {
        SpaceUpdateRequest update = new SpaceUpdateRequest();
        update.setId(101L);
        update.setSpaceLevel(1);
        update.setMaxSize(900000L);
        update.setMaxCount(200L);
        service.updateSpace(update, admin);
        update.setMaxSize(null);
        update.setMaxCount(null);
        update.setSpaceName("new name");
        service.updateSpace(update, admin);
        assertEquals(900000L, service.getById(101).getMaxSize());
        assertEquals(200L, service.getById(101).getMaxCount());
    }

    @Test
    void invalidQuotaOrDowngradeBelowUsageLeavesRecordUnchanged() {
        jdbc.update("UPDATE space SET totalSize=1000,totalCount=2 WHERE id=101");
        SpaceUpdateRequest update = new SpaceUpdateRequest();
        update.setId(101L);
        update.setSpaceName("must not persist");
        update.setMaxCount(1L);
        assertThrows(BusinessException.class, () -> service.updateSpace(update, admin));
        assertEquals("空间101", service.getById(101).getSpaceName());
        update.setMaxCount(0L);
        assertThrows(BusinessException.class, () -> service.updateSpace(update, admin));
        update.setMaxCount(100L);
        update.setMaxSize(-1L);
        assertThrows(BusinessException.class, () -> service.updateSpace(update, admin));
        update.setMaxSize(1000L);
        update.setSpaceLevel(99);
        assertThrows(BusinessException.class, () -> service.updateSpace(update, admin));
    }

    @Test
    void adminUpdateAlsoCannotChangeOwnerUsageOrDeleteState() throws Exception {
        mvc.perform(post("/space/update").header("X-Test-User", "admin")
                        .contentType("application/json")
                        .content("{\"id\":101,\"spaceName\":\"admin changed\","
                                + "\"userId\":1,\"totalCount\":30,\"totalSize\":55,\"isDelete\":1}"))
                .andExpect(jsonPath("$.code").value(0));
        Space saved = service.getById(101);
        assertEquals(2L, saved.getUserId());
        assertEquals(0L, saved.getTotalCount());
        assertEquals(0L, saved.getTotalSize());
        assertEquals(0, saved.getIsDelete());
    }

    @Test
    void emptySpaceDeletionIsLogicalAndOwnerOrAdminOnly() {
        assertThrows(BusinessException.class, () -> service.deleteEmptySpace(101L, other));
        assertTrue(service.deleteEmptySpace(101L, owner));
        assertNull(service.getById(101L));
        assertEquals(1, jdbc.queryForObject("SELECT isDelete FROM space WHERE id=101", Integer.class));
        assertEquals(1, service.listSpaces(null, admin).getTotal());
        assertThrows(BusinessException.class, () -> service.getSpaceVO(101L, owner));
        assertThrows(BusinessException.class, () -> service.deleteEmptySpace(101L, owner));
        assertTrue(service.deleteEmptySpace(102L, admin));
    }

    @Test
    void anyPictureReferenceIncludingDeletedDraftBlocksDeletion() {
        jdbc.update("INSERT INTO picture(url,originalUrl,name,userId,spaceId,uploadStatus,isDelete)"
                + " VALUES('test','test','fixture',2,101,0,1)");
        assertThrows(BusinessException.class, () -> service.deleteEmptySpace(101L, owner));
        jdbc.update("DELETE FROM picture");
        jdbc.update("UPDATE space SET totalCount=1 WHERE id=101");
        assertThrows(BusinessException.class, () -> service.deleteEmptySpace(101L, admin));
        assertNotNull(service.getById(101L));
    }

    @Test
    void updateParticipatesInOuterTransactionRollback() {
        SpaceUpdateRequest update = new SpaceUpdateRequest();
        update.setId(101L);
        update.setSpaceName("rolled back");
        new TransactionTemplate(app.getBean(org.springframework.transaction.PlatformTransactionManager.class))
                .execute(status -> {
                    service.updateSpace(update, admin);
                    assertEquals("rolled back", service.getById(101L).getSpaceName());
                    status.setRollbackOnly();
                    return null;
                });
        assertEquals("空间101", service.getById(101L).getSpaceName());
    }

    @Test
    void creationRequiresLoginAndReturnsStringIdWithDefaults() throws Exception {
        mvc.perform(post("/space/add").contentType("application/json").content("{}"))
                .andExpect(jsonPath("$.code").value(40100));
        String response = mvc.perform(post("/space/add").header("X-Test-User", "admin")
                        .contentType("application/json").content("{}"))
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data").isString())
                .andReturn().getResponse().getContentAsString();
        Long id = app.getBean(com.fasterxml.jackson.databind.ObjectMapper.class)
                .readTree(response).get("data").asLong();
        Space created = service.getById(id);
        assertEquals("默认空间", created.getSpaceName());
        assertEquals(0, created.getSpaceLevel());
        assertEquals(1L, created.getUserId());
        assertEquals(104857600L, created.getMaxSize());
        assertEquals(100L, created.getMaxCount());
        assertEquals(0L, created.getTotalCount());
        assertEquals(0L, created.getTotalSize());
        assertNotNull(created.getCreateTime());
    }

    @Test
    void ordinaryUserCanCreateCommonSpaceAndOverpostedFieldsAreIgnored() throws Exception {
        jdbc.update("DELETE FROM space WHERE id=101");
        mvc.perform(post("/space/add").header("X-Test-User", "owner")
                        .contentType("application/json")
                        .content("{\"id\":999,\"spaceName\":\"  我的照片  \",\"spaceLevel\":0,"
                                + "\"userId\":3,\"maxCount\":9999,\"maxSize\":9999999999,"
                                + "\"totalCount\":100,\"totalSize\":200,\"isDelete\":1}"))
                .andExpect(jsonPath("$.code").value(0));
        Space created = service.lambdaQuery().eq(Space::getUserId, 2L).one();
        assertEquals("我的照片", created.getSpaceName());
        assertNotEquals(999L, created.getId());
        assertEquals(100L, created.getMaxCount());
        assertEquals(104857600L, created.getMaxSize());
        assertEquals(0L, created.getTotalCount());
        assertEquals(0L, created.getTotalSize());
        assertEquals(0, created.getIsDelete());
    }

    @Test
    void omittedLevelAndBlankNameAreNormalizedBeforePermissionCheck() {
        jdbc.update("DELETE FROM space WHERE id=101");
        SpaceAddRequest add = new SpaceAddRequest();
        add.setSpaceName("   ");
        Space created = service.getById(service.addSpace(add, owner));
        assertEquals("默认空间", created.getSpaceName());
        assertEquals(0, created.getSpaceLevel());
    }

    @Test
    void ordinaryUserCannotCreatePaidTierButAdminCanCreateForSelf() {
        for (int tier : new int[]{1, 2}) {
            SpaceAddRequest add = new SpaceAddRequest();
            add.setSpaceLevel(tier);
            BusinessException denied = assertThrows(BusinessException.class,
                    () -> service.addSpace(add, owner));
            assertEquals(ErrorCode.NO_AUTH_ERROR.getCode(), denied.getCode());
            Long id = service.addSpace(add, admin);
            Space created = service.getById(id);
            assertEquals(tier, created.getSpaceLevel());
            assertEquals(SpaceLevelEnum.getEnumByValue(tier).getMaxSize(), created.getMaxSize());
            assertEquals(SpaceLevelEnum.getEnumByValue(tier).getMaxCount(), created.getMaxCount());
            assertEquals(1L, created.getUserId());
            service.deleteEmptySpace(id, admin);
        }
    }

    @Test
    void creationRejectsInvalidInputWithoutWriting() {
        assertThrows(BusinessException.class, () -> service.addSpace(null, owner));
        assertThrows(BusinessException.class, () -> service.addSpace(new SpaceAddRequest(), null));
        assertThrows(BusinessException.class, () -> service.addSpace(new SpaceAddRequest(), user(5, 99)));
        SpaceAddRequest add = new SpaceAddRequest();
        add.setSpaceLevel(99);
        assertThrows(BusinessException.class, () -> service.addSpace(add, admin));
        add.setSpaceLevel(0);
        add.setSpaceName(String.join("", Collections.nCopies(31, "a")));
        assertThrows(BusinessException.class, () -> service.addSpace(add, admin));
        assertEquals(2, service.count());
    }

    @Test
    void repeatedCreationIsRejectedForOrdinaryUserButAdminMayCreateSeveral() throws Exception {
        mvc.perform(post("/space/add").header("X-Test-User", "owner")
                        .contentType("application/json").content("{}"))
                .andExpect(jsonPath("$.code").value(50001));
        service.addSpace(new SpaceAddRequest(), admin);
        assertNotNull(service.getById(service.addSpace(new SpaceAddRequest(), admin)));
        assertEquals(4, service.count());
    }

    @Test
    void deletedSpaceDoesNotConsumeCreationSlotAcrossMultipleCycles() {
        long previous = 101;
        for (int i = 0; i < 3; i++) {
            service.deleteEmptySpace(previous, owner);
            long next = service.addSpace(new SpaceAddRequest(), owner);
            assertNotEquals(previous, next);
            assertNull(service.getById(previous));
            previous = next;
        }
        assertEquals(1, service.lambdaQuery().eq(Space::getUserId, 2L).count());
        assertEquals(4, jdbc.queryForObject("SELECT COUNT(*) FROM space WHERE userId=2", Integer.class));
    }

    @Test
    void concurrentCreationForSameUserCommitsOnlyOneSpace() throws Exception {
        jdbc.update("DELETE FROM space WHERE id=101");
        int callers = 16;
        ExecutorService executor = Executors.newFixedThreadPool(callers);
        CountDownLatch ready = new CountDownLatch(callers);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Boolean>> results = new ArrayList<>();
        try {
            for (int i = 0; i < callers; i++) {
                results.add(executor.submit(() -> {
                    ready.countDown();
                    assertTrue(start.await(10, TimeUnit.SECONDS));
                    try {
                        service.addSpace(new SpaceAddRequest(), owner);
                        return true;
                    } catch (BusinessException error) {
                        assertEquals(50001, error.getCode());
                        assertTrue(error.getMessage().contains("仅能有一个"));
                        return false;
                    }
                }));
            }
            assertTrue(ready.await(10, TimeUnit.SECONDS));
            start.countDown();
            int successes = 0;
            for (Future<Boolean> result : results) {
                // MySQL may be reached over an SSH tunnel: 16 serialized transactions
                // are not a latency benchmark. Keep the one-success/one-row assertions.
                if (result.get(mysql() ? 120 : 15, TimeUnit.SECONDS)) {
                    successes++;
                }
            }
            assertEquals(1, successes);
            assertEquals(1, service.lambdaQuery().eq(Space::getUserId, 2L).count());
        } finally {
            start.countDown();
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(10, TimeUnit.SECONDS));
        }
    }

    @Test
    void injectedFailureAfterInsertRollsBackAndDoesNotKeepCreationSlot() {
        ProbedTransactionTemplate template = app.getBean(ProbedTransactionTemplate.class);
        template.failAfterAction = true;
        assertThrows(IllegalStateException.class, () -> service.addSpace(new SpaceAddRequest(), admin));
        assertEquals(2, service.count());
        template.failAfterAction = false;
        assertNotNull(service.getById(service.addSpace(new SpaceAddRequest(), admin)));
    }

    @Test
    void creationRejectsAmbientTransactionInsteadOfUnlockingBeforeOuterCommit() {
        new TransactionTemplate(app.getBean(PlatformTransactionManager.class)).execute(status -> {
            BusinessException error = assertThrows(BusinessException.class,
                    () -> service.addSpace(new SpaceAddRequest(), admin));
            assertTrue(error.getMessage().contains("外层事务"));
            return null;
        });
        assertEquals(2, service.count());
    }

    @Test
    void userLockIsHeldUntilTransactionCommitCompletes() throws Exception {
        User freshUser = user(4L, 0);
        ProbedTransactionTemplate template = app.getBean(ProbedTransactionTemplate.class);
        template.beforeCommit = new CountDownLatch(1);
        template.allowCommit = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<Long> first = executor.submit(() -> service.addSpace(new SpaceAddRequest(), freshUser));
            assertTrue(template.beforeCommit.await(10, TimeUnit.SECONDS));
            assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM space WHERE userId=4", Integer.class));
            CountDownLatch attempted = new CountDownLatch(1);
            Future<Long> second = executor.submit(() -> {
                attempted.countDown();
                return service.addSpace(new SpaceAddRequest(), freshUser);
            });
            assertTrue(attempted.await(10, TimeUnit.SECONDS));
            assertThrows(TimeoutException.class, () -> second.get(200, TimeUnit.MILLISECONDS));
            assertEquals(1, template.entries.get(), "第二个请求不应在第一个提交前开启事务");
            template.allowCommit.countDown();
            assertNotNull(first.get(10, TimeUnit.SECONDS));
            ExecutionException duplicate = assertThrows(ExecutionException.class,
                    () -> second.get(10, TimeUnit.SECONDS));
            assertInstanceOf(BusinessException.class, duplicate.getCause());
            assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM space WHERE userId=4", Integer.class));
        } finally {
            template.allowCommit.countDown();
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(10, TimeUnit.SECONDS));
        }
    }

    @Test
    void incrementalMigrationPreservesExistingPublicPicture() throws Exception {
        jdbc.update("INSERT INTO picture(id,url,originalUrl,name,userId,uploadStatus,updateTime)"
                + " VALUES(999,'old','old','historical',2,1,'2020-01-01 00:00:00')");
        jdbc.execute("DROP TABLE space");
        jdbc.execute("ALTER TABLE picture DROP COLUMN spaceId");
        script("sql/alter_space_foundation.sql");
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM picture", Integer.class));
        assertNull(jdbc.queryForObject("SELECT spaceId FROM picture WHERE id=999", Long.class));
        assertEquals("old", jdbc.queryForObject("SELECT url FROM picture WHERE id=999", String.class));
        assertTrue(jdbc.queryForObject("SELECT updateTime FROM picture WHERE id=999",
                java.sql.Timestamp.class).toString().startsWith("2020-01-01"));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM space", Integer.class));
        jdbc.update("INSERT INTO space(id,spaceName,userId,updateTime) VALUES(501,'old private',2,'2020-01-01 00:00:00')");
        if (mysql()) {
            script("sql/alter_team_space.sql");
            script("sql/alter_team_space.sql");
        } else {
            jdbc.execute("ALTER TABLE space ADD COLUMN spaceType INT NOT NULL DEFAULT 0");
        }
        assertEquals(0, jdbc.queryForObject("SELECT spaceType FROM space WHERE id=501", Integer.class));
        assertTrue(jdbc.queryForObject("SELECT updateTime FROM space WHERE id=501", java.sql.Timestamp.class)
                .toString().startsWith("2020-01-01"));
        assertNull(jdbc.queryForObject("SELECT spaceId FROM picture WHERE id=999", Long.class));
        assertEquals("old", jdbc.queryForObject("SELECT url FROM picture WHERE id=999", String.class));
    }

    @Test void privateAndTeamCreationAreIndependentAndOwnerMembershipIsAtomic() {
        SpaceAddRequest team = new SpaceAddRequest(); team.setSpaceType(1); team.setSpaceName("Team");
        long id = service.addSpace(team, owner);
        assertEquals(1, service.getById(id).getSpaceType());
        assertEquals("admin", jdbc.queryForObject("SELECT spaceRole FROM space_user WHERE spaceId=? AND userId=2", String.class, id));
        assertEquals(1, service.listMySpaces(new SpaceQueryRequest(), owner).getTotal());
        assertThrows(BusinessException.class, () -> service.addSpace(team, owner));
        SpaceQueryRequest query = new SpaceQueryRequest(); query.setSpaceType(1);
        assertEquals(1, service.listMySpaces(query, owner).getTotal());
        assertTrue(service.getSpaceVO(id, owner).getPermissionList().contains("spaceUser:manage"));
    }

    @Test void failedTeamCreationRollsBackBothSpaceAndOwnerMembership() {
        ProbedTransactionTemplate tx = app.getBean(ProbedTransactionTemplate.class); tx.failAfterAction = true;
        SpaceAddRequest team = new SpaceAddRequest(); team.setSpaceType(1);
        assertThrows(IllegalStateException.class, () -> service.addSpace(team, owner));
        assertEquals(2, service.count());
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM space_user", Integer.class));
    }

    @Test void deletingEmptyTeamAlsoRemovesMembersAndAllowsRecreation() {
        SpaceAddRequest r = new SpaceAddRequest(); r.setSpaceType(1);
        long id = service.addSpace(r, owner);
        assertTrue(service.deleteEmptySpace(id, owner));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM space_user WHERE spaceId=?", Integer.class, id));
        assertNotEquals(id, service.addSpace(r, owner));
    }

    @Test void spaceTypeWhitelistAndMetadataChangesNeverConvertPrivateToTeam() {
        SpaceAddRequest r = new SpaceAddRequest(); r.setSpaceType(9);
        assertThrows(BusinessException.class, () -> service.addSpace(r, owner));
        SpaceQueryRequest query = new SpaceQueryRequest(); query.setSpaceType(-1);
        assertThrows(BusinessException.class, () -> service.listSpaces(query, admin));
        r.setSpaceType(1); long id = service.addSpace(r, owner);
        SpaceEditRequest rename = new SpaceEditRequest(); rename.setId(id); rename.setSpaceName("renamed");
        service.editSpace(rename, owner); assertEquals(1, service.getById(id).getSpaceType());
        assertThrows(BusinessException.class, () -> service.editSpace(rename, other));
    }

    private static User user(long id, int role) {
        User user = new User();
        user.setId(id);
        user.setUserRole(role);
        return user;
    }

    @TestConfiguration(proxyBeanMethods = false)
    @EnableAutoConfiguration
    @EnableAspectJAutoProxy
    @Import({MybatisPlusConfig.class, JsonConfig.class, SpaceServiceImpl.class,
            learn.gjx.tuku1.manager.space.PictureAccessManager.class,
            SpaceController.class, AuthInterceptor.class, GlobalExceptionHandler.class, UserSpaceCreationLock.class})
    static class TestApplication {
        @Bean
        UserService userService() {
            return mock(UserService.class);
        }

        @Bean
        ProbedTransactionTemplate transactionTemplate(PlatformTransactionManager manager) {
            return new ProbedTransactionTemplate(manager);
        }
    }

    /** 测试专用故障/提交门闩；真实事务仍由 Spring/MySQL 或 H2 执行。 */
    static class ProbedTransactionTemplate extends TransactionTemplate {
        volatile boolean failAfterAction;
        volatile CountDownLatch beforeCommit;
        volatile CountDownLatch allowCommit;
        final AtomicInteger entries = new AtomicInteger();

        ProbedTransactionTemplate(PlatformTransactionManager manager) {
            super(manager);
            setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRED);
        }

        void resetProbe() {
            failAfterAction = false;
            beforeCommit = null;
            allowCommit = null;
            entries.set(0);
        }

        @Override
        public <T> T execute(TransactionCallback<T> action) {
            entries.incrementAndGet();
            return super.execute(status -> {
                T result = action.doInTransaction(status);
                if (failAfterAction) {
                    throw new IllegalStateException("injected failure after insert");
                }
                if (beforeCommit != null) {
                    TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                        @Override
                        public void beforeCommit(boolean readOnly) {
                            beforeCommit.countDown();
                            try {
                                assertTrue(allowCommit.await(10, TimeUnit.SECONDS));
                            } catch (InterruptedException error) {
                                Thread.currentThread().interrupt();
                                throw new IllegalStateException(error);
                            }
                        }
                    });
                }
                return result;
            });
        }
    }
}
