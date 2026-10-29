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
                "--mybatis-plus.configuration.map-underscore-to-camel-case=false", "--logging.level.root=WARN");
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
    @Configuration
    @EnableAutoConfiguration
    @MapperScan("learn.gjx.tuku1.mapper")
    @Import(UserServiceImpl.class)
    static class TestApplication {
        @Bean LoginAttemptLimiter loginAttemptLimiter() { return mock(LoginAttemptLimiter.class); }
    }
}
