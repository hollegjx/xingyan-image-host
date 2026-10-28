package learn.gjx.tuku1.manager;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import learn.gjx.tuku1.exception.BusinessException;
import learn.gjx.tuku1.exception.ErrorCode;
import learn.gjx.tuku1.mapper.PrivateFileCleanupMapper;
import learn.gjx.tuku1.model.entity.PrivateFileCleanup;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import javax.annotation.Resource;
import java.util.*;
import java.util.stream.Collectors;

/** 私有文件使用独立的数据库清理任务。 */
@Component
@Slf4j
public class PrivatePictureCleanupManager {
    private static final long UPLOAD_GRACE_MS = 24 * 60 * 60 * 1000L;
    @Resource private PrivateFileCleanupMapper mapper;
    @Resource private PrivatePictureFileManager storage;
    @Resource private TransactionTemplate transactionTemplate;

    /** 必须先成功提交登记，再执行任何 COS 写入。崩溃遗留对象在 24 小时后回收。 */
    public void registerUpload(Long spaceId, Collection<String> urls) {
        independent().execute(status -> {
            enqueue(spaceId, urls, new Date(System.currentTimeMillis() + UPLOAD_GRACE_MS));
            return null;
        });
    }

    /** 与图片保存、额度更新同事务；清理器先抢到任务或凭据超时则拒绝再引用该文件。 */
    public void adopt(Long spaceId, Collection<String> urls) {
        requireTransaction();
        for (String url : distinct(urls)) {
            List<PrivateFileCleanup> tasks = mapper.lockByUrl(spaceId, url);
            // A failed delete can have succeeded remotely, so its backoff cannot renew the upload lease.
            if (tasks.isEmpty() || tasks.stream().anyMatch(t -> !Objects.equals(t.getAttempts(), 0)
                    || !t.getNextAttemptTime().after(new Date()))) {
                throw new BusinessException(ErrorCode.OPERATION_ERROR, "上传凭据已过期，请重新上传");
            }
            tasks.forEach(t -> mapper.deleteById(t.getId()));
        }
    }

    /** 旧文件的清理意图随业务一起提交，业务回滚则不会误删旧图。 */
    public void enqueueCommitted(Long spaceId, Collection<String> urls) {
        requireTransaction();
        enqueue(spaceId, urls, new Date());
    }

    /** 上传失败或派生图不采用，缩短等待；失败时原有持久化登记仍然存在。 */
    public void abandon(Long spaceId, Collection<String> urls) {
        try {
            independent().execute(status -> {
                for (String url : distinct(urls)) {
                    List<PrivateFileCleanup> tasks = mapper.lockByUrl(spaceId, url);
                    if (tasks.isEmpty()) {
                        enqueue(spaceId, Collections.singleton(url), new Date());
                    } else {
                        for (PrivateFileCleanup task : tasks) {
                            task.setNextAttemptTime(new Date());
                            mapper.updateById(task);
                        }
                    }
                }
                return null;
            });
        } catch (RuntimeException error) {
            log.error("私有上传补偿加速失败，保留原有延迟清理任务，spaceId={}", spaceId, error);
        }
    }

    @Scheduled(fixedDelayString = "${picture.private-storage.cleanup-delay-ms:60000}")
    public void retryPending() {
        if (!storage.isEnabled()) { return; }
        List<PrivateFileCleanup> tasks = mapper.selectList(new QueryWrapper<PrivateFileCleanup>()
                .le("nextAttemptTime", new Date()).orderByAsc("id").last("LIMIT 100"));
        for (PrivateFileCleanup candidate : tasks) {
            try {
                independent().execute(status -> {
                    PrivateFileCleanup task = mapper.lock(candidate.getId());
                    if (task == null || task.getNextAttemptTime().after(new Date())) { return null; }
                    // 补偿可能源于提交结果不明；绝不能删除已经成功引用的对象。
                    if (mapper.liveReferences(task.getUrl()) > 0) {
                        mapper.deleteById(task.getId());
                        return null;
                    }
                    try {
                        storage.delete(task.getSpaceId(), task.getUrl());
                        mapper.deleteById(task.getId());
                    } catch (RuntimeException error) {
                        int attempts = Math.min(task.getAttempts() + 1, 1000000);
                        task.setAttempts(attempts);
                        task.setNextAttemptTime(new Date(System.currentTimeMillis()
                                + Math.min(3600000L, 60000L * (1L << Math.min(attempts - 1, 6)))));
                        mapper.updateById(task);
                        log.warn("私有文件清理失败，将重试，taskId={}, attempts={}", task.getId(), attempts);
                    }
                    return null;
                });
            } catch (RuntimeException error) {
                log.error("私有文件清理任务暂不可执行，taskId={}", candidate.getId(), error);
            }
        }
    }

    private void enqueue(Long spaceId, Collection<String> urls, Date due) {
        for (String url : distinct(urls)) {
            // 登记时检查桶与空间路径；执行删除时再次检查。
            storage.validatedKey(spaceId, url);
            PrivateFileCleanup task = new PrivateFileCleanup();
            task.setSpaceId(spaceId); task.setUrl(url); task.setNextAttemptTime(due); task.setAttempts(0);
            if (mapper.insert(task) != 1) { throw new BusinessException(ErrorCode.OPERATION_ERROR, "文件清理登记失败"); }
        }
    }

    private List<String> distinct(Collection<String> urls) {
        return urls.stream().filter(Objects::nonNull).filter(s -> !s.trim().isEmpty())
                .distinct().sorted().collect(Collectors.toList());
    }

    private TransactionTemplate independent() {
        TransactionTemplate template = new TransactionTemplate(transactionTemplate.getTransactionManager());
        template.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        return template;
    }

    private void requireTransaction() {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("Private cleanup changes require a business transaction");
        }
    }
}
