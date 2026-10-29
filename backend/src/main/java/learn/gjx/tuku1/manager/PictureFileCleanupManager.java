package learn.gjx.tuku1.manager;

import learn.gjx.tuku1.config.CosClientConfig;
import learn.gjx.tuku1.exception.BusinessException;
import learn.gjx.tuku1.exception.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.scheduling.annotation.Scheduled;

import javax.annotation.Resource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.net.URI;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 统一处理图片文件清理，避免业务服务散落 COS 异常处理代码。
 */
@Component
@Slf4j
public class PictureFileCleanupManager {

    @Resource private JdbcTemplate jdbc;
    @Resource private TransactionTemplate transactionTemplate;
    @Resource private CosManager storage;
    @Resource private CosClientConfig config;
    private static final long UPLOAD_GRACE_MS = 86_400_000L;

    public void registerUpload(Collection<String> urls) {
        independent().execute(status -> {
            for (String url : distinct(urls)) {
                if (key(url) == null) throw new IllegalArgumentException("Unmanaged upload URL");
                jdbc.update("INSERT INTO public_file_cleanup (id,url,nextAttemptTime,attempts) VALUES (?,?,?,0)",
                        UUID.randomUUID().toString(), url, new Date(System.currentTimeMillis() + UPLOAD_GRACE_MS));
            }
            return null;
        });
    }

    public void adopt(Collection<String> urls) {
        requireTransaction();
        for (String url : distinct(urls)) {
            // Retry backoff must never turn an expired upload receipt into a valid one.
            List<Boolean> valid = jdbc.query("SELECT nextAttemptTime,attempts FROM public_file_cleanup WHERE url=? FOR UPDATE",
                    (rs, row) -> rs.getInt("attempts") == 0 && rs.getTimestamp("nextAttemptTime").after(new Date()), url);
            if (valid.size() != 1 || !valid.get(0))
                throw new BusinessException(ErrorCode.OPERATION_ERROR, "上传凭据已过期，请重新上传");
            jdbc.update("DELETE FROM public_file_cleanup WHERE url=?", url);
        }
    }

    public void enqueueCommitted(Collection<String> urls) {
        requireTransaction();
        for (String url : distinct(urls)) {
            if (key(url) == null) continue;
            if (jdbc.update("UPDATE public_file_cleanup SET nextAttemptTime=? WHERE url=?", new Date(), url) == 0)
                jdbc.update("INSERT INTO public_file_cleanup (id,url,nextAttemptTime,attempts) VALUES (?,?,?,0)",
                        UUID.randomUUID().toString(), url, new Date());
        }
    }

    /** Failure here leaves the independently committed pre-upload receipt intact. */
    public void deleteQuietly(String url, String scene) {
        try {
            independent().execute(status -> { enqueueCommitted(Collections.singletonList(url)); return null; });
        } catch (RuntimeException error) {
            log.error("公共上传补偿加速失败，保留延迟清理凭据，scene={}", scene, error);
        }
    }

    @Scheduled(fixedDelayString = "${picture.file-cleanup.retry-delay-ms:60000}")
    public void retryFailedDeletes() {
        List<String> ids = jdbc.queryForList("SELECT id FROM public_file_cleanup WHERE nextAttemptTime<=? ORDER BY nextAttemptTime,id LIMIT 100", String.class, new Date());
        for (String id : ids) {
            try {
                independent().execute(status -> {
                    List<Map<String,Object>> rows = jdbc.queryForList("SELECT url,attempts,nextAttemptTime FROM public_file_cleanup WHERE id=? FOR UPDATE", id);
                    if (rows.isEmpty()) return null;
                    Map<String,Object> row = rows.get(0);
                    Object nextAttemptTime = row.get("nextAttemptTime");
                    Date nextAttempt = nextAttemptTime instanceof java.time.LocalDateTime
                            ? java.sql.Timestamp.valueOf((java.time.LocalDateTime) nextAttemptTime)
                            : (Date) nextAttemptTime;
                    if (nextAttempt.after(new Date())) return null;
                    String url = (String) row.get("url");
                    // Also protects successful commits whose response was lost.
                    Long refs = jdbc.queryForObject("SELECT COUNT(*) FROM picture WHERE isDelete=0 AND (url=? OR originalUrl=? OR thumbnailUrl=?)", Long.class, url, url, url);
                    if (refs != null && refs > 0) {
                        jdbc.update("DELETE FROM public_file_cleanup WHERE id=?", id);
                        return null;
                    }
                    try {
                        String objectKey = key(url);
                        if (objectKey == null) throw new IllegalStateException("Cleanup bucket configuration mismatch");
                        try { storage.deleteObject(objectKey); }
                        catch (com.qcloud.cos.exception.CosServiceException error) {
                            if (!"NoSuchKey".equals(error.getErrorCode())) throw error;
                        }
                        jdbc.update("DELETE FROM public_file_cleanup WHERE id=?", id);
                    } catch (RuntimeException error) {
                        int attempts = Math.min(((Number) row.get("attempts")).intValue() + 1, 1000000);
                        long delay = Math.min(3_600_000L, 60_000L * (1L << Math.min(attempts - 1, 6)));
                        jdbc.update("UPDATE public_file_cleanup SET attempts=?,nextAttemptTime=? WHERE id=?", attempts, new Date(System.currentTimeMillis() + delay), id);
                        log.warn("公共文件清理失败，将重试，taskId={}, attempts={}", id, attempts);
                    }
                    return null;
                });
            } catch (RuntimeException error) { log.error("公共文件清理任务暂不可执行，taskId={}", id, error); }
        }
    }

    private String key(String url) {
        if (url == null) return null;
        try {
            URI uri = URI.create(url);
            String host = config.getBucket() + ".cos." + config.getRegion() + ".myqcloud.com";
            if (!("https".equals(uri.getScheme()) || "http".equals(uri.getScheme()))
                    || !host.equalsIgnoreCase(uri.getHost()) || uri.getPort() != -1 || uri.getUserInfo() != null
                    || uri.getRawQuery() != null || uri.getFragment() != null || uri.getPath() == null) return null;
            String path = uri.getPath();
            return path.startsWith("/public/") && !path.contains("/../") ? path.substring(1) : null;
        } catch (IllegalArgumentException error) { return null; }
    }

    private List<String> distinct(Collection<String> urls) {
        return urls.stream().filter(Objects::nonNull).filter(s -> !s.trim().isEmpty()).distinct().sorted().collect(Collectors.toList());
    }
    private TransactionTemplate independent() {
        TransactionTemplate tx = new TransactionTemplate(transactionTemplate.getTransactionManager());
        tx.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        return tx;
    }
    private void requireTransaction() {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) throw new IllegalStateException("Cleanup requires a business transaction");
    }
}
