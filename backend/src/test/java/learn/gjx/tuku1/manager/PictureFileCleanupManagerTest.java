package learn.gjx.tuku1.manager;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class PictureFileCleanupManagerTest {

    @Test
    void shouldRetryTransientCosDeleteFailure() {
        FileManager fileManager = mock(FileManager.class);
        PictureFileCleanupManager manager = new PictureFileCleanupManager();
        ReflectionTestUtils.setField(manager, "fileManager", fileManager);
        String url = "https://cos.example.com/test.jpg";
        doThrow(new IllegalStateException("temporary failure"))
                .doReturn(true)
                .when(fileManager).deletePicture(url);

        manager.deleteQuietly(url, "test");
        assertEquals(1, manager.pendingRetryCount());

        manager.retryFailedDeletes();

        assertEquals(0, manager.pendingRetryCount());
        verify(fileManager, org.mockito.Mockito.times(2)).deletePicture(url);
    }
}
