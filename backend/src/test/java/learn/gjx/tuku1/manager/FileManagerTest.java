package learn.gjx.tuku1.manager;

import com.qcloud.cos.model.PutObjectResult;
import com.qcloud.cos.model.ciModel.persistence.CIObject;
import com.qcloud.cos.model.ciModel.persistence.CIUploadResult;
import com.qcloud.cos.model.ciModel.persistence.ImageInfo;
import com.qcloud.cos.model.ciModel.persistence.OriginalInfo;
import com.qcloud.cos.model.ciModel.persistence.PicOperations;
import com.qcloud.cos.model.ciModel.persistence.ProcessResults;
import learn.gjx.tuku1.config.CosClientConfig;
import learn.gjx.tuku1.exception.BusinessException;
import learn.gjx.tuku1.model.dto.file.PictureUploadResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.File;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class FileManagerTest {

    private FileManager fileManager;
    private CosManager cosManager;
    private PrivatePictureCleanupManager privateCleanup;
    private PictureFileCleanupManager publicCleanup;

    @BeforeEach
    void setUp() {
        fileManager = new FileManager();
        cosManager = mock(CosManager.class);
        CosClientConfig config = new CosClientConfig();
        config.setBucket("test-bucket");
        config.setRegion("ap-test");
        ReflectionTestUtils.setField(fileManager, "cosManager", cosManager);
        ReflectionTestUtils.setField(fileManager, "cosClientConfig", config);
        PrivatePictureFileManager privateStorage = new PrivatePictureFileManager();
        learn.gjx.tuku1.config.PrivatePictureStorageProperties props = new learn.gjx.tuku1.config.PrivatePictureStorageProperties();
        props.setEnabled(true); props.setBucket("private-test-1250000000");
        ReflectionTestUtils.setField(privateStorage, "properties", props);
        ReflectionTestUtils.setField(privateStorage, "cosClientConfig", config);
        ReflectionTestUtils.setField(fileManager, "privateStorage", privateStorage);
        privateCleanup = mock(PrivatePictureCleanupManager.class);
        ReflectionTestUtils.setField(fileManager, "privateCleanup", privateCleanup);
        publicCleanup = mock(PictureFileCleanupManager.class);
        ReflectionTestUtils.setField(fileManager, "publicCleanup", publicCleanup);
    }

    @Test
    void shouldOnlyExtractKeyFromCurrentCos() {
        assertEquals("public/1/test.jpg", fileManager.extractKeyFromUrl(
                "https://test-bucket.cos.ap-test.myqcloud.com/public/1/test.jpg"));
        assertNull(fileManager.extractKeyFromUrl("https://outside.example.com/test.jpg"));
    }

    @Test void publicUploadMustRegisterBeforeAnyCosWrite() {
        org.mockito.Mockito.doThrow(new IllegalStateException("database unavailable")).when(publicCleanup).registerUpload(any());
        assertThrows(IllegalStateException.class,()->fileManager.uploadPicture(new MockMultipartFile("file","image.jpg","image/jpeg",jpegBytes(30000)),"public/1"));
        org.mockito.Mockito.verifyNoInteractions(cosManager);
    }

    @Test void processRejectsArbitraryPathsAndAdditionalOutputsBeforeCosAccess() {
        learn.gjx.tuku1.model.dto.picture.PictureProcessRule rule=new learn.gjx.tuku1.model.dto.picture.PictureProcessRule();rule.setRule("rotate/90");
        assertThrows(BusinessException.class,()->fileManager.processPicture("public/1/a.jpg",Arrays.asList(rule),"public/2"));
        rule.setOutputFileName("../../victim.jpg");assertThrows(BusinessException.class,()->fileManager.processPicture("public/1/a.jpg",Arrays.asList(rule),null));
        rule.setOutputFileName("safe.jpg");rule.setRule("imageMogr2/rotate/90|saveas/other");
        assertThrows(BusinessException.class,()->fileManager.processPicture("public/1/a.jpg",Arrays.asList(rule),null));
        org.mockito.Mockito.verifyNoInteractions(cosManager);
    }

    @Test void processedObjectsUseUniqueRegisteredTemporaryPaths() {
        com.qcloud.cos.model.COSObject source=new com.qcloud.cos.model.COSObject();
        source.setObjectContent(new com.qcloud.cos.model.COSObjectInputStream(new java.io.ByteArrayInputStream(jpegBytes(1000)),new org.apache.http.client.methods.HttpGet("https://unused.invalid/test")));
        when(cosManager.getObject("public/1/a.jpg")).thenReturn(source);
        when(cosManager.putPictureObject(anyString(),any(File.class),any(PicOperations.class))).thenReturn(uploadResult());
        learn.gjx.tuku1.model.dto.picture.PictureProcessRule rule=new learn.gjx.tuku1.model.dto.picture.PictureProcessRule();rule.setRule("rotate/90");rule.setOutputFileName("a.jpg");
        learn.gjx.tuku1.model.vo.PictureProcessResult result=fileManager.processPicture("public/1/a.jpg",Arrays.asList(rule,rule),"exports");
        assertTrue(result.getExpiresAt().after(new java.util.Date()));
        ArgumentCaptor<PicOperations> ops=ArgumentCaptor.forClass(PicOperations.class);
        org.mockito.InOrder order=org.mockito.Mockito.inOrder(publicCleanup,cosManager);
        order.verify(publicCleanup).registerUpload(any());order.verify(cosManager).putPictureObject(anyString(),any(File.class),ops.capture());
        assertTrue(ops.getValue().getRules().get(0).getFileId().startsWith("/public/processing/"));
        org.junit.jupiter.api.Assertions.assertNotEquals(ops.getValue().getRules().get(0).getFileId(),ops.getValue().getRules().get(1).getFileId());
        org.mockito.Mockito.verifyNoInteractions(privateCleanup);
    }

    @Test
    void shouldNotDeleteForeignUrl() {
        assertFalse(fileManager.deletePicture("https://outside.example.com/test.jpg"));
        verify(cosManager, never()).deleteObject(org.mockito.ArgumentMatchers.anyString());
    }

    @Test
    void shouldDeleteCurrentCosObject() {
        assertTrue(fileManager.deletePicture(
                "https://test-bucket.cos.ap-test.myqcloud.com/public/1/test.jpg"));
        verify(cosManager).deleteObject("public/1/test.jpg");
    }

    @Test
    void shouldRejectFileWhoseContentDoesNotMatchSuffix() {
        MockMultipartFile file = new MockMultipartFile("file", "fake.jpg", "image/jpeg", "not-an-image".getBytes());

        assertThrows(BusinessException.class, () -> fileManager.uploadPicture(file, "public/1"));
        verify(cosManager, never()).putPictureObject(
                org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void shouldRejectPrivateRemoteAddress() {
        assertThrows(BusinessException.class,
                () -> fileManager.uploadPictureByUrl("http://127.0.0.1/private.jpg", "public/1"));
    }

    @Test
    void shouldGenerateAndMatchDerivativesByKeyInsteadOfResultOrder() {
        byte[] content = jpegBytes(30 * 1024);
        MockMultipartFile file = new MockMultipartFile("file", "photo.jpg", "image/jpeg", content);
        when(cosManager.putPictureObject(anyString(), any(File.class), any(PicOperations.class)))
                .thenAnswer(invocation -> {
                    String originalKey = invocation.getArgument(0);
                    String baseKey = originalKey.substring(0, originalKey.lastIndexOf('.'));
                    // 故意把缩略图放在主展示图之前，验证实现不依赖 COS 返回顺序。
                    return uploadResult(baseKey + "_thumbnail.webp", 2 * 1024,
                            baseKey + "_display.webp", 10 * 1024);
                });

        PictureUploadResult result = fileManager.uploadPicture(file, "public/1");

        assertTrue(result.getOriginalUrl().endsWith(".jpg"));
        assertTrue(result.getUrl().endsWith("_display.webp"));
        assertTrue(result.getThumbnailUrl().endsWith("_thumbnail.webp"));
        assertEquals(Long.valueOf(content.length), result.getPicSize());

        ArgumentCaptor<PicOperations> operationsCaptor = ArgumentCaptor.forClass(PicOperations.class);
        verify(cosManager).putPictureObject(anyString(), any(File.class), operationsCaptor.capture());
        assertEquals(2, operationsCaptor.getValue().getRules().size());
        assertTrue(operationsCaptor.getValue().getRules().get(0).getFileId().startsWith("/public/1/"));
        assertTrue(operationsCaptor.getValue().getRules().get(0).getRule().contains("format/webp"));
        assertTrue(operationsCaptor.getValue().getRules().get(1).getRule().contains("thumbnail/512x512>"));
    }

    @Test
    void shouldSkipThumbnailForSmallPicture() {
        byte[] content = jpegBytes(10 * 1024);
        MockMultipartFile file = new MockMultipartFile("file", "small.jpg", "image/jpeg", content);
        when(cosManager.putPictureObject(anyString(), any(File.class), any(PicOperations.class)))
                .thenAnswer(invocation -> {
                    String originalKey = invocation.getArgument(0);
                    String baseKey = originalKey.substring(0, originalKey.lastIndexOf('.'));
                    return uploadResult(baseKey + "_display.webp", 4 * 1024);
                });

        PictureUploadResult result = fileManager.uploadPicture(file, "public/1");

        assertTrue(result.getUrl().endsWith("_display.webp"));
        assertNull(result.getThumbnailUrl());
        ArgumentCaptor<PicOperations> operationsCaptor = ArgumentCaptor.forClass(PicOperations.class);
        verify(cosManager).putPictureObject(anyString(), any(File.class), operationsCaptor.capture());
        assertEquals(1, operationsCaptor.getValue().getRules().size());
    }

    @Test
    void shouldKeepOriginalGifWithoutDerivativeRules() {
        byte[] content = gifBytes(30 * 1024);
        MockMultipartFile file = new MockMultipartFile("file", "animated.gif", "image/gif", content);
        when(cosManager.putPictureObject(anyString(), any(File.class), any(PicOperations.class)))
                .thenReturn(uploadResult());

        PictureUploadResult result = fileManager.uploadPicture(file, "public/1");

        assertEquals(result.getOriginalUrl(), result.getUrl());
        assertNull(result.getThumbnailUrl());
        ArgumentCaptor<PicOperations> operationsCaptor = ArgumentCaptor.forClass(PicOperations.class);
        verify(cosManager).putPictureObject(anyString(), any(File.class), operationsCaptor.capture());
        assertTrue(operationsCaptor.getValue().getRules().isEmpty());
    }

    private PutObjectResult uploadResult(Object... keyAndSizePairs) {
        ImageInfo imageInfo = new ImageInfo();
        imageInfo.setWidth(1200);
        imageInfo.setHeight(800);
        imageInfo.setFormat("JPEG");
        OriginalInfo originalInfo = new OriginalInfo();
        originalInfo.setImageInfo(imageInfo);
        CIUploadResult ciUploadResult = new CIUploadResult();
        ciUploadResult.setOriginalInfo(originalInfo);
        ProcessResults processResults = new ProcessResults();
        CIObject[] objects = new CIObject[keyAndSizePairs.length / 2];
        for (int i = 0; i < keyAndSizePairs.length; i += 2) {
            CIObject object = new CIObject();
            object.setKey((String) keyAndSizePairs[i]);
            object.setSize((Integer) keyAndSizePairs[i + 1]);
            objects[i / 2] = object;
        }
        processResults.setObjectList(Arrays.asList(objects));
        ciUploadResult.setProcessResults(processResults);
        PutObjectResult putObjectResult = new PutObjectResult();
        putObjectResult.setCiUploadResult(ciUploadResult);
        return putObjectResult;
    }

    @Test
    void extractsCosColorWithoutAnotherNetworkRequestAndLeavesInvalidColorNull() {
        for (String color : new String[]{"0x080e0", null, "invalid"}) {
            PutObjectResult result = uploadResult();
            result.getCiUploadResult().getOriginalInfo().getImageInfo().setAve(color);
            when(cosManager.putPictureObject(anyString(), any(File.class), any(PicOperations.class))).thenReturn(result);
            PictureUploadResult uploaded = fileManager.uploadPicture(
                    new MockMultipartFile("file", "photo.jpg", "image/jpeg", jpegBytes(1000)), "public/1");
            assertEquals(color != null && color.startsWith("0x") ? "#0080e0" : null, uploaded.getPicColor());
        }
    }

    @Test
    void shouldUseCanonicalKeyWhenCosReturnsLeadingSlash() {
        when(cosManager.putPictureObject(anyString(), any(File.class), any(PicOperations.class)))
                .thenAnswer(invocation -> {
                    PicOperations operations = invocation.getArgument(2);
                    return uploadResult(operations.getRules().get(0).getFileId(), 1000,
                            operations.getRules().get(1).getFileId(), 500);
                });
        PictureUploadResult result = fileManager.uploadPicture(
                new MockMultipartFile("file", "test.jpg", "image/jpeg", jpegBytes(30 * 1024)), "public/1");
        assertTrue(result.getUrl().startsWith("https://test-bucket.cos.ap-test.myqcloud.com/public/1/"));
        assertTrue(result.getThumbnailUrl().startsWith("https://test-bucket.cos.ap-test.myqcloud.com/public/1/"));
    }

    @Test
    void shouldDiscardDerivativesThatAreNotSmaller() {
        when(cosManager.putPictureObject(anyString(), any(File.class), any(PicOperations.class)))
                .thenAnswer(invocation -> {
                    PicOperations operations = invocation.getArgument(2);
                    return uploadResult(operations.getRules().get(0).getFileId(), 40 * 1024,
                            operations.getRules().get(1).getFileId(), 30 * 1024);
                });
        PictureUploadResult result = fileManager.uploadPicture(
                new MockMultipartFile("file", "test.jpg", "image/jpeg", jpegBytes(30 * 1024)), "public/1");
        assertEquals(result.getOriginalUrl(), result.getUrl());
        assertNull(result.getThumbnailUrl());
        verify(publicCleanup, org.mockito.Mockito.times(2)).deleteQuietly(anyString(), anyString());
    }

    @Test
    void shouldFallbackWhenNoProcessResultsReturned() {
        PutObjectResult response = uploadResult();
        response.getCiUploadResult().setProcessResults(null);
        when(cosManager.putPictureObject(anyString(), any(File.class), any(PicOperations.class)))
                .thenReturn(response);
        PictureUploadResult result = fileManager.uploadPicture(
                new MockMultipartFile("file", "test.jpg", "image/jpeg", jpegBytes(30 * 1024)), "public/1");
        assertEquals(result.getOriginalUrl(), result.getUrl());
        assertNull(result.getThumbnailUrl());
    }

    @Test
    void shouldCleanupAllPlannedKeysWhenUploadFails() {
        when(cosManager.putPictureObject(anyString(), any(File.class), any(PicOperations.class)))
                .thenThrow(new IllegalStateException("upload timeout"));
        assertThrows(IllegalStateException.class, () -> fileManager.uploadPicture(
                new MockMultipartFile("file", "test.jpg", "image/jpeg", jpegBytes(30 * 1024)), "public/1"));
        ArgumentCaptor<String> keys = ArgumentCaptor.forClass(String.class);
        verify(publicCleanup, org.mockito.Mockito.times(3)).deleteQuietly(keys.capture(), anyString());
        assertEquals(3, keys.getAllValues().stream().distinct().count());
        assertTrue(keys.getAllValues().stream().allMatch(key -> key.startsWith("https://test-bucket.cos.ap-test.myqcloud.com/public/1/")));
    }

    @Test
    void shouldCleanupWhenOriginalMetadataIsInvalid() {
        when(cosManager.putPictureObject(anyString(), any(File.class), any(PicOperations.class)))
                .thenReturn(new PutObjectResult());
        assertThrows(BusinessException.class, () -> fileManager.uploadPicture(
                new MockMultipartFile("file", "test.jpg", "image/jpeg", jpegBytes(30 * 1024)), "public/1"));
        verify(publicCleanup, org.mockito.Mockito.times(3)).deleteQuietly(anyString(), anyString());
    }

    @Test
    void shouldHonorDisabledFlagAndExactThreshold() {
        when(cosManager.putPictureObject(anyString(), any(File.class), any(PicOperations.class)))
                .thenReturn(uploadResult());
        fileManager.uploadPicture(
                new MockMultipartFile("file", "test.jpg", "image/jpeg", jpegBytes(20 * 1024)), "public/1");
        ReflectionTestUtils.setField(fileManager, "derivativeEnabled", false);
        fileManager.uploadPicture(
                new MockMultipartFile("file", "test.jpg", "image/jpeg", jpegBytes(30 * 1024)), "public/1");
        ArgumentCaptor<PicOperations> operations = ArgumentCaptor.forClass(PicOperations.class);
        verify(cosManager, org.mockito.Mockito.times(2))
                .putPictureObject(anyString(), any(File.class), operations.capture());
        assertEquals(1, operations.getAllValues().get(0).getRules().size());
        assertTrue(operations.getAllValues().get(1).getRules().isEmpty());
    }

    @Test
    void shouldNotReencodeWebpMainImage() {
        when(cosManager.putPictureObject(anyString(), any(File.class), any(PicOperations.class)))
                .thenReturn(uploadResult());
        byte[] bytes = new byte[30 * 1024];
        System.arraycopy("RIFF1234WEBP".getBytes(), 0, bytes, 0, 12);
        PictureUploadResult result = fileManager.uploadPicture(
                new MockMultipartFile("file", "test.webp", "image/webp", bytes), "public/1");
        assertEquals(result.getOriginalUrl(), result.getUrl());
        ArgumentCaptor<PicOperations> operations = ArgumentCaptor.forClass(PicOperations.class);
        verify(cosManager).putPictureObject(anyString(), any(File.class), operations.capture());
        assertEquals(1, operations.getValue().getRules().size());
        assertTrue(operations.getValue().getRules().get(0).getFileId().endsWith("_thumbnail.webp"));
    }

    @Test
    void privateOriginalAndDerivativesStayInPrivateBucketAndRegisterBeforeCosWrite() {
        when(cosManager.putPrivatePictureObject(anyString(), anyString(), any(File.class), any(PicOperations.class)))
                .thenAnswer(invocation -> {
                    String key = invocation.getArgument(1);
                    String base = key.substring(0, key.lastIndexOf('.'));
                    return uploadResult(base + "_display.webp", 10000, base + "_thumbnail.webp", 1000);
                });
        PictureUploadResult result = fileManager.uploadPrivatePicture(new MockMultipartFile("file", "photo.JPG",
                "image/jpeg", jpegBytes(30000)), 101L);
        assertTrue(result.getOriginalUrl().startsWith("https://private-test-1250000000.cos.ap-test.myqcloud.com/space/101/"));
        assertTrue(result.getUrl().endsWith("_display.webp"));
        assertTrue(result.getThumbnailUrl().endsWith("_thumbnail.webp"));
        ArgumentCaptor<PicOperations> operations = ArgumentCaptor.forClass(PicOperations.class);
        org.mockito.InOrder order = org.mockito.Mockito.inOrder(privateCleanup, cosManager);
        order.verify(privateCleanup).registerUpload(org.mockito.ArgumentMatchers.eq(101L), org.mockito.ArgumentMatchers.argThat(urls -> urls.size() == 3));
        order.verify(cosManager).putPrivatePictureObject(org.mockito.ArgumentMatchers.eq("private-test-1250000000"),
                anyString(), any(File.class), operations.capture());
        operations.getValue().getRules().forEach(rule -> {
            assertEquals("private-test-1250000000", rule.getBucket());
            assertTrue(rule.getFileId().startsWith("/space/101/"));
        });
        verify(cosManager, never()).putPictureObject(anyString(), any(File.class), any(PicOperations.class));
        verify(cosManager, never()).deleteObject(anyString());
    }

    @Test
    void privateInvalidFileIsRejectedBeforeRegistrationOrUpload() {
        assertThrows(BusinessException.class, () -> fileManager.uploadPrivatePicture(new MockMultipartFile("file",
                "fake.jpg", "image/jpeg", new byte[]{1, 2, 3}), 101L));
        org.mockito.Mockito.verifyNoInteractions(privateCleanup, cosManager);
    }

    @Test
    void privateUploadStopsBeforeCosIfDurableRegistrationFails() {
        org.mockito.Mockito.doThrow(new IllegalStateException("database down")).when(privateCleanup)
                .registerUpload(org.mockito.ArgumentMatchers.eq(101L), org.mockito.ArgumentMatchers.anyCollection());
        assertThrows(IllegalStateException.class, () -> fileManager.uploadPrivatePicture(new MockMultipartFile("file",
                "photo.jpg", "image/jpeg", jpegBytes(30000)), 101L));
        org.mockito.Mockito.verifyNoInteractions(cosManager);
    }

    @Test
    void privateCosFailureSchedulesEveryPlannedFileAndSanitizesError() {
        when(cosManager.putPrivatePictureObject(anyString(), anyString(), any(File.class), any(PicOperations.class)))
                .thenThrow(new IllegalStateException("sensitive COS address"));
        BusinessException error = assertThrows(BusinessException.class, () -> fileManager.uploadPrivatePicture(
                new MockMultipartFile("file", "photo.jpg", "image/jpeg", jpegBytes(30000)), 101L));
        assertEquals("私有图片上传失败", error.getMessage());
        verify(privateCleanup).abandon(org.mockito.ArgumentMatchers.eq(101L), org.mockito.ArgumentMatchers.argThat(urls -> urls.size() == 3));
        verify(cosManager, never()).deleteObject(anyString());
    }

    @Test
    void privateUnusedAndMissingDerivativesAreScheduledForCleanup() {
        when(cosManager.putPrivatePictureObject(anyString(), anyString(), any(File.class), any(PicOperations.class)))
                .thenAnswer(invocation -> {
                    String key = invocation.getArgument(1);
                    return uploadResult(key.substring(0, key.lastIndexOf('.')) + "_display.webp", 40000);
                });
        PictureUploadResult result = fileManager.uploadPrivatePicture(new MockMultipartFile("file", "photo.jpg",
                "image/jpeg", jpegBytes(30000)), 101L);
        assertEquals(result.getOriginalUrl(), result.getUrl()); assertNull(result.getThumbnailUrl());
        verify(privateCleanup, org.mockito.Mockito.times(2)).abandon(org.mockito.ArgumentMatchers.eq(101L),
                org.mockito.ArgumentMatchers.argThat(urls -> urls.size() == 1));
        verify(cosManager, never()).deleteObject(anyString());
    }

    private byte[] jpegBytes(int size) {
        byte[] bytes = new byte[size];
        bytes[0] = (byte) 0xFF;
        bytes[1] = (byte) 0xD8;
        bytes[2] = (byte) 0xFF;
        return bytes;
    }

    private byte[] gifBytes(int size) {
        byte[] bytes = new byte[size];
        byte[] signature = "GIF89a".getBytes();
        System.arraycopy(signature, 0, bytes, 0, signature.length);
        return bytes;
    }
}
