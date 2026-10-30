package learn.gjx.tuku1.manager;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.qcloud.cos.COSClient;
import com.qcloud.cos.model.*;
import com.qcloud.cos.model.ciModel.persistence.*;
import learn.gjx.tuku1.config.CosClientConfig;
import learn.gjx.tuku1.manager.space.PictureAccessManager;
import learn.gjx.tuku1.manager.space.PrivatePictureWriteManager;
import learn.gjx.tuku1.model.dto.file.PictureUploadResult;
import learn.gjx.tuku1.model.entity.*;
import learn.gjx.tuku1.model.vo.PictureVO;
import learn.gjx.tuku1.service.impl.PictureServiceImpl;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.LoggerFactory;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionTemplate;
import java.nio.file.*;
import java.io.*;
import java.util.Collections;
import java.util.function.Supplier;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class UploadTimingBoundaryTest {
    @TempDir Path directory;
    private final Logger logger = (Logger) LoggerFactory.getLogger(UploadTiming.class);
    private ListAppender<ILoggingEvent> events;
    private Level previous;

    @BeforeEach void capture() {
        previous = logger.getLevel(); logger.setLevel(Level.INFO);
        events = new ListAppender<>(); events.start(); logger.addAppender(events);
    }
    @AfterEach void restore() {
        logger.detachAppender(events); events.stop(); logger.setLevel(previous);
    }

    private void assertTiming(int index, String stage, long bytes) {
        ILoggingEvent event = events.list.get(index);
        assertTrue(event.getFormattedMessage().matches(
                "upload_timing stage=" + stage + " elapsed_ms=[0-9]+ bytes=" + bytes));
        assertNull(event.getThrowableProxy());
    }

    @ParameterizedTest @ValueSource(ints = {0, 1, 2, 3, 4})
    void everyPutLogsOnceAndPreservesResultAndFailure(int variant) throws Exception {
        COSClient client = mock(COSClient.class);
        CosManager manager = new CosManager();
        ReflectionTestUtils.setField(manager, "cosClient", client);
        CosClientConfig config = new CosClientConfig(); config.setBucket("test-only-bucket");
        ReflectionTestUtils.setField(manager, "cosClientConfig", config);
        File file = Files.write(directory.resolve("not-for-logs.jpg"), new byte[123]).toFile();
        PicOperations ops = new PicOperations(); ops.setRules(Collections.emptyList());
        ObjectMetadata metadata = new ObjectMetadata(); metadata.setContentLength(123);
        Supplier<PutObjectResult> upload = () -> {
            switch (variant) {
                case 0: return manager.putObject("hidden/key.jpg", file);
                case 1: return manager.putPictureObject("hidden/key.jpg", file);
                case 2: return manager.putPictureObject("hidden/key.jpg", file, ops);
                case 3: return manager.putPrivatePictureObject("hidden-bucket", "hidden/key.jpg", file, ops);
                default: return manager.putObject("hidden/key.jpg", new ByteArrayInputStream(new byte[123]), metadata);
            }
        };
        PutObjectResult result = new PutObjectResult();
        when(client.putObject(any(PutObjectRequest.class))).thenReturn(result);
        assertSame(result, upload.get());
        assertEquals(1, events.list.size()); assertTiming(0, "COS_UPLOAD", 123);
        IllegalStateException error = new IllegalStateException("private SDK detail must not enter timing log");
        when(client.putObject(any(PutObjectRequest.class))).thenThrow(error);
        assertSame(error, assertThrows(IllegalStateException.class, upload::get));
        assertEquals(2, events.list.size()); assertTiming(1, "COS_UPLOAD", 123);
        verify(client, times(2)).putObject(any(PutObjectRequest.class));
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void privateAclIsSeparateAndFailureStillPropagates(boolean fail) throws Exception {
        COSClient client = mock(COSClient.class); CosManager manager = new CosManager();
        ReflectionTestUtils.setField(manager, "cosClient", client);
        PicOperations ops = new PicOperations(); PicOperations.Rule rule = new PicOperations.Rule();
        rule.setBucket("private-test"); rule.setFileId("/space/1/display.webp");
        ops.setRules(Collections.singletonList(rule));
        CIObject object = new CIObject(); object.setKey("space/1/display.webp"); object.setSize(50);
        ProcessResults processed = new ProcessResults(); processed.setObjectList(Collections.singletonList(object));
        CIUploadResult ci = new CIUploadResult(); ci.setProcessResults(processed);
        PutObjectResult result = new PutObjectResult(); result.setCiUploadResult(ci);
        when(client.putObject(any(PutObjectRequest.class))).thenReturn(result);
        IllegalStateException error = new IllegalStateException("ACL test failure");
        if (fail) doThrow(error).when(client).setObjectAcl(anyString(), anyString(), any(CannedAccessControlList.class));
        File file = Files.write(directory.resolve("original.jpg"), new byte[100]).toFile();
        if (fail) assertSame(error, assertThrows(IllegalStateException.class,
                () -> manager.putPrivatePictureObject("private-test", "space/1/a.jpg", file, ops)));
        else assertSame(result, manager.putPrivatePictureObject("private-test", "space/1/a.jpg", file, ops));
        assertEquals(2, events.list.size()); assertTiming(0, "COS_UPLOAD", 100); assertTiming(1, "PRIVATE_ACL", 50);
        verify(client).setObjectAcl("private-test", "space/1/display.webp", CannedAccessControlList.Private);
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void publicPersistenceLogsAndPreservesRollback(boolean fail) {
        PictureServiceImpl service = new PictureServiceImpl();
        TransactionTemplate transaction = mock(TransactionTemplate.class);
        PictureFileCleanupManager cleanup = mock(PictureFileCleanupManager.class);
        ReflectionTestUtils.setField(service, "transactionTemplate", transaction);
        ReflectionTestUtils.setField(service, "pictureFileCleanupManager", cleanup);
        PictureUploadResult upload = new PictureUploadResult(); upload.setPicSize(100L); upload.setUrl("https://example.invalid/display.webp");
        PictureVO vo = new PictureVO(); vo.setId(123L);
        IllegalStateException error = new IllegalStateException("database test failure");
        if (fail) when(transaction.execute(any())).thenThrow(error);
        else when(transaction.execute(any())).thenReturn(vo);
        Supplier<PictureVO> save = () -> ReflectionTestUtils.invokeMethod(service, "persistUploadedPicture",
                upload, null, null, null, null, null, null, null, false, new User(), null);
        if (fail) {
            assertSame(error, assertThrows(IllegalStateException.class, save::get));
            verify(cleanup).deleteQuietly(upload.getUrl(), "rollback-upload");
        } else { assertSame(vo, save.get()); verifyNoInteractions(cleanup); }
        assertEquals(1, events.list.size()); assertTiming(0, "DATABASE", 100);
    }

    @ParameterizedTest @ValueSource(booleans = {false, true})
    void privatePersistenceLogsWithoutChangingReturnedPrivateUrls(boolean fail) {
        PictureServiceImpl service = new PictureServiceImpl();
        PrivatePictureWriteManager writes = mock(PrivatePictureWriteManager.class);
        PictureAccessManager access = mock(PictureAccessManager.class);
        ReflectionTestUtils.setField(service, "privatePictureWriteManager", writes);
        ReflectionTestUtils.setField(service, "pictureAccessManager", access);
        PictureUploadResult upload = new PictureUploadResult(); upload.setPicSize(100L);
        upload.setUrl("https://example.invalid/display.webp");
        Picture saved = new Picture(); saved.setId(123L); saved.setSpaceId(1L); saved.setPicSize(100L);
        IllegalStateException error = new IllegalStateException("private database test failure");
        if (fail) when(writes.save(any(), isNull(), any())).thenThrow(error);
        else when(writes.save(any(), isNull(), any())).thenReturn(saved);
        Supplier<PictureVO> save = () -> ReflectionTestUtils.invokeMethod(service, "persistPrivateUpload",
                upload, null, 1L, null, null, null, null, null, new User(), null, null, null);
        if (fail) assertSame(error, assertThrows(IllegalStateException.class, save::get));
        else assertEquals("/picture/private/file?id=123&variant=display", save.get().getUrl());
        assertEquals(1, events.list.size()); assertTiming(0, "DATABASE", 100);
        verify(writes).save(any(), isNull(), any());
    }
}
