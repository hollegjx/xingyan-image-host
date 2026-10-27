package learn.gjx.tuku1.service;

import learn.gjx.tuku1.aop.AuthInterceptor;
import learn.gjx.tuku1.config.JsonConfig;
import learn.gjx.tuku1.config.MybatisPlusConfig;
import learn.gjx.tuku1.controller.SpaceAnalyzeController;
import learn.gjx.tuku1.exception.BusinessException;
import learn.gjx.tuku1.exception.ErrorCode;
import learn.gjx.tuku1.exception.GlobalExceptionHandler;
import learn.gjx.tuku1.manager.space.PictureAccessManager;
import learn.gjx.tuku1.manager.space.SpaceAnalysisRepository;
import learn.gjx.tuku1.model.dto.space.analyze.SpaceAnalyzeRequest;
import learn.gjx.tuku1.model.entity.User;
import learn.gjx.tuku1.model.vo.SpaceAnalysisVO;
import learn.gjx.tuku1.model.vo.SpaceAnalysisVO.Distribution;
import org.junit.jupiter.api.*;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import javax.servlet.http.HttpServletRequest;
import java.sql.*;
import java.util.*;
import java.util.stream.Collectors;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** H2 测通用聚合/鉴权，MYSQL_TEST_* 开启时同一组用例在一次性 MySQL 库验证真实 SQL。 */
class SpaceAnalyzeIntegrationTest {
    private static final String MYSQL = System.getenv("MYSQL_TEST_SERVER_URL");
    private static ConfigurableApplicationContext app;
    private static String schema;
    private static JdbcTemplate jdbc;
    private SpaceAnalyzeService service;
    private MockMvc mvc;
    private final User owner = user(2, 0);
    private final User admin = user(1, 1);
    private static boolean mysql() { return MYSQL != null && !MYSQL.isEmpty(); }
    private static Connection connection() throws Exception {
        return DriverManager.getConnection(MYSQL, System.getenv("MYSQL_TEST_USERNAME"), System.getenv("MYSQL_TEST_PASSWORD"));
    }

    @BeforeAll static void start() throws Exception {
        String url = "jdbc:h2:mem:analysis_" + UUID.randomUUID() + ";MODE=MySQL;NON_KEYWORDS=USER,DAY;DB_CLOSE_DELAY=-1";
        try {
            if (mysql()) {
                java.net.URI endpoint = java.net.URI.create(MYSQL.substring(5));
                assertEquals("mysql", endpoint.getScheme());
                assertTrue(Arrays.asList("localhost", "127.0.0.1").contains(endpoint.getHost()));
                assertEquals("/", endpoint.getPath());
                schema = "tuku_space_test_" + UUID.randomUUID().toString().replace("-", "");
                try (Connection c = connection(); Statement s = c.createStatement()) {
                    s.execute("CREATE DATABASE `" + schema + "` CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci");
                }
                int q = MYSQL.indexOf('?');
                url = q < 0 ? MYSQL + schema : MYSQL.substring(0, q) + schema + MYSQL.substring(q);
            }
            app = new SpringApplicationBuilder(TestApplication.class).web(WebApplicationType.SERVLET).run(
                    "--spring.config.location=optional:classpath:analysis-empty.yml", "--server.port=0",
                    "--server.address=127.0.0.1", "--spring.session.store-type=none",
                    "--spring.datasource.url=" + url,
                    "--spring.datasource.driver-class-name=" + (mysql() ? "com.mysql.cj.jdbc.Driver" : "org.h2.Driver"),
                    "--spring.datasource.username=" + (mysql() ? System.getenv("MYSQL_TEST_USERNAME") : "sa"),
                    "--spring.datasource.password=" + (mysql() ? System.getenv("MYSQL_TEST_PASSWORD") : ""),
                    "--mybatis-plus.configuration.map-underscore-to-camel-case=false",
                    "--spring.main.banner-mode=off", "--logging.level.root=WARN");
            jdbc = app.getBean(JdbcTemplate.class);
            jdbc.execute("CREATE TABLE space(id BIGINT PRIMARY KEY,spaceName VARCHAR(128),userId BIGINT,spaceLevel INT DEFAULT 0,spaceType INT DEFAULT 0,"
                    + "totalSize BIGINT DEFAULT 0,totalCount BIGINT DEFAULT 0,maxSize BIGINT DEFAULT 1000,maxCount BIGINT DEFAULT 10,"
                    + "createTime DATETIME,editTime DATETIME,updateTime DATETIME,isDelete INT DEFAULT 0)");
            jdbc.execute("CREATE TABLE picture(id BIGINT AUTO_INCREMENT PRIMARY KEY,spaceId BIGINT,userId BIGINT DEFAULT 2,"
                    + "category VARCHAR(64),tags VARCHAR(512),picSize BIGINT,uploadStatus INT DEFAULT 1,reviewStatus INT DEFAULT 0,"
                    + "createTime DATETIME DEFAULT '2025-01-01 12:00:00',isDelete INT DEFAULT 0)");
        } catch (Exception e) { stop(); throw e; }
    }

    @AfterAll static void stop() throws Exception {
        if (app != null) { app.close(); app = null; }
        if (schema != null) {
            try (Connection c = connection(); Statement s = c.createStatement()) { s.execute("DROP DATABASE `" + schema + "`"); }
            schema = null;
        }
    }

    @BeforeEach void resetData() {
        service = app.getBean(SpaceAnalyzeService.class);
        mvc = MockMvcBuilders.webAppContextSetup((WebApplicationContext) app).build();
        jdbc.update("DELETE FROM picture"); jdbc.update("DELETE FROM space");
        jdbc.update("INSERT INTO space(id,spaceName,userId,totalSize,totalCount) VALUES(101,'本人空间',2,600,3),(102,'他人空间',3,900,1)");
        UserService users = app.getBean(UserService.class);
        reset(users);
        when(users.getLoginUser(any())).thenAnswer(i -> {
            String value = ((HttpServletRequest) i.getArgument(0)).getHeader("X-Test-User");
            if ("owner".equals(value)) return owner;
            if ("admin".equals(value)) return admin;
            throw new BusinessException(ErrorCode.NOT_LOGIN_ERROR);
        });
    }
    private SpaceAnalyzeRequest request(String scope) {
        SpaceAnalyzeRequest r = new SpaceAnalyzeRequest(); r.setScope(scope);
        if ("SPACE".equals(scope)) r.setSpaceId(101L);
        r.setStartDate("2024-12-29"); r.setEndDate("2025-01-06"); return r;
    }
    private void picture(Long space, Long size, String category, String tags, int status, int deleted) {
        jdbc.update("INSERT INTO picture(spaceId,picSize,category,tags,uploadStatus,isDelete) VALUES(?,?,?,?,?,?)",
                space, size, category, tags, status, deleted);
    }
    @Test void endpointsRequireSessionAndPreventCrossSpaceIncludingAdmin() throws Exception {
        for (String endpoint : Arrays.asList("usage", "category", "tag", "size", "user", "rank", "dashboard")) {
            mvc.perform(post("/space/analyze/" + endpoint).contentType("application/json").content("{\"scope\":\"SPACE\",\"spaceId\":101}"))
                    .andExpect(jsonPath("$.code").value(40100));
            mvc.perform(post("/space/analyze/" + endpoint).header("X-Test-User", "admin")
                    .contentType("application/json").content("{\"scope\":\"SPACE\",\"spaceId\":101}"))
                    .andExpect(jsonPath("$.code").value(40101));
            mvc.perform(post("/space/analyze/" + endpoint).header("X-Test-User", "owner")
                    .contentType("application/json").content("{\"scope\":\"SPACE\",\"spaceId\":102}"))
                    .andExpect(jsonPath("$.code").value(40101));
        }
    }
    @Test void invalidOrAmbiguousScopeNeverFallsBackToPublic() {
        for (String scope : Arrays.asList(null, "", "space", "PUBLIC OR 1=1")) {
            assertThrows(BusinessException.class, () -> service.usage(request(scope), admin));
        }
        SpaceAnalyzeRequest r = request("PUBLIC"); r.setSpaceId(101L);
        assertThrows(BusinessException.class, () -> service.usage(r, admin));
        assertThrows(BusinessException.class, () -> service.usage(request("PUBLIC"), owner));
        assertThrows(BusinessException.class, () -> service.usage(request("ALL"), owner));
        SpaceAnalyzeRequest invalid = request("SPACE"); invalid.setSpaceId(0L);
        assertThrows(BusinessException.class, () -> service.usage(invalid, owner));
    }
    @Test void deletedSpaceAndDisabledUserAreDenied() {
        jdbc.update("UPDATE space SET isDelete=1 WHERE id=101");
        assertThrows(BusinessException.class, () -> service.usage(request("SPACE"), owner));
        admin.setIsDelete(1);
        assertThrows(BusinessException.class, () -> service.usage(request("ALL"), admin));
    }
    @Test void usageIncludesDraftsButDistributionsDoNotAndNoPrivateLeak() {
        picture(101L, 100L, "风景", "[]", 1, 0);
        picture(101L, 200L, "风景", "[]", 1, 0);
        picture(101L, 300L, "草稿类别", "[]", 0, 0);
        picture(101L, 999L, "已删除", "[]", 1, 1);
        picture(102L, 900L, "他人秘密", "[]", 1, 0);
        picture(null, 700L, "公共", "[]", 1, 0);
        SpaceAnalysisVO.Usage usage = service.usage(request("SPACE"), owner);
        assertEquals(600L, usage.getUsedSize()); assertEquals(3L, usage.getUsedCount());
        assertEquals(2L, usage.getCompletedCount()); assertEquals(1L, usage.getDraftCount());
        assertEquals(60.0, usage.getSizeUsageRatio()); assertEquals(30.0, usage.getCountUsageRatio());
        List<Distribution> categories = service.categories(request("SPACE"), owner);
        assertEquals(1, categories.size()); assertEquals("风景", categories.get(0).getName());
        assertEquals(2L, categories.get(0).getCount()); assertEquals(300L, categories.get(0).getTotalSize());
        assertEquals(1L, service.usage(request("PUBLIC"), admin).getUsedCount());
    }
    @Test void globalExcludesDeletedAndMissingSpacesAndPreservesPrivacy() {
        picture(null, 100L, "公共", "[]", 1, 0);
        picture(101L, 200L, "秘密", "[]", 1, 0);
        picture(102L, 300L, "删除空间", "[]", 1, 0);
        picture(999L, 400L, "孤立空间", "[]", 1, 0);
        jdbc.update("UPDATE space SET isDelete=1 WHERE id=102");
        SpaceAnalysisVO result = service.dashboard(request("ALL"), admin);
        assertEquals(2L, result.getUsage().getUsedCount()); assertEquals(300L, result.getUsage().getUsedSize());
        assertNull(result.getUsage().getMaxSize()); assertTrue(result.getTags().isEmpty()); assertTrue(result.getCategories().isEmpty());
        assertThrows(BusinessException.class, () -> service.tags(request("ALL"), admin));
        assertThrows(BusinessException.class, () -> service.categories(request("ALL"), admin));
    }
    @Test void emptyAndZeroQuotaAreExplicitAndHttpLongsRemainStrings() throws Exception {
        jdbc.update("UPDATE space SET totalCount=0,totalSize=0,maxCount=0,maxSize=0 WHERE id=101");
        SpaceAnalysisVO.Usage u = service.usage(request("SPACE"), owner);
        assertNull(u.getCountUsageRatio()); assertNull(u.getSizeUsageRatio());
        assertEquals(0L, u.getCompletedCount()); assertTrue(service.categories(request("SPACE"), owner).isEmpty());
        mvc.perform(post("/space/analyze/usage").header("X-Test-User", "owner")
                .contentType("application/json").content("{\"scope\":\"SPACE\",\"spaceId\":\"101\"}"))
                .andExpect(jsonPath("$.code").value(0)).andExpect(jsonPath("$.data.usedCount").value("0"))
                .andExpect(header().string("Cache-Control", "no-store")).andExpect(header().string("Vary", "Cookie"));
    }
    @Test void categoryTailAndEmptyCategoryDoNotLoseCounts() {
        for (int i = 0; i < 23; i++) picture(101L, 1L, "cat" + i, "[]", 1, 0);
        picture(101L, 1L, "  ", "[]", 1, 0); picture(101L, 1L, null, "[]", 1, 0);
        List<Distribution> result = service.categories(request("SPACE"), owner);
        assertEquals(21, result.size());
        assertEquals(25, result.stream().mapToLong(Distribution::getCount).sum());
        assertEquals("未分类", result.get(0).getName());
        assertEquals("其他分类（合并）", result.get(20).getName());
    }
    @Test void sizesHaveExactNonOverlappingBoundariesAndUnknownBucket() {
        for (Long n : Arrays.asList(0L, 102399L, 102400L, 524287L, 524288L, 1048575L, 1048576L, 2097151L, 2097152L, null, -1L))
            picture(101L, n, "x", "[]", 1, 0);
        List<Distribution> r = service.sizes(request("SPACE"), owner);
        assertEquals(Arrays.asList(2L, 2L, 2L, 2L, 1L, 2L), r.stream().map(Distribution::getCount).collect(Collectors.toList()));
        assertEquals(0L, r.get(5).getTotalSize());
    }
    @Test void dayWeekMonthFillGapsAndHandleYearBoundary() {
        picture(101L, 100L, "x", "[]", 1, 0);
        SpaceAnalyzeRequest r = request("SPACE");
        assertEquals(9, service.trend(r, owner).getPoints().size());
        assertEquals(1L, service.trend(r, owner).getPoints().get(3).getCount());
        r.setTimeDimension("week");
        List<Distribution> weeks = service.trend(r, owner).getPoints();
        assertEquals(Arrays.asList("2024-12-23", "2024-12-30", "2025-01-06"), weeks.stream().map(Distribution::getName).collect(Collectors.toList()));
        assertEquals(1L, weeks.get(1).getCount());
        r.setTimeDimension("month");
        assertEquals(Arrays.asList(0L, 1L), service.trend(r, owner).getPoints().stream().map(Distribution::getCount).collect(Collectors.toList()));
    }
    @Test void trendIncludesEndDayButExcludesNextMidnightAndFiltersUser() {
        picture(101L, 1L, "x", "[]", 1, 0);
        jdbc.update("UPDATE picture SET createTime='2025-01-06 23:59:59'");
        picture(101L, 1L, "x", "[]", 1, 0);
        jdbc.update("UPDATE picture SET createTime='2025-01-07 00:00:00' WHERE createTime='2025-01-01 12:00:00'");
        SpaceAnalyzeRequest r = request("SPACE");
        assertEquals(1, service.trend(r, owner).getPoints().stream().mapToLong(Distribution::getCount).sum());
        r.setUserId(3L);
        assertEquals(0, service.trend(r, owner).getPoints().stream().mapToLong(Distribution::getCount).sum());
        SpaceAnalyzeRequest all = request("ALL"); all.setUserId(2L);
        assertThrows(BusinessException.class, () -> service.trend(all, admin));
    }
    @Test void invalidDatesDimensionsAndLimitsAreRejected() {
        for (String dimension : Arrays.asList(null, "year", "day; DROP TABLE picture")) {
            SpaceAnalyzeRequest r = request("SPACE"); r.setTimeDimension(dimension);
            assertThrows(BusinessException.class, () -> service.trend(r, owner));
        }
        for (String[] range : new String[][] {{"2025-01-10","2025-01-01"}, {"2023-01-01","2025-01-01"},
                {"2025-02-30","2025-03-01"}, {"2025-01-01",null}, {"2999-01-01","2999-01-01"}}) {
            SpaceAnalyzeRequest r = request("SPACE"); r.setStartDate(range[0]); r.setEndDate(range[1]);
            assertThrows(BusinessException.class, () -> service.trend(r, owner));
        }
        for (Integer n : Arrays.asList(null, 0, -1, 21)) {
            SpaceAnalyzeRequest r = request("ALL"); r.setTopN(n);
            assertThrows(BusinessException.class, () -> service.rankings(r, admin));
        }
    }
    @Test void rankUsesQuotaLedgerStableOrderingAndOnlyAdminGlobal() throws Exception {
        jdbc.update("INSERT INTO space(id,spaceName,userId,totalSize,totalCount) VALUES(9007199254740993,'大 ID',4,900,7)");
        List<SpaceAnalysisVO.Ranking> result = service.rankings(request("ALL"), admin);
        assertEquals(102L, result.get(0).getSpaceId()); assertEquals(9007199254740993L, result.get(1).getSpaceId());
        assertThrows(BusinessException.class, () -> service.rankings(request("SPACE"), owner));
        mvc.perform(post("/space/analyze/rank").header("X-Test-User", "admin").contentType("application/json").content("{\"scope\":\"ALL\"}"))
                .andExpect(jsonPath("$.data[1].spaceId").value("9007199254740993"));
    }
    @Test void mysqlTagJsonDedupAndMalformedValuesStaySafe() {
        Assumptions.assumeTrue(mysql(), "JSON_TABLE requires MySQL 8; exercised in isolated MySQL run");
        picture(101L, 1L, "x", "[\"猫\",\"猫\",\" 狗 \",\"\",null,3,{},[\"bad\"]]", 1, 0);
        picture(101L, 1L, "x", "[\"猫\",\"Cat\",\"cat\"]", 1, 0);
        for (String malformed : Arrays.asList("{bad", "{}", "null", "42", null)) picture(101L, 1L, "x", malformed, 1, 0);
        picture(102L, 1L, "x", "[\"秘密\"]", 1, 0);
        picture(101L, 1L, "x", "[\"草稿\"]", 0, 0);
        picture(101L, 1L, "x", "[\"已删除\"]", 1, 1);
        List<Distribution> tags = service.tags(request("SPACE"), owner);
        assertEquals(4, tags.size()); assertEquals("猫", tags.get(0).getName()); assertEquals(2L, tags.get(0).getCount());
        assertEquals(5, tags.stream().mapToLong(Distribution::getCount).sum());
        assertTrue(tags.stream().anyMatch(t -> t.getName().equals("狗")));
        String longTag = String.join("", Collections.nCopies(40, "长"));
        picture(101L, 1L, "x", "[\"" + longTag + "\"]", 1, 0);
        assertTrue(service.tags(request("SPACE"), owner).stream().anyMatch(t -> t.getName().equals(longTag)),
                "现有图片允许 JSON 总长度 512，不能静默丢弃超过 32 字的合法标签");
    }
    @Test void mysqlDashboardEndToEndHasOnlyAggregatesAndBoundedTags() throws Exception {
        Assumptions.assumeTrue(mysql(), "dashboard includes MySQL JSON_TABLE");
        for (int i = 0; i < 35; i++) picture(101L, 100L, "分类", "[\"tag" + i + "\"]", 1, 0);
        assertEquals(30, service.dashboard(request("SPACE"), owner).getTags().size());
        mvc.perform(post("/space/analyze/dashboard").header("X-Test-User", "owner").contentType("application/json")
                        .content("{\"scope\":\"SPACE\",\"spaceId\":\"101\"}"))
                .andExpect(jsonPath("$.code").value(0)).andExpect(jsonPath("$.data.usage.completedCount").value("35"))
                .andExpect(jsonPath("$.data.url").doesNotExist()).andExpect(header().string("Cache-Control", "no-store"));
    }
    private static User user(long id, int role) { User u = new User(); u.setId(id); u.setUserRole(role); return u; }
    @TestConfiguration(proxyBeanMethods = false) @EnableAutoConfiguration @EnableAspectJAutoProxy
    @Import({MybatisPlusConfig.class, JsonConfig.class, PictureAccessManager.class, SpaceAnalysisRepository.class,
            SpaceAnalyzeService.class, SpaceAnalyzeController.class, AuthInterceptor.class, GlobalExceptionHandler.class})
    static class TestApplication { @Bean UserService userService() { return mock(UserService.class); } }
}
