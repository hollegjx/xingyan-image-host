package learn.gjx.tuku1.job;

import learn.gjx.tuku1.service.PictureService;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Date;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class PictureDraftCleanupJobTest {

    @Test
    void shouldCleanDraftsUsingConfiguredRetention() {
        PictureService pictureService = mock(PictureService.class);
        PictureDraftCleanupJob job = new PictureDraftCleanupJob();
        ReflectionTestUtils.setField(job, "pictureService", pictureService);
        ReflectionTestUtils.setField(job, "expireHours", 24L);
        ReflectionTestUtils.setField(job, "cleanupBatchSize", 50);

        job.cleanExpiredDrafts();

        verify(pictureService).cleanExpiredDraftPictures(any(Date.class), eq(50));
    }
}
