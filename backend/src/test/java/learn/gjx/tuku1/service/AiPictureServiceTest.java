package learn.gjx.tuku1.service;

import learn.gjx.tuku1.api.aliyunai.AliyunOutpaintingClient;
import learn.gjx.tuku1.config.AiEditingProperties;
import learn.gjx.tuku1.exception.*;
import learn.gjx.tuku1.manager.*;
import learn.gjx.tuku1.manager.space.PictureAccessManager;
import learn.gjx.tuku1.mapper.PictureMapper;
import learn.gjx.tuku1.model.dto.picture.*;
import learn.gjx.tuku1.model.entity.*;
import learn.gjx.tuku1.model.vo.*;
import org.junit.jupiter.api.*;
import org.springframework.mock.web.MockHttpServletRequest;
import java.io.IOException;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class AiPictureServiceTest {
    private PictureMapper pictures;private PictureAccessManager access;private PictureService uploads;private AiTaskStore store;
    private AiEditingFiles files;private AliyunOutpaintingClient provider;private AiEditingProperties config;private AiPictureService service;
    private User user;private Picture picture;private AiOutpaintingRequest body;private AiPictureTask saved;
    @BeforeEach void setup(){
        pictures=mock(PictureMapper.class);access=mock(PictureAccessManager.class);uploads=mock(PictureService.class);store=mock(AiTaskStore.class);
        files=mock(AiEditingFiles.class);provider=mock(AliyunOutpaintingClient.class);config=new AiEditingProperties();config.setEnabled(true);config.setApiKey("test-only");config.setWorkspaceId("ws-testspace");config.getAllowedUserIds().add(1L);
        service=new AiPictureService(pictures,access,uploads,store,files,config);
        user=new User();user.setId(1L);picture=new Picture();picture.setId(11L);picture.setUrl("source");picture.setPicWidth(600);picture.setPicHeight(600);picture.setPicFormat("png");
        when(pictures.selectById(11L)).thenReturn(picture);
        body=new AiOutpaintingRequest();body.setPictureId(11L);body.setRequestId(UUID.randomUUID().toString());body.setExternalProcessingConsent(true);body.setXScale(2d);body.setYScale(2d);
        when(store.reserve(any())).thenAnswer(i->{saved=i.getArgument(0);return saved;});
        when(store.find(anyString(),eq(1L))).thenAnswer(i->saved);
    }
    @Test void disabledNeverCallsProvider(){config.setEnabled(false);assertThrows(BusinessException.class,()->service.create(body,user,"/api"));verifyNoInteractions(provider,store,files);}
    @Test void requiresConsent(){body.setExternalProcessingConsent(false);assertThrows(BusinessException.class,()->service.create(body,user,"/api"));verifyNoInteractions(provider,store);}
    @Test void privateRequiresIndependentFeatureGate(){picture.setSpaceId(8L);assertThrows(BusinessException.class,()->service.create(body,user,"/api"));verifyNoInteractions(provider,store);}
    @Test void authBeforeOutboundCalls(){doThrow(new BusinessException(ErrorCode.NO_AUTH_ERROR)).when(access).checkEdit(picture,user);assertThrows(BusinessException.class,()->service.create(body,user,"/api"));verifyNoInteractions(provider,store,files);}
    @Test void badScalesAndDimensionsRejected(){
        body.setXScale(Double.NaN);assertThrows(BusinessException.class,()->service.create(body,user,"/api"));
        body.setXScale(2d);picture.setPicWidth(5000);assertThrows(BusinessException.class,()->service.create(body,user,"/api"));verifyNoInteractions(provider,store);
    }
    @Test void creationOnlyEnqueuesOneBoundedImage(){
        assertEquals("QUEUED",service.create(body,user,"/api").getStatus());
        assertEquals("qwen-image-3.0",saved.getProviderModel());
        assertEquals(1024,saved.getOutputWidth());assertEquals(1024,saved.getOutputHeight());
        verifyNoInteractions(provider,files);
    }
    @Test void missingWorkspaceDisablesCapabilitiesAndSubmission(){
        config.setWorkspaceId("");assertFalse(service.enabled(user));
        assertThrows(BusinessException.class,()->service.create(body,user,"/api"));verifyNoInteractions(store,provider,files);
    }
    @Test void duplicateRequestDoesNotResubmit()throws Exception {
        AiPictureTask old=new AiPictureTask();old.setId(UUID.randomUUID().toString());old.setStatus("UNKNOWN");
        when(store.reserve(any())).thenReturn(old);assertEquals("UNKNOWN",service.create(body,user,"/api").getStatus());verifyNoInteractions(provider,files);
    }
    @Test void queryRejectsForeignTaskBeforeCallingProvider(){when(store.find(anyString(),eq(1L))).thenReturn(null);assertThrows(BusinessException.class,()->service.get(UUID.randomUUID().toString(),user,"/api"));verifyNoInteractions(provider);}
    private AiPictureTask task(String status){
        saved=new AiPictureTask();saved.setId(UUID.randomUUID().toString());saved.setPictureId(11L);saved.setStatus(status);saved.setSourceUrl("source");saved.setCreateTime(new Date());saved.setExpiresAt(new Date(System.currentTimeMillis()+100000));return saved;
    }
    @Test void resultReturnsOnlyAuthenticatedRoute(){AiPictureTask t=task("SUCCEEDED");t.setResultUrl("secret-signed-url");AiTaskVO vo=service.get(t.getId(),user,"/tuku/api");assertEquals("/tuku/api/picture/out_painting/result?taskId="+t.getId(),vo.getResultUrl());}
    @Test void quarantinedCandidateCanNeverBePreviewedOrApplied(){
        AiPictureTask t=task("UNKNOWN");t.setResultUrl("https://untrusted.invalid/quarantined");
        assertNull(service.get(t.getId(),user,"/api").getResultUrl());
        assertThrows(BusinessException.class,()->service.result(t.getId(),user));
        assertThrows(BusinessException.class,()->service.apply(t.getId(),user,new MockHttpServletRequest()));verifyNoInteractions(files,uploads);
    }
    @Test void expiredResultCannotBeApplied(){AiPictureTask t=task("SUCCEEDED");t.setExpiresAt(new Date(0));assertThrows(BusinessException.class,()->service.apply(t.getId(),user,new MockHttpServletRequest()));verifyNoInteractions(files,uploads);}
    @Test void changedSourceCannotBeApplied(){AiPictureTask t=task("SUCCEEDED");picture.setUrl("replacement");assertThrows(BusinessException.class,()->service.apply(t.getId(),user,new MockHttpServletRequest()));verifyNoInteractions(files,uploads);}
    @Test void alreadyAppliedIsReadOnly(){AiPictureTask t=task("APPLIED");service.apply(t.getId(),user,new MockHttpServletRequest());verify(uploads).getPictureVO(eq(picture),any());verifyNoInteractions(files);}
    @Test void applyingIsNotRetried(){AiPictureTask t=task("APPLYING");assertThrows(BusinessException.class,()->service.apply(t.getId(),user,new MockHttpServletRequest()));verifyNoInteractions(files,uploads);}
    @Test void applyUsesExistingUploadAndPreservesMetadata(){
        AiPictureTask t=task("SUCCEEDED");t.setResultUrl("provider-result");picture.setSpaceId(8L);
        picture.setName("原名称");picture.setIntroduction("说明");picture.setCategory("风景");picture.setTags("[\"旅行\"]");
        byte[] bytes=new byte[]{1};org.springframework.web.multipart.MultipartFile file=mock(org.springframework.web.multipart.MultipartFile.class);
        when(files.result("provider-result")).thenReturn(bytes);when(files.uploadFile(bytes)).thenReturn(file);
        when(store.transition(eq(t.getId()),eq("SUCCEEDED"),eq("APPLYING"),anyString())).thenReturn(true);
        service.apply(t.getId(),user,new MockHttpServletRequest());
        org.mockito.ArgumentCaptor<PictureUploadRequest> arg=org.mockito.ArgumentCaptor.forClass(PictureUploadRequest.class);
        verify(uploads).uploadPicture(eq(file),arg.capture(),eq(user));
        assertEquals(11L,arg.getValue().getId());assertEquals(8L,arg.getValue().getSpaceId());
        assertEquals("原名称",arg.getValue().getName());assertEquals("说明",arg.getValue().getIntroduction());
        assertEquals("风景",arg.getValue().getCategory());assertEquals(Collections.singletonList("旅行"),arg.getValue().getTags());
        verify(store).transition(t.getId(),"APPLYING","APPLIED","结果已应用");
    }
    @Test void applyFailureRecordsUncertaintyAndDoesNotRetryUpload(){
        AiPictureTask t=task("SUCCEEDED");t.setResultUrl("provider-result");
        org.springframework.web.multipart.MultipartFile file=mock(org.springframework.web.multipart.MultipartFile.class);
        when(files.uploadFile(any())).thenReturn(file);
        when(store.transition(eq(t.getId()),eq("SUCCEEDED"),eq("APPLYING"),anyString())).thenReturn(true);
        when(uploads.uploadPicture(eq(file),any(),eq(user))).thenThrow(new RuntimeException("test failure"));
        assertThrows(BusinessException.class,()->service.apply(t.getId(),user,new MockHttpServletRequest()));
        verify(uploads,times(1)).uploadPicture(eq(file),any(),eq(user));
        verify(store).transition(eq(t.getId()),eq("APPLYING"),eq("APPLY_UNKNOWN"),anyString());
    }
    @Test void queryingQueuedTaskOnlyReadsDatabase(){AiPictureTask t=task("QUEUED");assertEquals("QUEUED",service.get(t.getId(),user,"/api").getStatus());verifyNoInteractions(provider,files);verify(store).expireStale(eq(t.getId()),any());}
}
