package learn.gjx.tuku1.service.impl;

import learn.gjx.tuku1.model.entity.User;
import learn.gjx.tuku1.model.dto.user.UserPasswordUpdateRequest;
import learn.gjx.tuku1.model.dto.user.UserProfileUpdateRequest;
import learn.gjx.tuku1.security.LoginAttemptLimiter;
import learn.gjx.tuku1.security.UserPasswords;
import learn.gjx.tuku1.exception.BusinessException;
import org.junit.jupiter.api.*;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletRequest;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

class UserAccountPersistenceTest {
    static ConfigurableApplicationContext app;
    static UserServiceImpl service;
    @BeforeAll static void start() {
        app = new SpringApplicationBuilder(TestApplication.class).web(WebApplicationType.NONE).run(
                "--spring.config.location=optional:classpath:user-security-empty.yml", "--spring.session.store-type=none",
                "--spring.datasource.url=jdbc:h2:mem:usersecurity;MODE=MySQL;NON_KEYWORDS=USER;DB_CLOSE_DELAY=-1",
                "--spring.datasource.driver-class-name=org.h2.Driver", "--spring.datasource.username=sa", "--spring.datasource.password=",
                "--mybatis-plus.configuration.map-underscore-to-camel-case=false", "--mybatis-plus.global-config.db-config.logic-delete-field=isDelete", "--logging.level.root=WARN");
        app.getBean(JdbcTemplate.class).execute("CREATE TABLE user(id BIGINT PRIMARY KEY,userAccount VARCHAR(64) UNIQUE,userPassword VARCHAR(128),"
                + "userName VARCHAR(32),userProfile VARCHAR(128),userAvatar VARCHAR(128),userRole INT,isDelete INT DEFAULT 0,"
                + "createTime TIMESTAMP DEFAULT CURRENT_TIMESTAMP,updateTime TIMESTAMP,editTime TIMESTAMP)");
        service = app.getBean(UserServiceImpl.class);
    }
    @AfterAll static void stop() { if (app != null) app.close(); }
    @BeforeEach void clear() { app.getBean(JdbcTemplate.class).execute("DELETE FROM user"); }
    @Test void realDatabaseRegistrationProfileAndPasswordChange() {
        String original = "original-fixture";
        long id = service.userRegister("db-fixture", original, original);
        assertTrue(service.getById(id).getUserPassword().startsWith("$2a$12$"));
        MockHttpServletRequest current = new MockHttpServletRequest(), other = new MockHttpServletRequest();
        service.userLogin("db-fixture", original, current); service.userLogin("db-fixture", original, other);
        UserProfileUpdateRequest profile = new UserProfileUpdateRequest(); profile.setUserName("新昵称"); profile.setUserProfile("");
        service.updateMyProfile(profile, current); assertEquals("新昵称", service.getById(id).getUserName());
        UserPasswordUpdateRequest change = new UserPasswordUpdateRequest(); change.setOldPassword(original);
        change.setNewPassword("replacement-fixture"); change.setCheckPassword(change.getNewPassword());
        assertTrue(service.updateMyPassword(change, current));
        assertEquals(id, service.getLoginUser(current).getId());
        assertThrows(BusinessException.class, () -> service.getLoginUser(other));
        assertThrows(BusinessException.class, () -> service.userLogin("db-fixture", original, new MockHttpServletRequest()));
        assertEquals(id, service.userLogin("db-fixture", change.getNewPassword(), new MockHttpServletRequest()).getId());
    }
    @Test void realDatabaseLegacyHashUpgradePersists() {
        User user = new User(); user.setId(7L); user.setUserAccount("legacy-fixture"); user.setUserRole(0);
        user.setUserPassword(cn.hutool.crypto.digest.DigestUtil.md5Hex("tuku1_salt_2025" + "legacy-password"));
        assertTrue(service.save(user));
        service.userLogin("legacy-fixture", "legacy-password", new MockHttpServletRequest());
        assertTrue(service.getById(7L).getUserPassword().startsWith("$2a$12$"));
        assertEquals(7L, service.userLogin("legacy-fixture", "legacy-password", new MockHttpServletRequest()).getId());
    }
    private learn.gjx.tuku1.service.UserAdministrationService admin() {
        return app.getBean(learn.gjx.tuku1.service.UserAdministrationService.class);
    }
    private void account(long id, int role) {
        User u = new User(); u.setId(id); u.setUserAccount("admin-fixture-" + id); u.setUserName("fixture");
        u.setUserRole(role); u.setUserPassword(UserPasswords.hash(java.util.UUID.randomUUID().toString()));
        assertTrue(service.save(u));
    }
    private learn.gjx.tuku1.model.dto.user.UserUpdateRequest role(long id, int value) {
        learn.gjx.tuku1.model.dto.user.UserUpdateRequest r = new learn.gjx.tuku1.model.dto.user.UserUpdateRequest();
        r.setId(id); r.setUserRole(value); return r;
    }
    @Test void cannotDeleteSelfWithAnotherAdminPresent() {
        account(1, 1); account(2, 1);
        assertThrows(BusinessException.class, () -> admin().delete(1L, 1L)); assertNotNull(service.getById(1L));
    }
    @Test void lastAdministratorCannotBeDeletedOrDemoted() {
        account(1, 1);
        assertThrows(BusinessException.class, () -> admin().delete(1L, 1L));
        assertThrows(BusinessException.class, () -> admin().update(role(1, 0), 1L));
        assertEquals(1, service.getById(1L).getUserRole());
    }
    @Test void administratorCanDeleteAnotherOrdinaryUser() {
        account(1, 1); account(2, 0); assertTrue(admin().delete(2L, 1L)); assertTrue(service.getById(2L) == null);
    }
    @Test void administratorCannotChangeOwnRole() {
        account(1, 1); account(2, 1);
        assertThrows(BusinessException.class, () -> admin().update(role(1, 0), 1L));
    }
    @Test void roleUpdatesAllowOnlyKnownRolesAndRecheckActor() {
        account(1, 1); account(2, 0);
        assertThrows(BusinessException.class, () -> admin().update(role(1, 0), 2L));
        assertThrows(BusinessException.class, () -> admin().update(role(2, 99), 1L));
        assertTrue(admin().update(role(2, 1), 1L)); assertEquals(1, service.getById(2L).getUserRole());
        assertTrue(admin().update(role(2, 0), 1L)); assertEquals(0, service.getById(2L).getUserRole());
    }
    @Test void resetReturnsOnlyNewRandomCredentialAndRevokesExistingSession() {
        account(1, 1); account(2, 0);
        User before = service.getById(2L);
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.getSession().setAttribute(learn.gjx.tuku1.constant.UserConstant.USER_LOGIN_STATE, service.userLogin(before));
        request.getSession().setAttribute(learn.gjx.tuku1.security.UserSessionSecurity.VERSION,
                learn.gjx.tuku1.security.UserSessionSecurity.version(before));
        learn.gjx.tuku1.model.vo.UserPasswordResetVO result = admin().resetPassword(2L, 1L);
        String temporary = result.getTemporaryPassword();
        assertTrue(temporary.length() >= 32);
        assertFalse(result.toString().contains(temporary));
        assertTrue(UserPasswords.matches(temporary, service.getById(2L).getUserPassword()));
        assertThrows(BusinessException.class, () -> service.getLoginUser(request));
        assertEquals(2L, service.userLogin(before.getUserAccount(), temporary, new MockHttpServletRequest()).getId());
        assertFalse(admin().resetPassword(2L, 1L).getTemporaryPassword().equals(temporary));
        assertFalse(UserPasswords.matches(temporary, service.getById(2L).getUserPassword()));
    }
    @Test void nonAdminCannotResetOrDelete() {
        account(1, 1); account(2, 0);
        assertThrows(BusinessException.class, () -> admin().resetPassword(1L, 2L));
        assertThrows(BusinessException.class, () -> admin().delete(1L, 2L));
    }
    @Test void concurrentCrossDemotionRetainsAnAdministrator() throws Exception { concurrentAdminChanges(false); }
    @Test void concurrentCrossDeletionRetainsAnAdministrator() throws Exception { concurrentAdminChanges(true); }
    private void concurrentAdminChanges(boolean deletion) throws Exception {
        account(1, 1); account(2, 1);
        java.util.concurrent.ExecutorService pool = java.util.concurrent.Executors.newFixedThreadPool(2);
        java.util.concurrent.CountDownLatch start = new java.util.concurrent.CountDownLatch(1);
        java.util.List<java.util.concurrent.Future<Boolean>> jobs = new java.util.ArrayList<>();
        try {
            for (long actor = 1; actor <= 2; actor++) {
                final long caller = actor, target = 3 - actor;
                jobs.add(pool.submit(() -> { start.await();
                    try { return deletion ? admin().delete(target, caller) : admin().update(role(target, 0), caller); }
                    catch (BusinessException | org.springframework.dao.DataAccessException expected) { return false; }
                }));
            }
            start.countDown(); int succeeded = 0;
            for (java.util.concurrent.Future<Boolean> job : jobs) if (job.get(10, java.util.concurrent.TimeUnit.SECONDS)) succeeded++;
            assertEquals(1, succeeded);
            assertEquals(1L, app.getBean(JdbcTemplate.class).queryForObject("SELECT COUNT(*) FROM user WHERE userRole=1 AND isDelete=0", Long.class));
        } finally { pool.shutdownNow(); }
    }

    @Configuration
    @EnableAutoConfiguration
    @MapperScan("learn.gjx.tuku1.mapper")
    @Import({UserServiceImpl.class, learn.gjx.tuku1.service.UserAdministrationService.class})
    static class TestApplication {
        @Bean LoginAttemptLimiter loginAttemptLimiter() { return mock(LoginAttemptLimiter.class); }
    }
}
