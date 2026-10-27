package learn.gjx.tuku1.service.impl;

import learn.gjx.tuku1.exception.BusinessException;
import learn.gjx.tuku1.manager.BingImageSearchManager;
import learn.gjx.tuku1.manager.FileManager;
import learn.gjx.tuku1.manager.PictureFileCleanupManager;
import learn.gjx.tuku1.model.dto.file.PictureUploadResult;
import learn.gjx.tuku1.model.dto.picture.PictureReviewRequest;
import learn.gjx.tuku1.model.dto.picture.PictureUploadRequest;
import learn.gjx.tuku1.model.entity.Picture;
import learn.gjx.tuku1.model.entity.User;
import learn.gjx.tuku1.model.enums.PictureReviewStatusEnum;
import learn.gjx.tuku1.model.enums.PictureUploadStatusEnum;
import learn.gjx.tuku1.model.vo.PictureVO;
import learn.gjx.tuku1.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PictureServiceImplTest {

    private PictureServiceImpl pictureService;
    private FileManager fileManager;
    private PictureFileCleanupManager cleanupManager;
    private UserService userService;
    private TransactionTemplate transactionTemplate;

    @BeforeEach
    void setUp() {
        pictureService = spy(new PictureServiceImpl());
        fileManager = mock(FileManager.class);
        cleanupManager = mock(PictureFileCleanupManager.class);
        userService = mock(UserService.class);
        transactionTemplate = mock(TransactionTemplate.class);
        ReflectionTestUtils.setField(pictureService, "fileManager", fileManager);
        ReflectionTestUtils.setField(pictureService, "pictureFileCleanupManager", cleanupManager);
        ReflectionTestUtils.setField(pictureService, "bingImageSearchManager", mock(BingImageSearchManager.class));
        ReflectionTestUtils.setField(pictureService, "userService", userService);
        ReflectionTestUtils.setField(pictureService, "transactionTemplate", transactionTemplate);
        ReflectionTestUtils.setField(pictureService, "pictureAccessManager",
                new learn.gjx.tuku1.manager.space.PictureAccessManager());
    }

    @Test
    void shouldDeleteNewCosFileWhenDatabaseTransactionFails() {
        User user = user(1L, 0);
        MockMultipartFile file = new MockMultipartFile("file", "test.jpg", "image/jpeg", new byte[]{1});
        PictureUploadResult uploadResult = uploadResult("https://cos.example.com/new.jpg");
        when(fileManager.uploadPicture(file, "public/1")).thenReturn(uploadResult);
        when(transactionTemplate.execute(any(TransactionCallback.class)))
                .thenThrow(new BusinessException(50001, "数据库失败"));

        assertThrows(BusinessException.class,
                () -> pictureService.uploadPicture(file, new PictureUploadRequest(), user));

        verify(cleanupManager).deleteQuietly(uploadResult.getUrl(), "rollback-upload");
    }

    @Test
    void shouldDeleteAllNewDerivativeFilesWhenDatabaseTransactionFails() {
        User user = user(1L, 0);
        MockMultipartFile file = new MockMultipartFile("file", "test.jpg", "image/jpeg", new byte[]{1});
        PictureUploadResult uploadResult = uploadResult("https://cos.example.com/display.webp");
        uploadResult.setOriginalUrl("https://cos.example.com/original.jpg");
        uploadResult.setThumbnailUrl("https://cos.example.com/thumbnail.webp");
        when(fileManager.uploadPicture(file, "public/1")).thenReturn(uploadResult);
        when(transactionTemplate.execute(any(TransactionCallback.class)))
                .thenThrow(new BusinessException(50001, "数据库失败"));

        assertThrows(BusinessException.class,
                () -> pictureService.uploadPicture(file, new PictureUploadRequest(), user));

        verify(cleanupManager).deleteQuietly(uploadResult.getOriginalUrl(), "rollback-upload");
        verify(cleanupManager).deleteQuietly(uploadResult.getUrl(), "rollback-upload");
        verify(cleanupManager).deleteQuietly(uploadResult.getThumbnailUrl(), "rollback-upload");
    }

    @Test
    void shouldDeleteOldCosFileAfterReplacementCommitted() {
        User user = user(1L, 0);
        MockMultipartFile file = new MockMultipartFile("file", "test.jpg", "image/jpeg", new byte[]{1});
        Picture oldPicture = new Picture();
        oldPicture.setId(10L);
        oldPicture.setUserId(1L);
        oldPicture.setUrl("https://cos.example.com/display.webp");
        oldPicture.setOriginalUrl("https://cos.example.com/original.jpg");
        oldPicture.setThumbnailUrl("https://cos.example.com/thumb.webp");
        PictureUploadRequest request = new PictureUploadRequest();
        request.setId(10L);
        PictureUploadResult uploadResult = uploadResult("https://cos.example.com/new.jpg");
        doReturn(oldPicture).when(pictureService).getById(10L);
        when(fileManager.uploadPicture(file, "public/1")).thenReturn(uploadResult);
        when(transactionTemplate.execute(any(TransactionCallback.class))).thenReturn(new PictureVO());

        pictureService.uploadPicture(file, request, user);

        verify(cleanupManager).deleteQuietly(oldPicture.getOriginalUrl(), "replace-picture");
        verify(cleanupManager).deleteQuietly(oldPicture.getUrl(), "replace-picture");
        verify(cleanupManager).deleteQuietly(oldPicture.getThumbnailUrl(), "replace-picture");
    }

    @Test
    void shouldDeleteSharedOriginalAndDisplayFileOnlyOnce() {
        User user = user(1L, 0);
        MockMultipartFile file = new MockMultipartFile("file", "test.jpg", "image/jpeg", new byte[]{1});
        Picture oldPicture = new Picture();
        oldPicture.setId(10L);
        oldPicture.setUserId(1L);
        oldPicture.setUrl("https://cos.example.com/shared.jpg");
        oldPicture.setOriginalUrl("https://cos.example.com/shared.jpg");
        PictureUploadRequest request = new PictureUploadRequest();
        request.setId(10L);
        PictureUploadResult uploadResult = uploadResult("https://cos.example.com/new.jpg");
        doReturn(oldPicture).when(pictureService).getById(10L);
        when(fileManager.uploadPicture(file, "public/1")).thenReturn(uploadResult);
        when(transactionTemplate.execute(any(TransactionCallback.class))).thenReturn(new PictureVO());

        pictureService.uploadPicture(file, request, user);

        verify(cleanupManager).deleteQuietly(oldPicture.getUrl(), "replace-picture");
    }

    @Test
    void shouldNotReviewDraftPicture() {
        User admin = user(1L, 1);
        Picture draft = new Picture();
        draft.setId(10L);
        draft.setUploadStatus(PictureUploadStatusEnum.DRAFT.getValue());
        doReturn(draft).when(pictureService).getById(10L);
        when(userService.isAdmin(admin)).thenReturn(true);
        PictureReviewRequest request = new PictureReviewRequest();
        request.setId(10L);
        request.setReviewStatus(PictureReviewStatusEnum.PASS.getValue());

        assertThrows(BusinessException.class, () -> pictureService.reviewPicture(request, admin));
    }

    private User user(Long id, int role) {
        User user = new User();
        user.setId(id);
        user.setUserRole(role);
        return user;
    }

    private PictureUploadResult uploadResult(String url) {
        PictureUploadResult result = new PictureUploadResult();
        result.setUrl(url);
        return result;
    }
}
