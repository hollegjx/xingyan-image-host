package learn.gjx.tuku1.manager;

import com.qcloud.cos.COSClient;
import com.qcloud.cos.model.*;
import learn.gjx.tuku1.config.CosClientConfig;
import learn.gjx.tuku1.config.PrivatePictureStorageProperties;
import learn.gjx.tuku1.exception.BusinessException;
import learn.gjx.tuku1.model.entity.Picture;
import org.apache.http.client.methods.HttpGet;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class PrivatePictureFileManagerTest {
    private PrivatePictureFileManager manager;
    private PrivatePictureStorageProperties properties;
    private COSClient cos;
    private Picture picture;
    private final String root = "https://private-test-1250000000.cos.ap-test.myqcloud.com";

    @BeforeEach
    void setup() {
        manager = new PrivatePictureFileManager();
        cos = mock(COSClient.class);
        properties = new PrivatePictureStorageProperties();
        properties.setEnabled(true); properties.setBucket("private-test-1250000000");
        CosClientConfig config = new CosClientConfig();
        config.setBucket("public-test-1250000000"); config.setRegion("ap-test");
        ReflectionTestUtils.setField(manager, "properties", properties);
        ReflectionTestUtils.setField(manager, "cosClientConfig", config);
        ReflectionTestUtils.setField(manager, "cosClient", cos);
        picture = new Picture(); picture.setId(1L); picture.setSpaceId(101L);
        picture.setUrl(root + "/space/101/display.webp");
        picture.setOriginalUrl(root + "/space/101/original.jpg");
        picture.setThumbnailUrl(root + "/space/101/thumb.webp");
    }

    @Test
    void requiresSeparateBucketAndExplicitEnable() {
        properties.setEnabled(false);
        assertThrows(BusinessException.class, () -> manager.validatedKey(101L, picture.getUrl()));
        properties.setEnabled(true); properties.setBucket("public-test-1250000000");
        assertThrows(BusinessException.class, () -> manager.validatedKey(101L, picture.getUrl()));
        properties.setBucket(null);
        assertThrows(BusinessException.class, () -> manager.validatedKey(101L, picture.getUrl()));
        verifyNoInteractions(cos);
    }

    @Test
    void acceptsOnlyCanonicalKeysInThePicturesOwnSpace() {
        assertEquals("space/101/display.webp", manager.validatedKey(101L, picture.getUrl()));
        for (String path : new String[]{"/space/102/a.jpg", "/public/101/a.jpg", "/space/101/../a.jpg",
                "/space/101/%2e%2e/a.jpg", "/space/101/a%2f.jpg", "/space/101/a.svg", "/space/101/a.html",
                "/space/101/a.jpg?imageMogr2/format/html", "/space/101/a.jpg#fragment", "/space//101/a.jpg"}) {
            assertThrows(BusinessException.class, () -> manager.validatedKey(101L, root + path), path);
        }
        verifyNoInteractions(cos);
    }

    @Test
    void rejectsForeignHostsPortsCredentialsAndNonHttps() {
        for (String url : new String[]{picture.getUrl().replace("https:", "http:"),
                picture.getUrl().replace("private-test", "public-test"),
                picture.getUrl().replace("myqcloud.com", "myqcloud.com.evil.test"),
                picture.getUrl().replace("https://", "https://user@"),
                picture.getUrl().replace("myqcloud.com", "myqcloud.com:443"),
                "file:///space/101/display.webp"}) {
            assertThrows(BusinessException.class, () -> manager.validatedKey(101L, url));
        }
    }

    @Test
    void deletionOnlyAcceptsOwnSpaceAndTreatsMissingObjectAsAlreadyCleaned() {
        assertThrows(BusinessException.class, () -> manager.delete(102L, picture.getUrl()));
        verifyNoInteractions(cos);
        com.qcloud.cos.exception.CosServiceException missing = new com.qcloud.cos.exception.CosServiceException("missing");
        missing.setStatusCode(404); missing.setErrorCode("NoSuchKey");
        doThrow(missing).when(cos).deleteObject(properties.getBucket(), "space/101/display.webp");
        assertDoesNotThrow(() -> manager.delete(101L, picture.getUrl()));
        missing.setErrorCode("NoSuchBucket");
        assertThrows(com.qcloud.cos.exception.CosServiceException.class, () -> manager.delete(101L, picture.getUrl()));
    }

    @Test
    void invalidVariantAndAbsentThumbnailNeverFallBackToPublicFiles() {
        assertThrows(BusinessException.class, () -> manager.write(picture, "../original", new MockHttpServletResponse()));
        picture.setThumbnailUrl(null);
        assertThrows(BusinessException.class, () -> manager.write(picture, "thumbnail", new MockHttpServletResponse()));
        verifyNoInteractions(cos);
    }

    @Test
    void streamsAndClosesObjectWithoutDisclosingStorageUrl() throws Exception {
        AtomicBoolean closed = new AtomicBoolean();
        COSObject object = new COSObject();
        object.setObjectContent(new COSObjectInputStream(new ByteArrayInputStream(new byte[]{1, 2}) {
            @Override public void close() throws IOException { closed.set(true); super.close(); }
        }, new HttpGet()));
        when(cos.getObject(any(GetObjectRequest.class))).thenReturn(object);
        MockHttpServletResponse response = new MockHttpServletResponse();
        manager.write(picture, "original", response);
        assertArrayEquals(new byte[]{1, 2}, response.getContentAsByteArray());
        assertEquals("image/jpeg", response.getContentType());
        assertEquals("private, no-store", response.getHeader("Cache-Control"));
        assertEquals("Cookie", response.getHeader("Vary"));
        assertNull(response.getHeader("Location"));
        assertTrue(closed.get());
        org.mockito.ArgumentCaptor<GetObjectRequest> captured = org.mockito.ArgumentCaptor.forClass(GetObjectRequest.class);
        verify(cos).getObject(captured.capture());
        assertEquals(properties.getBucket(), captured.getValue().getBucketName());
        assertEquals("space/101/original.jpg", captured.getValue().getKey());
    }

    @Test
    void cloudFailureDoesNotExposeSdkMessagesOrUsePublicFallback() {
        when(cos.getObject(any(GetObjectRequest.class))).thenThrow(new IllegalStateException("secret/internal/url"));
        BusinessException error = assertThrows(BusinessException.class,
                () -> manager.write(picture, "display", new MockHttpServletResponse()));
        assertEquals("私有图片读取失败", error.getMessage());
        verify(cos, times(1)).getObject(any(GetObjectRequest.class));
    }

    @Test
    void streamingFailureClosesObjectAndRemovesPartialUncommittedBody() {
        AtomicBoolean closed = new AtomicBoolean();
        COSObject object = new COSObject();
        object.setObjectContent(new COSObjectInputStream(new java.io.InputStream() {
            @Override public int read() throws IOException { throw new IOException("secret/internal/url"); }
            @Override public void close() { closed.set(true); }
        }, new HttpGet()));
        when(cos.getObject(any(GetObjectRequest.class))).thenReturn(object);
        MockHttpServletResponse response = new MockHttpServletResponse();
        BusinessException error = assertThrows(BusinessException.class, () -> manager.write(picture, "display", response));
        assertEquals("私有图片读取失败", error.getMessage());
        assertTrue(closed.get());
        assertNull(response.getContentType());
        assertEquals(0, response.getContentAsByteArray().length);
        assertEquals("private, no-store", response.getHeader("Cache-Control"));
    }
}
