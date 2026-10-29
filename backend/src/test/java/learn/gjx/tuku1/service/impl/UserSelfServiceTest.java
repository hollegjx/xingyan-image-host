package learn.gjx.tuku1.service.impl;

import learn.gjx.tuku1.constant.UserConstant;
import learn.gjx.tuku1.exception.BusinessException;
import learn.gjx.tuku1.model.dto.user.*;
import learn.gjx.tuku1.model.entity.User;
import learn.gjx.tuku1.security.*;
import org.junit.jupiter.api.*;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.test.util.ReflectionTestUtils;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class UserSelfServiceTest {
    UserServiceImpl service;
    User user;
    final String oldPassword = "old-fixture-password";
    @BeforeEach void setup() {
        service = spy(new UserServiceImpl());
        ReflectionTestUtils.setField(service, "loginAttemptLimiter", mock(LoginAttemptLimiter.class));
        user = new User(); user.setId(5L); user.setUserRole(0); user.setUserAccount("self-fixture");
        user.setUserPassword(UserPasswords.hash(oldPassword));
        doReturn(user).when(service).getById(5L);
    }
    MockHttpServletRequest session() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.getSession().setAttribute(UserConstant.USER_LOGIN_STATE, service.userLogin(user));
        request.getSession().setAttribute(UserSessionSecurity.VERSION, UserSessionSecurity.version(user));
        return request;
    }
    UserPasswordUpdateRequest update() {
        UserPasswordUpdateRequest r = new UserPasswordUpdateRequest();
        r.setOldPassword(oldPassword); r.setNewPassword("new-fixture-password"); r.setCheckPassword(r.getNewPassword()); return r;
    }
    @Test void passwordChangeKeepsCallerAndRevokesOtherSessions() {
        MockHttpServletRequest current = session(), other = session();
        String originalId = current.getSession().getId();
        doReturn(true).when(service).update(any(User.class), any());
        assertTrue(service.updateMyPassword(update(), current));
        assertNotEquals(originalId, current.getSession().getId());
        assertTrue(UserPasswords.matches(update().getNewPassword(), user.getUserPassword()));
        assertFalse(UserPasswords.matches(oldPassword, user.getUserPassword()));
        assertEquals(5L, service.getLoginUser(current).getId());
        assertThrows(BusinessException.class, () -> service.getLoginUser(other));
    }
    @Test void wrongOldPasswordNeverWritesOrRevokes() {
        MockHttpServletRequest current = session(), other = session();
        UserPasswordUpdateRequest r = update(); r.setOldPassword("wrong-fixture");
        assertThrows(BusinessException.class, () -> service.updateMyPassword(r, current));
        verify(service, never()).update(any(User.class), any());
        assertEquals(5L, service.getLoginUser(other).getId());
    }
    @Test void missingVersionRequiresLoginAgain() {
        MockHttpServletRequest current = session(); current.getSession().removeAttribute(UserSessionSecurity.VERSION);
        assertThrows(BusinessException.class, () -> service.getLoginUser(current));
    }
    @Test void mismatchingConfirmationAndShortPasswordAreRejected() {
        UserPasswordUpdateRequest r = update(); r.setCheckPassword("different-fixture");
        assertThrows(BusinessException.class, () -> service.updateMyPassword(r, session()));
        r.setNewPassword("short"); r.setCheckPassword("short");
        assertThrows(BusinessException.class, () -> service.updateMyPassword(r, session()));
        verify(service, never()).update(any(User.class), any());
    }
    @Test void concurrentPasswordChangeDoesNotRefreshSession() {
        MockHttpServletRequest current = session(); String version = (String) current.getSession().getAttribute(UserSessionSecurity.VERSION);
        doReturn(false).when(service).update(any(User.class), any());
        assertThrows(BusinessException.class, () -> service.updateMyPassword(update(), current));
        assertEquals(version, current.getSession().getAttribute(UserSessionSecurity.VERSION));
    }
    @Test void profileValidatesDatabaseLengthsAndAllowsClearingBio() {
        UserProfileUpdateRequest r = new UserProfileUpdateRequest(); r.setUserName(" ");
        assertThrows(BusinessException.class, () -> service.updateMyProfile(r, session()));
        r.setUserName("name"); r.setUserProfile(String.join("", java.util.Collections.nCopies(129, "x")));
        assertThrows(BusinessException.class, () -> service.updateMyProfile(r, session()));
        r.setUserProfile(""); doReturn(true).when(service).updateById(any(User.class));
        assertEquals("", service.updateMyProfile(r, session()).getUserProfile());
    }
}
