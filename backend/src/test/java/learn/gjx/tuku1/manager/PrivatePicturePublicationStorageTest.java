package learn.gjx.tuku1.manager;

import com.qcloud.cos.COSClient;
import com.qcloud.cos.model.*;
import learn.gjx.tuku1.config.*;
import learn.gjx.tuku1.model.entity.Picture;
import learn.gjx.tuku1.model.vo.PrivatePictureLinkVO;
import learn.gjx.tuku1.exception.BusinessException;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.test.util.ReflectionTestUtils;
import java.io.*;
import java.nio.file.Files;
import java.net.URI;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class PrivatePicturePublicationStorageTest {
    CosClientConfig config;
    PrivatePictureFileManager storage;
    Picture source;
    @BeforeEach void prepare() {
        config=new CosClientConfig();config.setBucket("public-test-1250000000");config.setRegion("ap-nanjing");
        config.setSecretId("fixture-id");config.setSecretKey("fixture-key");
        config.setPrivateStorageBucket("private-test-1250000000");config.setPrivateStorageAccelerateEnabled(true);config.setAccelerateEnabled(true);
        PrivatePictureStorageProperties props=new PrivatePictureStorageProperties();props.setEnabled(true);props.setBucket(config.getPrivateStorageBucket());
        storage=new PrivatePictureFileManager();ReflectionTestUtils.setField(storage,"properties",props);ReflectionTestUtils.setField(storage,"cosClientConfig",config);
        source=new Picture();source.setSpaceId(10L);source.setOriginalUrl(storage.url("space/10/a.jpg"));source.setUrl(storage.url("space/10/a_display.webp"));
        source.setThumbnailUrl(storage.url("space/10/a_thumbnail.webp"));
    }
    @ParameterizedTest @ValueSource(ints={1,24,168}) void actualSdkSignsOnlyDisplayOnPrivateAcceleration(int hours) {
        COSClient client=config.cosClient();ReflectionTestUtils.setField(storage,"cosClient",client);
        try {
            long start=System.currentTimeMillis();PrivatePictureLinkVO value=storage.temporaryDisplayLink(source,hours);
            URI uri=URI.create(value.getUrl());
            assertTrue(uri.getHost().equals(config.getPrivateStorageBucket()+".cos.accelerate.myqcloud.com"));
            assertTrue(uri.getScheme().equals("https"));assertTrue(uri.getPath().equals("/space/10/a_display.webp"));
            assertTrue(uri.getQuery()!=null && uri.getQuery().contains("q-sign-time="));
            long duration=value.getExpiresAt().getTime()-start;assertTrue(duration>=hours*3600000L && duration<hours*3600000L+5000);
            assertFalse(value.toString().contains("q-sign"));
        } finally {client.shutdown();}
    }
    @Test void originalFallbackAndDisabledAccelerationAreRejectedBeforeSigning() {
        COSClient client=mock(COSClient.class);ReflectionTestUtils.setField(storage,"cosClient",client);
        source.setUrl(source.getOriginalUrl());assertThrows(BusinessException.class,()->storage.temporaryDisplayLink(source,1));
        source.setUrl(storage.url("space/10/a_display.webp"));config.setPrivateStorageAccelerateEnabled(false);
        assertThrows(BusinessException.class,()->storage.temporaryDisplayLink(source,1));verifyNoInteractions(client);
    }
    @Test void copiedBytesUseSeparateSourceAndDestinationBucketsAndDoNotChangeAcl() throws Exception {
        COSClient client=mock(COSClient.class);ReflectionTestUtils.setField(storage,"cosClient",client);
        byte[] bytes={1,2,3};COSObject object=new COSObject();object.setObjectContent(new COSObjectInputStream(new ByteArrayInputStream(bytes),mock(org.apache.http.client.methods.HttpRequestBase.class)));
        object.getObjectMetadata().setContentLength(bytes.length);
        when(client.getObject(any(GetObjectRequest.class))).thenAnswer(i->{GetObjectRequest r=i.getArgument(0);assertEquals(config.getPrivateStorageBucket(),r.getBucketName());assertEquals("space/10/a_display.webp",r.getKey());return object;});
        final File[] temp={null};
        when(client.putObject(any(PutObjectRequest.class))).thenAnswer(i->{PutObjectRequest p=i.getArgument(0);temp[0]=p.getFile();
            assertEquals(config.getBucket(),p.getBucketName());assertEquals("public/1/copy.webp",p.getKey());
            assertArrayEquals(bytes,Files.readAllBytes(p.getFile().toPath()));assertNull(p.getCannedAcl());assertNull(p.getAccessControlList());
            assertEquals("image/webp",p.getMetadata().getContentType());return new PutObjectResult();});
        storage.copyToPublic(source,source.getUrl(),"public/1/copy.webp");assertNotNull(temp[0]);assertFalse(temp[0].exists());
        verify(client).getObject(any(GetObjectRequest.class));verify(client).putObject(any(PutObjectRequest.class));verifyNoMoreInteractions(client);
    }
    @Test void truncatedSourceDoesNotUploadAndErrorsDoNotExposeSdkDetails() {
        COSClient client=mock(COSClient.class);ReflectionTestUtils.setField(storage,"cosClient",client);
        COSObject object=new COSObject();object.setObjectContent(new COSObjectInputStream(new ByteArrayInputStream(new byte[]{1}),mock(org.apache.http.client.methods.HttpRequestBase.class)));object.getObjectMetadata().setContentLength(2);
        when(client.getObject(any(GetObjectRequest.class))).thenReturn(object);
        BusinessException e=assertThrows(BusinessException.class,()->storage.copyToPublic(source,source.getUrl(),"public/1/copy.webp"));
        assertNull(e.getCause());verify(client,never()).putObject(any(PutObjectRequest.class));
    }
    @Test void plannedCopiesAreRegisteredFirstAndPartialFailureCompensatesEveryTarget() {
        FileManager files=new FileManager();PrivatePictureFileManager privateFiles=mock(PrivatePictureFileManager.class);
        PictureFileCleanupManager cleanup=mock(PictureFileCleanupManager.class);
        ReflectionTestUtils.setField(files,"privateStorage",privateFiles);ReflectionTestUtils.setField(files,"publicCleanup",cleanup);ReflectionTestUtils.setField(files,"cosClientConfig",config);
        when(privateFiles.validatedKey(anyLong(),anyString())).thenAnswer(i->URI.create(i.getArgument(1)).getPath().substring(1));
        doNothing().doThrow(new IllegalStateException("SDK failure")).when(privateFiles).copyToPublic(any(),anyString(),anyString());
        assertThrows(BusinessException.class,()->files.copyPrivateForSubmission(source,1L));
        org.mockito.InOrder order=inOrder(cleanup,privateFiles);
        order.verify(cleanup).registerUpload(argThat(urls->urls.size()==3));
        order.verify(privateFiles,times(2)).copyToPublic(any(),anyString(),anyString());
        verify(cleanup,times(3)).deleteQuietly(anyString(),eq("submission-copy-failed"));
    }
    @Test void displayAliasingOriginalCopiesOnlyTwoObjects() {
        source.setUrl(source.getOriginalUrl());FileManager files=new FileManager();PrivatePictureFileManager privateFiles=mock(PrivatePictureFileManager.class);
        PictureFileCleanupManager cleanup=mock(PictureFileCleanupManager.class);
        ReflectionTestUtils.setField(files,"privateStorage",privateFiles);ReflectionTestUtils.setField(files,"publicCleanup",cleanup);ReflectionTestUtils.setField(files,"cosClientConfig",config);
        when(privateFiles.validatedKey(anyLong(),anyString())).thenAnswer(i->URI.create(i.getArgument(1)).getPath().substring(1));
        Picture copied=files.copyPrivateForSubmission(source,1L);assertEquals(copied.getUrl(),copied.getOriginalUrl());
        verify(cleanup).registerUpload(argThat(urls->urls.size()==2));verify(privateFiles,times(2)).copyToPublic(any(),anyString(),anyString());
    }
}
