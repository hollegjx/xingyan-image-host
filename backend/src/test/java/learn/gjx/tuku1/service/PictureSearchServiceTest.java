package learn.gjx.tuku1.service;

import learn.gjx.tuku1.api.imagesearch.*;
import learn.gjx.tuku1.config.*;
import learn.gjx.tuku1.exception.*;
import learn.gjx.tuku1.manager.space.PictureAccessManager;
import learn.gjx.tuku1.mapper.*;
import learn.gjx.tuku1.model.dto.picture.SearchPictureByPictureRequest;
import learn.gjx.tuku1.model.entity.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.Collections;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class PictureSearchServiceTest {
    PictureMapper pictures;
    SpaceMapper spaces;
    ImageSearchApiFacade provider;
    ImageSearchProperties settings;
    PictureSearchService service;
    Picture picture;
    User user;
    SearchPictureByPictureRequest request;
    String root = "https://public-123.cos.ap-test.myqcloud.com/public/1/";

    @BeforeEach void setup() {
        pictures = mock(PictureMapper.class);
        spaces = mock(SpaceMapper.class);
        provider = mock(ImageSearchApiFacade.class);
        PictureAccessManager access = new PictureAccessManager();
        ReflectionTestUtils.setField(access, "spaceMapper", spaces);
        settings = new ImageSearchProperties();
        settings.setEnabled(true);
        CosClientConfig cos = new CosClientConfig();
        cos.setBucket("public-123"); cos.setRegion("ap-test");
        service = new PictureSearchService(pictures, access, provider, settings, cos);
        user = new User(); user.setId(1L); user.setUserRole(0);
        picture = new Picture(); picture.setId(2L); picture.setUserId(1L);
        picture.setUploadStatus(1); picture.setReviewStatus(1); picture.setIsDelete(0);
        picture.setOriginalUrl(root + "original.png"); picture.setUrl(root + "display.webp");
        when(pictures.selectById(2L)).thenReturn(picture);
        when(provider.search(anyString())).thenReturn(Collections.emptyList());
        request = new SearchPictureByPictureRequest();
        request.setPictureId(2L); request.setExternalSearchConsent(true);
    }

    @Test void usesStoredOriginalNotCallerUrlAndCooldownApplies() {
        assertTrue(service.search(request, user).isEmpty());
        verify(provider).search(root + "original.png");
        assertCode(42900, () -> service.search(request, user));
        verifyNoMoreInteractions(provider);
    }

    @Test void legacyRecordFallsBackToPublicDisplayUrl() {
        picture.setOriginalUrl(null);
        service.search(request, user);
        verify(provider).search(root + "display.webp");
    }

    @Test void identityConsentAndSwitchFailBeforeProvider() {
        assertCode(40100, () -> service.search(request, null));
        request.setExternalSearchConsent(null);
        assertCode(40000, () -> service.search(request, user));
        request.setExternalSearchConsent(false);
        assertCode(40000, () -> service.search(request, user));
        request.setExternalSearchConsent(true);
        settings.setEnabled(false);
        assertCode(50300, () -> service.search(request, user));
        verifyNoInteractions(provider);
    }

    @Test void rejectsPrivateForOwnerAndAdminAsWellAsOtherUsers() {
        picture.setSpaceId(7L);
        Space space = new Space(); space.setId(7L); space.setUserId(1L);
        when(spaces.selectById(7L)).thenReturn(space);
        assertCode(40101, () -> service.search(request, user));
        user.setId(8L); user.setUserRole(1);
        assertCode(40101, () -> service.search(request, user));
        verifyNoInteractions(provider);
    }

    @Test void draftsUnreviewedRejectedMissingAndDeletedNeverLeaveApp() {
        picture.setUploadStatus(0);
        assertCode(40101, () -> service.search(request, user));
        picture.setUploadStatus(1); picture.setReviewStatus(0);
        assertCode(40101, () -> service.search(request, user));
        picture.setReviewStatus(2);
        assertCode(40101, () -> service.search(request, user));
        picture.setIsDelete(1);
        assertCode(40400, () -> service.search(request, user));
        when(pictures.selectById(2L)).thenReturn(null);
        assertCode(40400, () -> service.search(request, user));
        verifyNoInteractions(provider);
    }

    @ParameterizedTest
    @ValueSource(strings = {"http://public-123.cos.ap-test.myqcloud.com/public/1/a.jpg",
            "https://private-123.cos.ap-test.myqcloud.com/public/1/a.jpg",
            "https://public-123.cos.ap-test.myqcloud.com/space/7/a.jpg",
            "https://public-123.cos.ap-test.myqcloud.com/public/1/a.jpg?q-signature=secret",
            "https://public-123.cos.ap-test.myqcloud.com/public/1/../a.jpg",
            "https://public-123.cos.ap-test.myqcloud.com/public/1/%2e%2e/secret",
            "https://127.0.0.1/private", "https://evil.example/public/1/a.png", "/api/picture/private/file?id=2"})
    void disallowedSourcesAreNotTransmitted(String source) {
        picture.setOriginalUrl(source);
        assertCode(40000, () -> service.search(request, user));
        verifyNoInteractions(provider);
    }

    @Test void invalidIdsFailBeforeDatabase() {
        assertCode(40000, () -> service.search(null, user));
        request.setPictureId(null);
        assertCode(40000, () -> service.search(request, user));
        request.setPictureId(0L);
        assertCode(40000, () -> service.search(request, user));
        verifyNoInteractions(pictures, provider);
    }

    @Test void limitsConcurrencyAndReleasesSlotOnFailure() throws Exception {
        CountDownLatch entered = new CountDownLatch(2);
        CountDownLatch release = new CountDownLatch(1);
        when(provider.search(anyString())).thenAnswer(call -> {
            entered.countDown();
            if (!release.await(5, TimeUnit.SECONDS)) throw new AssertionError("Test release timed out");
            throw new BusinessException(ErrorCode.SERVICE_UNAVAILABLE);
        });
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            User second = new User(); second.setId(2L); second.setUserRole(0);
            User third = new User(); third.setId(3L); third.setUserRole(0);
            Future<?> first = executor.submit(() -> assertCode(50300, () -> service.search(request, user)));
            Future<?> other = executor.submit(() -> assertCode(50300, () -> service.search(request, second)));
            assertTrue(entered.await(3, TimeUnit.SECONDS));
            assertCode(42900, () -> service.search(request, third));
            release.countDown(); first.get(3, TimeUnit.SECONDS); other.get(3, TimeUnit.SECONDS);
            User fourth = new User(); fourth.setId(4L); fourth.setUserRole(0);
            assertCode(50300, () -> service.search(request, fourth));
            verify(provider, times(3)).search(anyString());
        } finally { release.countDown(); executor.shutdownNow(); }
    }

    private void assertCode(int code, Runnable action) {
        assertEquals(code, assertThrows(BusinessException.class, action::run).getCode());
    }
}
