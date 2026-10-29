package learn.gjx.tuku1.service.impl;

import cn.hutool.crypto.digest.DigestUtil;
import learn.gjx.tuku1.model.entity.User;
import learn.gjx.tuku1.model.dto.user.UserAddRequest;
import learn.gjx.tuku1.exception.BusinessException;
import learn.gjx.tuku1.security.UserPasswords;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.any;

class UserPasswordTest {
    private final String password = "fixture-password-73";
    private UserServiceImpl service = spy(new UserServiceImpl());

    @Test void registrationCreatesBcryptAndCanLogin() {
        AtomicReference<User> saved = new AtomicReference<>();
        doReturn(0L).when(service).count(any());
        doAnswer(i -> { User u = i.getArgument(0); u.setId(12L); saved.set(u); return true; }).when(service).save(any(User.class));
        assertEquals(12L, service.userRegister("fixture-account", password, password));
        assertTrue(saved.get().getUserPassword().startsWith("$2a$12$"));
        assertNotEquals(service.getEncrptPassword(password), saved.get().getUserPassword());
        doAnswer(i -> saved.get()).when(service).getOne(any());
        assertEquals(12L, service.userLogin("fixture-account", password, new MockHttpServletRequest()).getId());
    }

    @Test void legacyLoginUpgradesAndSecondLoginUsesBcrypt() {
        User u = legacy(password);
        doReturn(u).when(service).getOne(any());
        doReturn(true).when(service).update(any(User.class), any());
        service.userLogin("fixture-account", password, new MockHttpServletRequest());
        assertTrue(u.getUserPassword().startsWith("$2a$12$"));
        service.userLogin("fixture-account", password, new MockHttpServletRequest());
        verify(service, times(1)).update(any(User.class), any());
    }

    @Test void wrongPasswordsDoNotUpgradeOrCreateSession() {
        for (String hash : new String[]{legacy(password).getUserPassword(), service.getEncrptPassword(password)}) {
            User u = new User(); u.setId(1L); u.setUserPassword(hash);
            doReturn(u).when(service).getOne(any());
            MockHttpServletRequest request = new MockHttpServletRequest();
            assertThrows(BusinessException.class, () -> service.userLogin("fixture-account", "wrong-fixture", request));
            assertNull(request.getSession(false));
        }
        verify(service, never()).update(any(User.class), any());
    }

    @Test void adminMustSupplyPassword() {
        UserAddRequest request = new UserAddRequest(); request.setUserAccount("fixture-account");
        assertThrows(BusinessException.class, () -> service.addUser(request));
        verify(service, never()).save(any(User.class));
    }

    @Test void longLegacyPasswordDoesNotLoseSuffixDuringUpgrade() {
        String longPassword = String.join("", java.util.Collections.nCopies(80, "x"));
        User u = legacy(longPassword); doReturn(u).when(service).getOne(any());
        doReturn(true).when(service).update(any(User.class), any());
        service.userLogin("fixture-account", longPassword, new MockHttpServletRequest());
        assertTrue(u.getUserPassword().length() <= 128);
        assertTrue(UserPasswords.matches(longPassword, u.getUserPassword()));
        assertFalse(UserPasswords.matches(longPassword + "y", u.getUserPassword()));
    }

    @Test void concurrentPasswordChangeCannotBeOverwrittenByLegacyUpgrade() {
        doReturn(legacy(password)).when(service).getOne(any());
        doReturn(false).when(service).update(any(User.class), any());
        User changed = new User(); changed.setUserPassword(service.getEncrptPassword("different-fixture"));
        doReturn(changed).when(service).getById(1L);
        MockHttpServletRequest request = new MockHttpServletRequest();
        assertThrows(BusinessException.class, () -> service.userLogin("fixture-account", password, request));
        assertNull(request.getSession(false));
    }

    @Test void newPasswordRulesRejectEmptyShortAndOverlongUtf8() {
        for (String value : new String[]{"", "short", String.join("", java.util.Collections.nCopies(25, "密"))})
            assertThrows(BusinessException.class, () -> UserPasswords.hash(value));
    }

    private User legacy(String raw) {
        User u = new User(); u.setId(1L); u.setUserRole(0); u.setUserAccount("fixture-account");
        u.setUserPassword(DigestUtil.md5Hex("tuku1_salt_2025" + raw)); return u;
    }
}
