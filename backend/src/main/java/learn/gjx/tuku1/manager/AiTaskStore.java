package learn.gjx.tuku1.manager;

import learn.gjx.tuku1.config.AiEditingProperties;
import learn.gjx.tuku1.api.aliyunai.AliyunOutpaintingClient;
import learn.gjx.tuku1.exception.*;
import learn.gjx.tuku1.model.entity.AiPictureTask;
import org.springframework.jdbc.core.BeanPropertyRowMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;
import java.util.*;

/** Short database transactions only: no provider calls while holding the submission gate. */
@Component
public class AiTaskStore {
    private final JdbcTemplate jdbc;
    private final TransactionTemplate tx;
    private final AiEditingProperties config;
    public AiTaskStore(JdbcTemplate jdbc, TransactionTemplate tx, AiEditingProperties config) {
        this.jdbc = jdbc; this.tx = tx; this.config = config;
    }
    public AiPictureTask find(String id, Long userId) {
        return one("SELECT * FROM ai_picture_task WHERE id=? AND userId=?", id, userId);
    }
    public AiPictureTask latest(Long pictureId, Long userId) {
        return one("SELECT * FROM ai_picture_task WHERE pictureId=? AND userId=? ORDER BY createTime DESC,id DESC LIMIT 1", pictureId, userId);
    }
    public AiPictureTask reserve(AiPictureTask task) {
        return tx.execute(status -> {
            jdbc.queryForObject("SELECT id FROM ai_picture_gate WHERE id=1 FOR UPDATE", Integer.class);
            AiPictureTask existing = one("SELECT * FROM ai_picture_task WHERE userId=? AND requestId=?", task.getUserId(), task.getRequestId());
            if (existing != null) {
                if (!Objects.equals(existing.getPictureId(), task.getPictureId())
                        || !Objects.equals(existing.getXScale(), task.getXScale())
                        || !Objects.equals(existing.getYScale(), task.getYScale()))
                    throw new BusinessException(ErrorCode.PARAMS_ERROR, "重复请求标识不能用于不同参数");
                return existing;
            }
            Date minute = new Date(task.getCreateTime().getTime() - 60_000);
            Date day = new Date(task.getCreateTime().getTime() - 86_400_000);
            if (count("SELECT COUNT(*) FROM ai_picture_task WHERE userId=? AND createTime>=?", task.getUserId(), minute) >= 3
                    || count("SELECT COUNT(*) FROM ai_picture_task WHERE userId=? AND createTime>=?", task.getUserId(), day) >= config.getDailyUserLimit()
                    || count("SELECT COUNT(*) FROM ai_picture_task WHERE createTime>=?", day) >= config.getDailyGlobalLimit())
                throw new BusinessException(ErrorCode.OPERATION_ERROR, "AI 任务频率或每日额度已达上限");
            if (count("SELECT COUNT(*) FROM ai_picture_task WHERE userId=? AND expiresAt>? AND status IN ('QUEUED','SUBMITTING','PENDING','RUNNING','SUSPENDED','UNKNOWN','APPLYING','APPLY_UNKNOWN')", task.getUserId(), task.getCreateTime()) > 0)
                throw new BusinessException(ErrorCode.OPERATION_ERROR, "已有待处理任务，请先查询其状态");
            task.setStatus("QUEUED");
            jdbc.update("INSERT INTO ai_picture_task (id,userId,pictureId,requestId,xScale,yScale,sourceUrl,status,createTime,expiresAt,nextPollTime,providerModel,outputWidth,outputHeight) VALUES (?,?,?,?,?,?,?,'QUEUED',?,?,?,?,?,?)",
                    task.getId(), task.getUserId(), task.getPictureId(), task.getRequestId(), task.getXScale(), task.getYScale(), task.getSourceUrl(), task.getCreateTime(), task.getExpiresAt(), task.getCreateTime(),
                    task.getProviderModel(), task.getOutputWidth(), task.getOutputHeight());
            return task;
        });
    }
    private long count(String sql, Object... args) { return Objects.requireNonNull(jdbc.queryForObject(sql, Long.class, args)); }
    private AiPictureTask one(String sql, Object... args) {
        List<AiPictureTask> rows = jdbc.query(sql, new BeanPropertyRowMapper<>(AiPictureTask.class), args);
        return rows.isEmpty() ? null : rows.get(0);
    }
    public boolean transition(String id, String expected, String status, String message) {
        return jdbc.update("UPDATE ai_picture_task SET status=?,message=? WHERE id=? AND status=?", status, message, id, expected) == 1;
    }

    public AiApplyClaim beginApply(AiPictureTask task, Long userId) {
        String token = UUID.randomUUID().toString();
        Date now = new Date();
        if (jdbc.update("UPDATE ai_picture_task SET status='APPLYING',applyToken=?,nextPollTime=?,message='正在保存，请勿重复提交' WHERE id=? AND userId=? AND status='SUCCEEDED' AND expiresAt>?",
                token, new Date(now.getTime() + 300_000), task.getId(), userId, now) != 1)
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "结果正在保存或已过期，请刷新查询");
        return new AiApplyClaim(task.getId(), token, userId, task.getPictureId(), task.getSourceUrl());
    }

    /** Must share the exact transaction that writes the picture and cleanup outbox. */
    public void commitApply(AiApplyClaim claim, learn.gjx.tuku1.model.entity.Picture current, Long userId) {
        if (!org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive())
            throw new IllegalStateException("AI apply requires picture transaction");
        if (current == null || !Objects.equals(claim.getUserId(), userId)
                || !Objects.equals(claim.getPictureId(), current.getId()) || !Objects.equals(claim.getSourceUrl(), current.getUrl()))
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "原图已变更，请刷新后重新处理");
        if (jdbc.update("UPDATE ai_picture_task SET status='APPLIED',message='结果已应用' WHERE id=? AND userId=? AND pictureId=? AND sourceUrl=? AND status='APPLYING' AND applyToken=? AND nextPollTime>?",
                claim.getTaskId(), userId, current.getId(), current.getUrl(), claim.getToken(), new Date()) != 1)
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "保存凭据已失效，请查询任务状态");
    }

    public void applyUncertain(AiApplyClaim claim) {
        jdbc.update("UPDATE ai_picture_task SET status='APPLY_UNKNOWN',message='保存中断，可核对并恢复已有结果' WHERE id=? AND status='APPLYING' AND applyToken=?", claim.getTaskId(), claim.getToken());
    }

    public boolean recoverApply(AiPictureTask task) {
        return task.getApplyToken() != null && jdbc.update("UPDATE ai_picture_task SET status='SUCCEEDED',applyToken=NULL,message='已恢复已有结果，未重新生成' WHERE id=? AND userId=? AND status='APPLY_UNKNOWN' AND applyToken=? AND expiresAt>?",
                task.getId(), task.getUserId(), task.getApplyToken(), new Date()) == 1;
    }

    public boolean recoverReceipt(AiPictureTask task) {
        return jdbc.update("UPDATE ai_picture_task SET status='SUCCEEDED',message='已核验生成回执，未重新生成' WHERE id=? AND userId=? AND status='UNKNOWN' AND resultUrl=? AND expiresAt>?",
                task.getId(), task.getUserId(), task.getResultUrl(), new Date()) == 1;
    }
    public AiPictureTask nextQueued() {
        return one("SELECT * FROM ai_picture_task WHERE status='QUEUED' AND providerModel=? ORDER BY createTime,id LIMIT 1", AliyunOutpaintingClient.MODEL);
    }
    /** Durable one-way claim before outbound I/O. A crashed claim is NEVER requeued. */
    public boolean claimGeneration(String id, Date now) {
        return jdbc.update("UPDATE ai_picture_task SET status='SUBMITTING',nextPollTime=? WHERE id=? AND status='QUEUED' AND providerModel=? AND expiresAt>? AND createTime>?",
                new Date(now.getTime() + 300_000), id, AliyunOutpaintingClient.MODEL, now, new Date(now.getTime() - 600_000)) == 1;
    }
    public boolean completed(String id, String result) {
        return jdbc.update("UPDATE ai_picture_task SET status='SUCCEEDED',resultUrl=?,message=NULL,expiresAt=? WHERE id=? AND status='SUBMITTING' AND providerModel=?",
                result, new Date(System.currentTimeMillis() + 1_800_000), id, AliyunOutpaintingClient.MODEL) == 1;
    }
    /** Quarantined receipt: never exposed by the VO before validation/SUCCEEDED. */
    public boolean received(String id,String candidate,String requestId) {
        return jdbc.update("UPDATE ai_picture_task SET resultUrl=?,providerRequestId=? WHERE id=? AND status='SUBMITTING' AND providerModel=?",
                candidate,requestId,id,AliyunOutpaintingClient.MODEL)==1;
    }
    public void requestEvidence(String id,String requestId) {
        jdbc.update("UPDATE ai_picture_task SET providerRequestId=? WHERE id=? AND status='SUBMITTING'",requestId,id);
    }
    public void expireStale(Date now) {
        expireStale(null, now);
    }
    public void expireStale(String id, Date now) {
        String suffix = id == null ? "" : " AND id=?";
        List<Object> queued = new ArrayList<>(Arrays.asList(new Date(now.getTime()-600_000), now));
        if (id != null) queued.add(id);
        jdbc.update("UPDATE ai_picture_task SET status='FAILED',message='排队已过期，未向百炼提交' WHERE status='QUEUED' AND (createTime<=? OR expiresAt<=?)"+suffix, queued.toArray());
        List<Object> claimed = new ArrayList<>(Collections.singletonList(now));
        if (id != null) claimed.add(id);
        jdbc.update("UPDATE ai_picture_task SET status='UNKNOWN',message='调用或进程中断，结果未确认；不会自动重新生成，请联系管理员核对' WHERE status='SUBMITTING' AND nextPollTime<=?"+suffix, claimed.toArray());
        jdbc.update("UPDATE ai_picture_task SET status='APPLY_UNKNOWN',message='保存中断，请核对并恢复；旧版本任务需管理员核对' WHERE status='APPLYING' AND nextPollTime<=?"+suffix, claimed.toArray());
        List<Object> legacy = new ArrayList<>(Collections.singletonList(new Date(now.getTime()-600_000)));
        if (id != null) legacy.add(id);
        jdbc.update("UPDATE ai_picture_task SET status='UNKNOWN',message='旧模型任务不可继续查询，请联系管理员核对；不会重新生成' WHERE providerModel IS NULL AND status IN ('PENDING','RUNNING','SUSPENDED') AND createTime<=?"+suffix, legacy.toArray());
    }
}
