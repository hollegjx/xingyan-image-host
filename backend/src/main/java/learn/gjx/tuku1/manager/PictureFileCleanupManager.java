package learn.gjx.tuku1.manager;

import cn.hutool.core.util.StrUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.scheduling.annotation.Scheduled;

import javax.annotation.Resource;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * 统一处理图片文件清理，避免业务服务散落 COS 异常处理代码。
 */
@Component
@Slf4j
public class PictureFileCleanupManager {

    private static final int MAX_RETRY_ATTEMPTS = 5;

    private static final int MAX_RETRY_BATCH_SIZE = 100;

    private final Queue<CleanupTask> retryQueue = new ConcurrentLinkedQueue<>();

    @Resource
    private FileManager fileManager;

    /**
     * 尽力删除图片文件。数据库事务已经结束或回滚后调用，不再影响主业务结果。
     */
    public void deleteQuietly(String pictureUrl, String scene) {
        if (StrUtil.isBlank(pictureUrl)) {
            return;
        }
        try {
            boolean deleted = fileManager.deletePicture(pictureUrl);
            if (!deleted) {
                log.warn("跳过非当前 COS 的图片清理, scene: {}, url: {}", scene, pictureUrl);
            }
        } catch (Exception e) {
            log.error("图片文件清理失败, scene: {}, url: {}", scene, pictureUrl, e);
            retryQueue.offer(new CleanupTask(pictureUrl, scene, 1));
        }
    }

    /**
     * 对临时 COS 故障进行有限次数重试，防止一次网络抖动留下孤儿文件。
     */
    @Scheduled(fixedDelayString = "${picture.file-cleanup.retry-delay-ms:60000}")
    public void retryFailedDeletes() {
        int taskCount = Math.min(retryQueue.size(), MAX_RETRY_BATCH_SIZE);
        for (int i = 0; i < taskCount; i++) {
            CleanupTask task = retryQueue.poll();
            if (task == null) {
                return;
            }
            try {
                fileManager.deletePicture(task.pictureUrl);
                log.info("图片文件重试清理成功, scene: {}, attempt: {}", task.scene, task.attempt);
            } catch (Exception e) {
                if (task.attempt < MAX_RETRY_ATTEMPTS) {
                    retryQueue.offer(task.nextAttempt());
                } else {
                    log.error("图片文件清理达到最大重试次数, scene: {}, url: {}", task.scene, task.pictureUrl, e);
                }
            }
        }
    }

    int pendingRetryCount() {
        return retryQueue.size();
    }

    private static class CleanupTask {
        private final String pictureUrl;
        private final String scene;
        private final int attempt;

        private CleanupTask(String pictureUrl, String scene, int attempt) {
            this.pictureUrl = pictureUrl;
            this.scene = scene;
            this.attempt = attempt;
        }

        private CleanupTask nextAttempt() {
            return new CleanupTask(pictureUrl, scene, attempt + 1);
        }
    }
}
