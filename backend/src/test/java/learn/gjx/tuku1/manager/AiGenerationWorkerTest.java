package learn.gjx.tuku1.manager;

import learn.gjx.tuku1.api.aliyunai.AliyunOutpaintingClient;
import learn.gjx.tuku1.config.AiEditingProperties;
import learn.gjx.tuku1.mapper.UserMapper;
import learn.gjx.tuku1.model.entity.*;
import learn.gjx.tuku1.service.AiPictureService;
import org.junit.jupiter.api.*;
import java.io.IOException;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class AiGenerationWorkerTest {
    AiTaskStore store;AiPictureService service;UserMapper users;AiEditingFiles files;AliyunOutpaintingClient provider;
    AiEditingProperties config;AiGenerationWorker worker;AiPictureTask task;Picture picture;User user;
    @BeforeEach void setup(){
        store=mock(AiTaskStore.class);service=mock(AiPictureService.class);users=mock(UserMapper.class);files=mock(AiEditingFiles.class);provider=mock(AliyunOutpaintingClient.class);
        config=new AiEditingProperties();config.setEnabled(true);config.setApiKey("test-only");config.setWorkspaceId("ws-testspace");
        worker=new AiGenerationWorker(store,service,users,files,provider,config);
        task=new AiPictureTask();task.setId("task-1");task.setUserId(1L);task.setPictureId(2L);task.setSourceUrl("source");task.setXScale(2d);task.setYScale(2d);task.setOutputWidth(1024);task.setOutputHeight(1024);
        user=new User();user.setId(1L);user.setUserRole(0);picture=new Picture();picture.setId(2L);picture.setUrl("source");
        when(store.nextQueued()).thenReturn(task);when(store.claimGeneration(eq("task-1"),any())).thenReturn(true,false);
        when(users.selectById(1L)).thenReturn(user);when(service.enabled(user)).thenReturn(true);when(service.editable(2L,user)).thenReturn(picture);
        when(files.providerSource(picture)).thenReturn("signed-source");when(files.validateResult("result")).thenReturn("result");
        when(store.completed("task-1","result")).thenReturn(true);
        when(store.received(eq("task-1"),anyString(),anyString())).thenReturn(true);
    }
    @AfterEach void close(){worker.close();}
    @Test void disabledNeverClaimsOrCalls()throws Exception{config.setEnabled(false);worker.runOne();verifyNoInteractions(store,provider,files);}
    @Test void lostClaimNeverCalls()throws Exception{when(store.claimGeneration(any(),any())).thenReturn(false);worker.runOne();verifyNoInteractions(provider,files,users);}
    @Test void successPersistsOnlyValidatedResult()throws Exception{
        when(provider.generate("signed-source",2,2,1024,1024)).thenReturn(new AliyunOutpaintingClient.Generation("result","request-1"));worker.runOne();worker.runOne();
        verify(provider,times(1)).generate("signed-source",2,2,1024,1024);verify(store).completed("task-1","result");
        org.mockito.InOrder order=inOrder(store,files);order.verify(store).received("task-1","result","request-1");order.verify(files).validateResult("result");
    }
    @Test void timeoutNeverResubmits()throws Exception{
        when(provider.generate(anyString(),anyDouble(),anyDouble(),anyInt(),anyInt())).thenThrow(new IOException("secret details"));
        worker.runOne();worker.runOne();verify(provider,times(1)).generate(anyString(),anyDouble(),anyDouble(),anyInt(),anyInt());
        verify(store).transition(eq("task-1"),eq("SUBMITTING"),eq("UNKNOWN"),anyString());
    }
    @Test void explicitRejectionFailsButDoesNotRetry()throws Exception{
        when(provider.generate(anyString(),anyDouble(),anyDouble(),anyInt(),anyInt())).thenThrow(new AliyunOutpaintingClient.ProviderHttpException(403,"AccessDenied","req-123"));
        worker.runOne();worker.runOne();verify(provider,times(1)).generate(anyString(),anyDouble(),anyDouble(),anyInt(),anyInt());
        verify(store).transition(eq("task-1"),eq("SUBMITTING"),eq("FAILED"),contains("HTTP 403"));
    }
    @Test void serverErrorRemainsUncertain()throws Exception{
        when(provider.generate(anyString(),anyDouble(),anyDouble(),anyInt(),anyInt())).thenThrow(new AliyunOutpaintingClient.ProviderHttpException(503,"Unavailable","req-123"));
        worker.runOne();verify(store).transition(eq("task-1"),eq("SUBMITTING"),eq("UNKNOWN"),anyString());
    }
    @Test void changedSourceIsRejectedBeforeOutbound()throws Exception{
        picture.setUrl("replaced");worker.runOne();verifyNoInteractions(provider,files);
        verify(store).transition(eq("task-1"),eq("SUBMITTING"),eq("FAILED"),contains("未调用"));
    }
    @Test void revokedAccountAndPrivateGateAreRechecked()throws Exception{
        when(service.enabled(user)).thenReturn(false);worker.runOne();verifyNoInteractions(provider,files);
    }
    @Test void privatePictureNeverLeavesWhilePrivateGateOff()throws Exception{
        picture.setSpaceId(3L);worker.runOne();verifyNoInteractions(provider,files);
    }
    @Test void untrustedResultDoesNotBecomePreview()throws Exception{
        when(provider.generate(anyString(),anyDouble(),anyDouble(),anyInt(),anyInt())).thenReturn(new AliyunOutpaintingClient.Generation("untrusted","request-1"));
        when(files.validateResult("untrusted")).thenThrow(new IllegalArgumentException());worker.runOne();
        verify(store,never()).completed(anyString(),anyString());verify(store).transition(eq("task-1"),eq("SUBMITTING"),eq("UNKNOWN"),anyString());
        verify(store).received("task-1","untrusted","request-1");
        verify(store).transition(eq("task-1"),eq("SUBMITTING"),eq("UNKNOWN"),contains("RESULT_VALIDATION"));
    }
}
