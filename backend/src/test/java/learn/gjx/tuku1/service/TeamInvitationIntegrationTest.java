package learn.gjx.tuku1.service;

import learn.gjx.tuku1.aop.AuthInterceptor;
import learn.gjx.tuku1.config.*;
import learn.gjx.tuku1.controller.TeamInvitationController;
import learn.gjx.tuku1.exception.*;
import learn.gjx.tuku1.manager.space.PictureAccessManager;
import learn.gjx.tuku1.mapper.UserMapper;
import learn.gjx.tuku1.model.dto.space.*;
import learn.gjx.tuku1.model.entity.User;
import learn.gjx.tuku1.model.vo.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.*;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.any;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

class TeamInvitationIntegrationTest {
    static ConfigurableApplicationContext app;
    static JdbcTemplate jdbc;
    TeamInvitationService invitations;
    MockMvc mvc;
    static com.fasterxml.jackson.databind.ObjectMapper json = new com.fasterxml.jackson.databind.ObjectMapper();
    @BeforeAll static void start() throws Exception {
        app = new SpringApplicationBuilder(TestApplication.class).web(WebApplicationType.SERVLET).run(
                "--spring.config.location=optional:classpath:invite-empty.yml", "--server.port=0", "--server.address=127.0.0.1",
                "--spring.session.store-type=none", "--spring.datasource.url=jdbc:h2:mem:invites;MODE=MySQL;NON_KEYWORDS=USER;DB_CLOSE_DELAY=-1",
                "--spring.datasource.driver-class-name=org.h2.Driver", "--spring.datasource.username=sa", "--spring.datasource.password=",
                "--mybatis-plus.configuration.map-underscore-to-camel-case=false", "--mybatis-plus.global-config.db-config.logic-delete-field=isDelete",
                "--logging.level.root=WARN");
        jdbc = app.getBean(JdbcTemplate.class);
        for (String file : Arrays.asList("sql/create_sql.sql", "sql/alter_team_invitation.sql")) {
            String ddl = new String(Files.readAllBytes(Paths.get(file)), StandardCharsets.UTF_8)
                .replaceFirst("(?i)use\\s+tuku\\s*;", "")
                .replaceAll("(?i)\\s+collate\\s*=?\\s*utf8mb4_unicode_ci", "")
                .replaceAll("(?i)\\s+comment\\s*=?\\s*'[^']*'", "")
                .replaceAll("(?i)NOT NULL\\s+ON UPDATE CURRENT_TIMESTAMP", "ON UPDATE CURRENT_TIMESTAMP NOT NULL")
                .replace("ENGINE=InnoDB", "").replace("DEFAULT CHARSET=utf8mb4", "");
            new ResourceDatabasePopulator(new ByteArrayResource(ddl.getBytes(StandardCharsets.UTF_8))).execute(app.getBean(javax.sql.DataSource.class));
        }
    }
    @AfterAll static void stop() { if (app != null) app.close(); }
    @BeforeEach void resetData() {
        jdbc.update("DELETE FROM team_invitation"); jdbc.update("DELETE FROM space_user"); jdbc.update("DELETE FROM space"); jdbc.update("DELETE FROM user");
        for (long id = 1; id <= 5; id++) jdbc.update("INSERT INTO user(id,userAccount,userPassword,userName,userRole) VALUES(?,?,?,?,?)", id,"fixture-"+id,"unused","fixture",id==5?1:0);
        jdbc.update("INSERT INTO space(id,spaceName,spaceType,userId,spaceLevel) VALUES(10,'邀请团队',1,1,0),(11,'另一团队',1,5,0)");
        jdbc.update("INSERT INTO space_user(id,spaceId,userId,spaceRole) VALUES(1,10,1,'admin'),(2,10,2,'editor'),(5,11,5,'admin')");
        invitations = app.getBean(TeamInvitationService.class);
        mvc = MockMvcBuilders.webAppContextSetup((WebApplicationContext) app).build();
        UserService users = app.getBean(UserService.class); reset(users);
        when(users.getLoginUser(any())).thenAnswer(i -> {
            javax.servlet.http.HttpServletRequest request = i.getArgument(0); String id = request.getHeader("X-Test-User");
            if (id == null) throw new BusinessException(ErrorCode.NOT_LOGIN_ERROR); return user(Long.parseLong(id));
        });
    }
    User user(long id) { return app.getBean(UserMapper.class).selectById(id); }
    TeamInviteCreateRequest request(int days, Integer maximum) {
        TeamInviteCreateRequest r = new TeamInviteCreateRequest(); r.setSpaceId(10L); r.setSpaceRole("viewer"); r.setValidDays(days); r.setMaxUses(maximum); return r;
    }
    TeamInvitationCreatedVO create(Integer maximum) { return invitations.create(request(1,maximum),user(1)); }
    @Test void onlyTeamAdministratorsCanCreateAndManage() {
        assertThrows(BusinessException.class, () -> invitations.create(request(1,null),null));
        assertThrows(BusinessException.class, () -> invitations.create(request(1,null),user(2)));
        assertThrows(BusinessException.class, () -> invitations.create(request(1,null),user(5)));
        create(null); assertEquals(1,invitations.list(10L,user(1)).size());
        assertThrows(BusinessException.class, () -> invitations.list(10L,user(2)));
    }
    @Test void credentialIsRandomAndOnlyHashIsPersistedOrListed() throws Exception {
        TeamInvitationCreatedVO created = create(null); String token = created.getToken();
        assertTrue(java.util.Base64.getUrlDecoder().decode(token).length >= 16);
        assertFalse(created.toString().contains(token));
        String hash = jdbc.queryForObject("SELECT tokenHash FROM team_invitation WHERE id=?",String.class,created.getId());
        assertTrue(hash.equals(cn.hutool.crypto.digest.DigestUtil.sha256Hex(token))); assertFalse(hash.equals(token));
        assertFalse(json.writeValueAsString(invitations.list(10L,user(1))).contains(token));
        assertFalse(create(null).getToken().equals(token));
    }
    @ParameterizedTest @ValueSource(ints={1,7}) void supportedExpiryAndRoles(int days) {
        TeamInviteCreateRequest r=request(days,3); r.setSpaceRole("editor");
        TeamInvitationCreatedVO result=invitations.create(r,user(1));
        TeamInvitationVO view=invitations.preview(result.getToken(),user(3));
        assertEquals("editor",view.getSpaceRole()); assertEquals("邀请团队",view.getSpaceName());
        long delta=view.getExpiresAt().getTime()-System.currentTimeMillis(); assertTrue(delta>days*86400000L-10000 && delta<=days*86400000L);
    }
    @Test void invalidRoleExpiryAndUsageAreRejected() {
        assertThrows(BusinessException.class, () -> invitations.create(request(2,null),user(1)));
        assertThrows(BusinessException.class, () -> invitations.create(request(1,0),user(1)));
        TeamInviteCreateRequest r=request(1,null); r.setSpaceRole("admin"); assertThrows(BusinessException.class, () -> invitations.create(r,user(1)));
    }
    @Test void previewDoesNotJoinAndConfirmedJoinUsesOnlyInvitationRole() {
        TeamInvitationCreatedVO c=create(null); invitations.preview(c.getToken(),user(3));
        assertEquals(0L,jdbc.queryForObject("SELECT COUNT(*) FROM space_user WHERE userId=3",Long.class));
        assertEquals(10L,invitations.join(c.getToken(),user(3)));
        assertEquals("viewer",jdbc.queryForObject("SELECT spaceRole FROM space_user WHERE userId=3",String.class));
        assertEquals(1,jdbc.queryForObject("SELECT usedCount FROM team_invitation WHERE id=?",Integer.class,c.getId()));
        assertEquals("你已经是团队成员",assertThrows(BusinessException.class,()->invitations.join(c.getToken(),user(3))).getMessage());
    }
    @Test void expiredRevokedAndExhaustedLinksHaveExplicitErrors() {
        TeamInvitationCreatedVO expired=create(null); jdbc.update("UPDATE team_invitation SET expiresAt=? WHERE id=?",new java.sql.Timestamp(0),expired.getId());
        assertEquals("邀请已过期",assertThrows(BusinessException.class,()->invitations.join(expired.getToken(),user(3))).getMessage());
        TeamInvitationCreatedVO revoked=create(null); TeamInviteManageRequest r=new TeamInviteManageRequest(); r.setSpaceId(10L);r.setId(revoked.getId());invitations.revoke(r,user(1));
        assertEquals("邀请已作废",assertThrows(BusinessException.class,()->invitations.preview(revoked.getToken(),user(3))).getMessage());
        TeamInvitationCreatedVO limited=create(1);invitations.join(limited.getToken(),user(3));
        assertEquals("邀请次数已用完",assertThrows(BusinessException.class,()->invitations.join(limited.getToken(),user(4))).getMessage());
        assertTrue(invitations.list(10L,user(1)).isEmpty());
    }
    @Test void cannotRevokeAnotherTeamsLinkOrUseDemotedIssuersLink() {
        TeamInvitationCreatedVO c=create(null); TeamInviteManageRequest r=new TeamInviteManageRequest();r.setSpaceId(11L);r.setId(c.getId());
        assertThrows(BusinessException.class,()->invitations.revoke(r,user(5)));
        jdbc.update("UPDATE space_user SET spaceRole='viewer' WHERE userId=1");
        assertThrows(BusinessException.class,()->invitations.join(c.getToken(),user(3)));
    }
    @Test void fullTeamRejectsJoinWithoutConsumingUse() {
        TeamInvitationCreatedVO c=create(1);
        for(int i=0;i<198;i++) jdbc.update("INSERT INTO space_user(id,spaceId,userId,spaceRole) VALUES(?,10,?,'viewer')",1000+i,1000+i);
        assertThrows(BusinessException.class,()->invitations.join(c.getToken(),user(3)));
        assertEquals(0,jdbc.queryForObject("SELECT usedCount FROM team_invitation WHERE id=?",Integer.class,c.getId()));
    }
    @Test void concurrentLastUseCanOnlyJoinOneUser() throws Exception {
        TeamInvitationCreatedVO c=create(1); ExecutorService pool=Executors.newFixedThreadPool(2); CountDownLatch start=new CountDownLatch(1);
        try {
            List<Future<Boolean>> results=new ArrayList<>();
            for(long id:new long[]{3,4}) results.add(pool.submit(()->{start.await();try{invitations.join(c.getToken(),user(id));return true;}catch(BusinessException e){return false;}}));
            start.countDown(); int count=0;for(Future<Boolean> result:results) if(result.get(10,TimeUnit.SECONDS))count++;
            assertEquals(1,count);assertEquals(1,jdbc.queryForObject("SELECT usedCount FROM team_invitation WHERE id=?",Integer.class,c.getId()));
        } finally {pool.shutdownNow();}
    }
    @Test void concurrentDifferentInvitesCannotExceedMemberLimit() throws Exception {
        String first=create(null).getToken(),second=create(null).getToken();
        for(int i=0;i<197;i++) jdbc.update("INSERT INTO space_user(id,spaceId,userId,spaceRole) VALUES(?,10,?,'viewer')",1000+i,1000+i);
        ExecutorService pool=Executors.newFixedThreadPool(2); CountDownLatch start=new CountDownLatch(1);
        try {
            List<Future<Boolean>> results=new ArrayList<>();
            for(long id:new long[]{3,4}) results.add(pool.submit(()->{start.await();try{invitations.join(id==3?first:second,user(id));return true;}catch(BusinessException e){return false;}}));
            start.countDown(); int count=0;for(Future<Boolean> result:results) if(result.get(10,TimeUnit.SECONDS))count++;
            assertEquals(1,count);assertEquals(200,jdbc.queryForObject("SELECT COUNT(*) FROM space_user WHERE spaceId=10",Integer.class));
            assertEquals(1,jdbc.queryForObject("SELECT SUM(usedCount) FROM team_invitation",Integer.class));
        } finally {pool.shutdownNow();}
    }
    @Test void deletedTeamAndUnknownTokensCannotBeUsed() {
        TeamInvitationCreatedVO c=create(null); jdbc.update("UPDATE space SET isDelete=1 WHERE id=10");
        assertThrows(BusinessException.class,()->invitations.join(c.getToken(),user(3)));
        assertThrows(BusinessException.class,()->invitations.preview(null,user(3)));
        byte[] bytes=new byte[32];new java.security.SecureRandom().nextBytes(bytes);
        assertThrows(BusinessException.class,()->invitations.preview(Base64.getUrlEncoder().withoutPadding().encodeToString(bytes),user(3)));
    }
    @Test void anonymousHttpCannotPreviewOrJoinAndPreviewIsNoStore() throws Exception {
        String token=create(null).getToken(); String body=json.writeValueAsString(Collections.singletonMap("token",token));
        for(String action:new String[]{"preview","join"}) {
            String result=mvc.perform(post("/spaceUser/invite/"+action).contentType("application/json").content(body)).andReturn().getResponse().getContentAsString();
            assertEquals(40100,json.readTree(result).path("code").asInt());
        }
        org.springframework.test.web.servlet.MvcResult result=mvc.perform(post("/spaceUser/invite/preview").header("X-Test-User","3").contentType("application/json").content(body)).andReturn();
        assertEquals(0,json.readTree(result.getResponse().getContentAsString()).path("code").asInt());
        assertEquals("no-store",result.getResponse().getHeader("Cache-Control"));
        assertFalse(result.getResponse().getContentAsString().contains(token));
    }
    @TestConfiguration @EnableAutoConfiguration @EnableAspectJAutoProxy
    @Import({MybatisPlusConfig.class,JsonConfig.class,TeamInvitationService.class,SpaceUserService.class,PictureAccessManager.class,TeamInvitationController.class,AuthInterceptor.class,GlobalExceptionHandler.class})
    static class TestApplication { @Bean UserService users(){return mock(UserService.class);} }
}
