package learn.gjx.tuku1.service.impl;

import learn.gjx.tuku1.constant.UserConstant;
import learn.gjx.tuku1.exception.BusinessException;
import learn.gjx.tuku1.model.entity.User;
import learn.gjx.tuku1.model.vo.LoginUserVO;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpSession;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class UserSessionTest {

    private UserServiceImpl service;
    private MockHttpServletRequest request;

    @BeforeEach
    void setUp() {
        service = spy(new UserServiceImpl());
        request = new MockHttpServletRequest();
    }

    @Test
    void anonymousLookupAndLogoutDoNotCreateSession() {
        assertThrows(BusinessException.class, () -> service.getLoginUser(request));
        assertThrows(BusinessException.class, () -> service.userLogout(request));
        assertNull(request.getSession(false));
    }

    @Test
    void loginRotatesExistingIdAndStoresSanitizedUser() {
        User user = new User();
        user.setId(1L);
        user.setUserAccount("testuser");
        user.setUserRole(0);
        doReturn(user).when(service).getOne(any());
        String oldId = request.getSession().getId();

        LoginUserVO result = service.userLogin("testuser", "password123", request);

        assertNotEquals(oldId, request.getSession().getId());
        assertSame(result, request.getSession().getAttribute(UserConstant.USER_LOGIN_STATE));
    }

    @Test
    void failedLoginDoesNotCreateSession() {
        doReturn(null).when(service).getOne(any());
        assertThrows(BusinessException.class,
                () -> service.userLogin("testuser", "password123", request));
        assertNull(request.getSession(false));
    }

    @Test
    void logoutInvalidatesEntireSession() {
        MockHttpSession session = (MockHttpSession) request.getSession();
        session.setAttribute(UserConstant.USER_LOGIN_STATE, new LoginUserVO());
        assertTrue(service.userLogout(request));
        assertTrue(session.isInvalid());
    }

    @Test
    void lookupStillLoadsLatestUserRoleFromDatabase() {
        LoginUserVO snapshot = new LoginUserVO();
        snapshot.setId(1L);
        snapshot.setUserRole(1);
        request.getSession().setAttribute(UserConstant.USER_LOGIN_STATE, snapshot);
        User current = new User();
        current.setId(1L);
        current.setUserRole(0);
        doReturn(current).when(service).getById(1L);
        assertEquals(0, service.getLoginUser(request).getUserRole());
        verify(service).getById(1L);
    }
}
