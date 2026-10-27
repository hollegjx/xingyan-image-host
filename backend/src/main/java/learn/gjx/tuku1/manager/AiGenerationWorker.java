package learn.gjx.tuku1.manager;

import learn.gjx.tuku1.api.aliyunai.AliyunOutpaintingClient;
import learn.gjx.tuku1.config.AiEditingProperties;
import learn.gjx.tuku1.mapper.UserMapper;
import learn.gjx.tuku1.model.entity.*;
import learn.gjx.tuku1.service.AiPictureService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import javax.annotation.PreDestroy;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

/** A durable DB queue with a bounded worker, not a volatile @Async submission queue. */
@Component
@Slf4j
public class AiGenerationWorker {
    private final AiTaskStore store;
    private final AiPictureService service;
    private final UserMapper users;
    private final AiEditingFiles files;
    private final AliyunOutpaintingClient provider;
    private final AiEditingProperties config;
    private final AtomicBoolean busy = new AtomicBoolean();
    private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r,"ai-image-worker"); thread.setDaemon(true); return thread;
    });
    public AiGenerationWorker(AiTaskStore store,AiPictureService service,UserMapper users,AiEditingFiles files,
                              AliyunOutpaintingClient provider,AiEditingProperties config) {
        this.store=store;this.service=service;this.users=users;this.files=files;this.provider=provider;this.config=config;
    }
    @Scheduled(fixedDelay = 2000, initialDelay = 3000)
    public void tick() {
        if (!config.isEnabled() || !config.configured() || !busy.compareAndSet(false,true)) return;
        try {
            executor.execute(() -> {
                try { runOne(); }
                catch (Exception e) { log.warn("AI worker interrupted, failureType={}",e.getClass().getSimpleName()); }
                finally { busy.set(false); }
            });
        } catch (RejectedExecutionException e) { busy.set(false); }
    }
    void runOne() throws Exception {
        if (!config.isEnabled() || !config.configured()) return;
        store.expireStale(new Date());
        AiPictureTask task=store.nextQueued();
        if (task==null || !store.claimGeneration(task.getId(),new Date())) return;
        String source;
        try {
            User user=users.selectById(task.getUserId());
            if (!service.enabled(user) || user.getUserRole()==null || (user.getUserRole()!=0 && user.getUserRole()!=1))
                throw new IllegalStateException();
            Picture picture=service.editable(task.getPictureId(),user);
            if (!Objects.equals(task.getSourceUrl(),picture.getUrl()) || (picture.getSpaceId()!=null&&!config.isPrivateEnabled())
                    || task.getOutputWidth()==null || task.getOutputHeight()==null) throw new IllegalStateException();
            source=files.providerSource(picture);
        } catch (Exception e) {
            store.transition(task.getId(),"SUBMITTING","FAILED","提交前检查失败：账号、图片、配置或权限已变化，未调用百炼");
            return;
        }
        String stage="PROVIDER_CALL";
        try {
            AliyunOutpaintingClient.Generation result=provider.generate(source,task.getXScale(),task.getYScale(),task.getOutputWidth(),task.getOutputHeight());
            stage="RECEIPT_SAVE";
            if (!store.received(task.getId(),result.url,result.requestId)) return;
            stage="RESULT_VALIDATION";
            String validated=files.validateResult(result.url);
            stage="RESULT_SAVE";
            if (!store.completed(task.getId(),validated))
                log.warn("AI result arrived after task left SUBMITTING, taskId={}",task.getId());
        } catch (AliyunOutpaintingClient.ProviderHttpException e) {
            log.warn("AI submit response taskId={}, http={}, code={}, requestId={}",task.getId(),e.status,e.code,e.requestId);
            store.requestEvidence(task.getId(),e.requestId);
            store.transition(task.getId(),"SUBMITTING",e.definitivelyRejected()?"FAILED":"UNKNOWN",
                    "HTTP "+e.status+" / "+e.code+"；请联系管理员核对，不会自动重新生成");
        } catch (AliyunOutpaintingClient.ResponseException e) {
            store.requestEvidence(task.getId(),e.requestId);
            store.transition(task.getId(),"SUBMITTING","UNKNOWN","响应未确认（"+e.code+"）；不会自动重新生成");
        } catch (Exception e) {
            log.warn("AI submit uncertain taskId={}, stage={}, failureType={}",task.getId(),stage,e.getClass().getSimpleName());
            store.transition(task.getId(),"SUBMITTING","UNKNOWN","结果未确认（"+stage+" / "+e.getClass().getSimpleName()+"），可能已计费；不会自动重新生成");
        }
    }
    @PreDestroy public void close() { executor.shutdown(); }
}
