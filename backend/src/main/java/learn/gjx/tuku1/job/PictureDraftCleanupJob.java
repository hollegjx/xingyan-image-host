package learn.gjx.tuku1.job;

import learn.gjx.tuku1.service.PictureService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;

/**
 * 定时清理用户未提交的图片草稿，避免数据库和 COS 长期堆积半成品。
 */
@Component
@Slf4j
@ConditionalOnProperty(prefix = "picture.draft", name = "cleanup-enabled", havingValue = "true", matchIfMissing = true)
public class PictureDraftCleanupJob {

    @Resource
    private PictureService pictureService;

    @Value("${picture.draft.expire-hours:24}")
    private long expireHours;

    @Value("${picture.draft.cleanup-batch-size:100}")
    private int cleanupBatchSize;

    @Scheduled(fixedDelayString = "${picture.draft.cleanup-delay-ms:3600000}")
    public void cleanExpiredDrafts() {
        Date expireBefore = Date.from(Instant.now().minus(expireHours, ChronoUnit.HOURS));
        int cleanedCount = pictureService.cleanExpiredDraftPictures(expireBefore, cleanupBatchSize);
        if (cleanedCount > 0) {
            log.info("过期图片草稿清理完成, count: {}", cleanedCount);
        }
    }
}
