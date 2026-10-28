package learn.gjx.tuku1.service.impl;

import cn.hutool.core.collection.CollUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import learn.gjx.tuku1.exception.BusinessException;
import learn.gjx.tuku1.exception.ErrorCode;
import learn.gjx.tuku1.manager.BingImageSearchManager;
import learn.gjx.tuku1.manager.FileManager;
import learn.gjx.tuku1.manager.AiApplyClaim;
import learn.gjx.tuku1.manager.AiTaskStore;
import learn.gjx.tuku1.manager.PictureFileCleanupManager;
import learn.gjx.tuku1.manager.cache.PictureListRevision;
import learn.gjx.tuku1.manager.cache.PublicPicturePageCache;
import learn.gjx.tuku1.manager.cache.PublicPictureQuery;
import learn.gjx.tuku1.manager.space.PictureAccessManager;
import learn.gjx.tuku1.manager.space.PrivatePictureWriteManager;
import learn.gjx.tuku1.mapper.PictureMapper;
import learn.gjx.tuku1.model.dto.file.PictureUploadResult;
import learn.gjx.tuku1.model.dto.picture.PictureEditRequest;
import learn.gjx.tuku1.model.dto.picture.PictureProcessRequest;
import learn.gjx.tuku1.model.dto.picture.PictureProcessRule;
import learn.gjx.tuku1.model.dto.picture.PictureQueryRequest;
import learn.gjx.tuku1.model.dto.picture.PictureReviewRequest;
import learn.gjx.tuku1.model.dto.picture.PictureUploadByBatchRequest;
import learn.gjx.tuku1.model.dto.picture.PictureUploadByUrlRequest;
import learn.gjx.tuku1.model.dto.picture.PictureUpdateRequest;
import learn.gjx.tuku1.model.dto.picture.PictureUploadRequest;
import learn.gjx.tuku1.model.entity.Picture;
import learn.gjx.tuku1.model.entity.User;
import learn.gjx.tuku1.model.enums.PictureReviewStatusEnum;
import learn.gjx.tuku1.model.enums.PictureUploadStatusEnum;
import learn.gjx.tuku1.model.vo.PictureProcessResult;
import learn.gjx.tuku1.model.vo.PictureVO;
import learn.gjx.tuku1.model.vo.UserVO;
import learn.gjx.tuku1.service.PictureService;
import learn.gjx.tuku1.service.UserService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * {@link PictureService} 的具体实现，依托 MyBatis-Plus 提供 CRUD 与业务扩展。
 */
@Service
@Slf4j
public class PictureServiceImpl extends ServiceImpl<PictureMapper, Picture>
        implements PictureService {

    private static final int DEFAULT_BATCH_UPLOAD_COUNT = 10;

    private static final int MAX_BATCH_UPLOAD_COUNT = 30;

    @Resource
    private FileManager fileManager;

    @Resource
    private AiTaskStore aiTaskStore;

    @Resource
    private PictureFileCleanupManager pictureFileCleanupManager;

    @Resource
    private BingImageSearchManager bingImageSearchManager;

    @Resource
    private TransactionTemplate transactionTemplate;

    @Resource
    private PictureListRevision pictureListRevision;

    @Resource
    private PublicPicturePageCache publicPicturePageCache;

    @Resource
    private UserService userService;

    @Resource
    private PictureAccessManager pictureAccessManager;

    @Resource
    private PrivatePictureWriteManager privatePictureWriteManager;

    @Override
    public Page<PictureVO> listSpacePicturePage(PictureQueryRequest input, HttpServletRequest request) {
        PictureQueryRequest query = PublicPictureQuery.normalizeFilters(input);
        if (query.getPageSize() > 20) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "每页数量不能超过 20");
        }
        pictureAccessManager.requireSpacePermission(query.getSpaceId(), userService.getLoginUser(request), "picture:view");
        // The space owner can see drafts and any review status; private content is not publicly reviewed.
        Page<Picture> page = this.page(new Page<>(query.getCurrent(), query.getPageSize()), getQueryWrapper(query));
        return getPictureVOPage(page, request);
    }

    @Override
    public Picture getPrivatePictureForRead(Long id, HttpServletRequest request) {
        User user = userService.getLoginUser(request);
        if (id == null || id <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "图片 ID 不合法");
        }
        Picture picture = this.getById(id);
        if (picture == null || picture.getSpaceId() == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "私有图片不存在");
        }
        pictureAccessManager.requireSpacePermission(picture.getSpaceId(), user, "picture:view");
        return picture;
    }

    @Override
    public Page<PictureVO> listPublicPicturePage(PictureQueryRequest queryRequest, HttpServletRequest request) {
        PictureQueryRequest query = PublicPictureQuery.normalize(queryRequest);
        Page<Picture> picturePage = publicPicturePageCache.get(query, () -> {
            QueryWrapper<Picture> wrapper = getQueryWrapper(query);
            return this.page(new Page<>(query.getCurrent(), query.getPageSize()), wrapper);
        });
        // Do not cache user profile snapshots or any caller/session-specific response.
        return getPictureVOPage(picturePage, request);
    }

    @Override
    public PictureVO uploadPicture(MultipartFile multipartFile, PictureUploadRequest pictureUploadRequest, User loginUser) {
        return uploadPictureInternal(multipartFile, pictureUploadRequest, loginUser, null);
    }

    @Override
    public PictureVO uploadAiPicture(MultipartFile file, PictureUploadRequest input, User user, AiApplyClaim claim) {
        Objects.requireNonNull(claim, "AI claim");
        return uploadPictureInternal(file, input, user, claim);
    }

    private PictureVO uploadPictureInternal(MultipartFile multipartFile, PictureUploadRequest pictureUploadRequest,
                                            User loginUser, AiApplyClaim claim) {
        // 1. 校验必要参数
        if (loginUser == null) {
            throw new BusinessException(ErrorCode.NOT_LOGIN_ERROR);
        }
        if (multipartFile == null || multipartFile.isEmpty()) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "File cannot be empty");
        }
        validatePictureMetadata(pictureUploadRequest == null ? null : pictureUploadRequest.getName(),
                pictureUploadRequest == null ? null : pictureUploadRequest.getIntroduction(),
                pictureUploadRequest == null ? null : pictureUploadRequest.getCategory(),
                pictureUploadRequest == null ? null : pictureUploadRequest.getTags());

        Long pictureId = null;
        if (pictureUploadRequest != null) {
            pictureId = pictureUploadRequest.getId();
        }
        Picture oldPicture = getAuthorizedOldPicture(pictureId, loginUser);

        Long spaceId = resolveUploadSpace(pictureUploadRequest == null ? null : pictureUploadRequest.getSpaceId(), oldPicture, loginUser);
        if (spaceId != null) {
            PictureUploadResult uploaded = fileManager.uploadPrivatePicture(multipartFile, spaceId);
            return persistPrivateUpload(uploaded, oldPicture, spaceId,
                    pictureUploadRequest == null ? null : pictureUploadRequest.getName(),
                    pictureUploadRequest == null ? null : pictureUploadRequest.getIntroduction(),
                    pictureUploadRequest == null ? null : pictureUploadRequest.getCategory(),
                    pictureUploadRequest == null ? null : pictureUploadRequest.getTags(), null, loginUser,
                    pictureUploadRequest == null ? null : pictureUploadRequest.getCollaborationToken(),
                    pictureUploadRequest == null ? null : pictureUploadRequest.getCollaborationRevision(), claim);
        }

        // 2. 上传图片到对象存储
        String uploadPathPrefix = getUploadPathPrefix(oldPicture, loginUser);
        PictureUploadResult uploadResult = fileManager.uploadPicture(multipartFile, uploadPathPrefix);

        return persistUploadedPicture(uploadResult, oldPicture, pictureId,
                pictureUploadRequest == null ? null : pictureUploadRequest.getName(),
                pictureUploadRequest == null ? null : pictureUploadRequest.getIntroduction(),
                pictureUploadRequest == null ? null : pictureUploadRequest.getCategory(),
                pictureUploadRequest == null ? null : pictureUploadRequest.getTags(),
                null,
                false,
                loginUser, claim);
    }

    @Override
    public PictureVO uploadPictureByUrl(PictureUploadByUrlRequest pictureUploadByUrlRequest, User loginUser) {
        return uploadPictureByUrl(pictureUploadByUrlRequest, loginUser, false);
    }

    private PictureVO uploadPictureByUrl(PictureUploadByUrlRequest pictureUploadByUrlRequest, User loginUser,
                                         boolean completed) {
        if (loginUser == null) {
            throw new BusinessException(ErrorCode.NOT_LOGIN_ERROR);
        }
        if (pictureUploadByUrlRequest == null || StrUtil.isBlank(pictureUploadByUrlRequest.getFileUrl())) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "图片 URL 不能为空");
        }
        if (pictureUploadByUrlRequest.getFileUrl().trim().length() > 1024) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "图片 URL 过长");
        }
        validatePictureMetadata(pictureUploadByUrlRequest.getName(), pictureUploadByUrlRequest.getIntroduction(),
                pictureUploadByUrlRequest.getCategory(), pictureUploadByUrlRequest.getTags());
        Long pictureId = pictureUploadByUrlRequest.getId();
        Picture oldPicture = getAuthorizedOldPicture(pictureId, loginUser);

        Long spaceId = resolveUploadSpace(pictureUploadByUrlRequest.getSpaceId(), oldPicture, loginUser);
        if (spaceId != null) {
            PictureUploadResult uploaded = fileManager.uploadPrivatePictureByUrl(pictureUploadByUrlRequest.getFileUrl(), spaceId);
            return persistPrivateUpload(uploaded, oldPicture, spaceId, pictureUploadByUrlRequest.getName(),
                    pictureUploadByUrlRequest.getIntroduction(), pictureUploadByUrlRequest.getCategory(),
                    pictureUploadByUrlRequest.getTags(), pictureUploadByUrlRequest.getFileUrl().trim(), loginUser);
        }

        String uploadPathPrefix = getUploadPathPrefix(oldPicture, loginUser);
        PictureUploadResult uploadResult = fileManager.uploadPictureByUrl(pictureUploadByUrlRequest.getFileUrl(), uploadPathPrefix);

        return persistUploadedPicture(uploadResult, oldPicture, pictureId,
                pictureUploadByUrlRequest.getName(),
                pictureUploadByUrlRequest.getIntroduction(),
                pictureUploadByUrlRequest.getCategory(),
                pictureUploadByUrlRequest.getTags(),
                pictureUploadByUrlRequest.getFileUrl(),
                completed,
                loginUser);
    }

    @Override
    public Integer uploadPictureByBatch(PictureUploadByBatchRequest pictureUploadByBatchRequest, User loginUser) {
        if (loginUser == null) {
            throw new BusinessException(ErrorCode.NOT_LOGIN_ERROR);
        }
        if (!userService.isAdmin(loginUser)) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "仅管理员可批量导入图片");
        }
        if (pictureUploadByBatchRequest == null || StrUtil.isBlank(pictureUploadByBatchRequest.getSearchText())) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "搜索关键词不能为空");
        }
        rejectPrivateUpload(pictureUploadByBatchRequest.getSpaceId(), loginUser);
        String searchText = pictureUploadByBatchRequest.getSearchText().trim();
        String namePrefix = StrUtil.blankToDefault(pictureUploadByBatchRequest.getNamePrefix(), searchText).trim();
        if (searchText.length() > 50 || namePrefix.length() > 100) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "搜索关键词或名称前缀过长");
        }
        validatePictureMetadata(namePrefix, null, pictureUploadByBatchRequest.getCategory(),
                pictureUploadByBatchRequest.getTags());
        Integer count = pictureUploadByBatchRequest.getCount();
        if (count == null) {
            count = DEFAULT_BATCH_UPLOAD_COUNT;
        }
        if (count <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "导入数量必须大于 0");
        }
        if (count > MAX_BATCH_UPLOAD_COUNT) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "导入数量不能超过 30");
        }
        Integer offset = pictureUploadByBatchRequest.getOffset();
        if (offset == null) {
            offset = 0;
        }
        if (offset < 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "偏移量不能小于 0");
        }

        List<String> candidateUrlList = bingImageSearchManager.search(searchText);
        if (offset >= candidateUrlList.size()) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "偏移量超过可抓取图片数量");
        }

        int successCount = 0;
        for (int i = offset; i < candidateUrlList.size(); i++) {
            if (successCount >= count) {
                break;
            }
            String imageUrl = candidateUrlList.get(i);
            PictureUploadByUrlRequest uploadByUrlRequest = new PictureUploadByUrlRequest();
            uploadByUrlRequest.setFileUrl(imageUrl);
            uploadByUrlRequest.setName(namePrefix + "-" + (successCount + 1));
            uploadByUrlRequest.setCategory(pictureUploadByBatchRequest.getCategory());
            uploadByUrlRequest.setTags(pictureUploadByBatchRequest.getTags());
            try {
                uploadPictureByUrl(uploadByUrlRequest, loginUser, true);
                successCount++;
            } catch (Exception e) {
                log.warn("批量导入单张图片失败，searchText: {}, fileUrl: {}", searchText, imageUrl, e);
            }
        }

        if (successCount == 0) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "批量导入失败");
        }
        return successCount;
    }

    @Override
    public boolean deletePicture(Long pictureId, User loginUser) {
        if (pictureId == null || pictureId <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "图片 ID 不合法");
        }
        if (loginUser == null) { throw new BusinessException(ErrorCode.NOT_LOGIN_ERROR); }
        Picture oldPicture = this.getById(pictureId);
        if (oldPicture == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "图片不存在");
        }
        if (oldPicture.getSpaceId() != null) {
            pictureAccessManager.requireSpacePermission(oldPicture.getSpaceId(), loginUser, "picture:delete");
            return privatePictureWriteManager.delete(oldPicture, loginUser);
        }
        if (!userService.isAdmin(loginUser)) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "仅管理员可删除公共图片");
        }
        Boolean removed = transactionTemplate.execute(status -> {
            Picture current = baseMapper.selectForUpdate(pictureId);
            if (current == null) return false;
            if (current.getSpaceId() != null) throw new BusinessException(ErrorCode.OPERATION_ERROR, "图片所属空间已变更");
            boolean result = this.removeById(pictureId);
            if (result) {
                pictureListRevision.advance();
                cleanupManagedPictureFiles(current, "delete-picture");
            }
            return result;
        });
        if (!Boolean.TRUE.equals(removed)) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "删除失败");
        }
        return true;
    }

    @Override
    public int cleanExpiredDraftPictures(Date expireBefore, int batchSize) {
        if (expireBefore == null || batchSize <= 0) {
            return 0;
        }
        int safeBatchSize = Math.min(batchSize, 200);
        List<Picture> drafts = this.lambdaQuery()
                .isNull(Picture::getSpaceId)
                .eq(Picture::getUploadStatus, PictureUploadStatusEnum.DRAFT.getValue())
                .lt(Picture::getCreateTime, expireBefore)
                .last("LIMIT " + safeBatchSize)
                .list();
        int privateRemoved = cleanPrivateDrafts(expireBefore, safeBatchSize - drafts.size());
        if (CollUtil.isEmpty(drafts)) { return privateRemoved; }
        Integer removed = transactionTemplate.execute(status -> {
            int count = 0;
            for (Picture candidate : drafts) {
                Picture current = baseMapper.selectForUpdate(candidate.getId());
                if (current == null || current.getSpaceId() != null
                        || !Objects.equals(current.getUploadStatus(), PictureUploadStatusEnum.DRAFT.getValue())
                        || !current.getCreateTime().before(expireBefore)) continue;
                if (!this.removeById(current.getId())) throw new BusinessException(ErrorCode.OPERATION_ERROR, "清理图片草稿失败");
                cleanupManagedPictureFiles(current, "expired-draft");
                count++;
            }
            if (count > 0) pictureListRevision.advance();
            return count;
        });
        if (removed == null) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "清理图片草稿失败");
        }
        return removed + privateRemoved;
    }

    private int cleanPrivateDrafts(Date expireBefore, int batchSize) {
        if (batchSize <= 0) { return 0; }
        List<Picture> drafts = this.lambdaQuery().isNotNull(Picture::getSpaceId)
                .eq(Picture::getUploadStatus, PictureUploadStatusEnum.DRAFT.getValue())
                .lt(Picture::getCreateTime, expireBefore).orderByAsc(Picture::getId)
                .last("LIMIT " + batchSize).list();
        int removed = 0;
        for (Picture draft : drafts) {
            try {
                if (privatePictureWriteManager.deleteExpiredDraft(draft, expireBefore)) { removed++; }
            } catch (RuntimeException error) {
                log.error("私有草稿清理失败，保留记录和用量，pictureId={}", draft.getId(), error);
            }
        }
        return removed;
    }

    private Long resolveUploadSpace(Long requested, Picture oldPicture, User user) {
        Long target = oldPicture == null ? requested : oldPicture.getSpaceId();
        if (oldPicture != null && requested != null && !Objects.equals(requested, target)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "替换不支持更换图片所属空间");
        }
        if (target != null) { privatePictureWriteManager.prepare(target, user); }
        return target;
    }

    private PictureVO persistPrivateUpload(PictureUploadResult uploaded, Picture oldPicture, Long spaceId,
                                            String name, String introduction, String category, List<String> tags,
                                            String sourceUrl, User user) {
        return persistPrivateUpload(uploaded, oldPicture, spaceId, name, introduction, category, tags, sourceUrl, user, null, null);
    }

    private PictureVO persistPrivateUpload(PictureUploadResult uploaded, Picture oldPicture, Long spaceId,
                                            String name, String introduction, String category, List<String> tags,
                                            String sourceUrl, User user, String editToken, Long editRevision) {
        return persistPrivateUpload(uploaded, oldPicture, spaceId, name, introduction, category, tags, sourceUrl, user, editToken, editRevision, null);
    }

    private PictureVO persistPrivateUpload(PictureUploadResult uploaded, Picture oldPicture, Long spaceId,
                                            String name, String introduction, String category, List<String> tags,
                                            String sourceUrl, User user, String editToken, Long editRevision, AiApplyClaim claim) {
        Picture picture = new Picture();
        org.springframework.beans.BeanUtils.copyProperties(uploaded, picture);
        picture.setSpaceId(spaceId);
        picture.setId(oldPicture == null ? null : oldPicture.getId());
        picture.setName(StrUtil.blankToDefault(name, uploaded.getPicName()));
        picture.setIntroduction(introduction); picture.setCategory(category);
        picture.setTags(tags == null ? null : JSONUtil.toJsonStr(tags));
        picture.setSourceUrl(sourceUrl);
        Picture saved = claim != null ? privatePictureWriteManager.save(picture, oldPicture, user, editToken, editRevision, claim)
                : editToken == null && editRevision == null ? privatePictureWriteManager.save(picture, oldPicture, user)
                : privatePictureWriteManager.save(picture, oldPicture, user, editToken, editRevision);
        PictureVO vo = PictureVO.objToVo(saved);
        // Service 自身也不向调用方暴露 COS 地址；Controller 按实际 context path 补齐。
        protectPrivateUrls(saved, vo, null, user);
        return vo;
    }

    private PictureVO persistUploadedPicture(PictureUploadResult uploadResult, Picture oldPicture, Long pictureId,
                                             String name, String introduction, String category, List<String> tags,
                                             String sourceUrl, boolean completed, User loginUser) {
        return persistUploadedPicture(uploadResult, oldPicture, pictureId, name, introduction, category, tags, sourceUrl, completed, loginUser, null);
    }

    private PictureVO persistUploadedPicture(PictureUploadResult uploadResult, Picture oldPicture, Long pictureId,
                                             String name, String introduction, String category, List<String> tags,
                                             String sourceUrl, boolean completed, User loginUser, AiApplyClaim claim) {
        try {
            PictureVO pictureVO = transactionTemplate.execute(status -> {
                Picture current = oldPicture;
                if (pictureId != null) {
                    current = baseMapper.selectForUpdate(pictureId);
                    if (current == null || !Objects.equals(current.getUrl(), oldPicture.getUrl())
                            || !Objects.equals(current.getOriginalUrl(), oldPicture.getOriginalUrl()))
                        throw new BusinessException(ErrorCode.OPERATION_ERROR, "图片已变更，请刷新后重试");
                    pictureAccessManager.checkEdit(current, loginUser);
                }
                if (claim != null) aiTaskStore.commitApply(claim, current, loginUser.getId());
                pictureFileCleanupManager.adopt(Arrays.asList(uploadResult.getOriginalUrl(), uploadResult.getUrl(), uploadResult.getThumbnailUrl()));
                PictureVO saved = saveUploadedPicture(uploadResult, current, pictureId, name, introduction, category, tags, sourceUrl, completed, loginUser);
                if (current != null) cleanupManagedPictureFiles(current, "replace-picture", uploadResult.getOriginalUrl(), uploadResult.getUrl(), uploadResult.getThumbnailUrl());
                return saved;
            });
            if (pictureVO == null) {
                throw new BusinessException(ErrorCode.OPERATION_ERROR, "保存图片失败");
            }
            return pictureVO;
        } catch (RuntimeException e) {
            cleanupUploadedPictureFiles(uploadResult, "rollback-upload");
            throw e;
        }
    }

    private PictureVO saveUploadedPicture(PictureUploadResult uploadResult, Picture oldPicture, Long pictureId,
                                          String name, String introduction, String category, List<String> tags,
                                          String sourceUrl, boolean completed, User loginUser) {
        String normalizedSourceUrl = normalizeSourceUrl(sourceUrl);
        String uploadedUrl = uploadResult == null ? null : uploadResult.getUrl();
        String originalUrl = uploadResult == null
                ? null : StrUtil.blankToDefault(uploadResult.getOriginalUrl(), uploadedUrl);
        String thumbnailUrl = uploadResult == null ? null : uploadResult.getThumbnailUrl();
        if (StrUtil.isBlank(uploadedUrl) || uploadedUrl.length() > 512
                || StrUtil.isBlank(originalUrl) || originalUrl.length() > 512
                || StrUtil.length(thumbnailUrl) > 512) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "上传图片地址无效");
        }
        Picture picture = new Picture();
        if (pictureId != null) {
            picture.setId(pictureId);
            picture.setEditTime(new Date());
        }
        picture.setUrl(uploadedUrl);
        picture.setOriginalUrl(originalUrl);
        picture.setThumbnailUrl(StrUtil.isBlank(thumbnailUrl) ? null : thumbnailUrl);
        picture.setPicSize(uploadResult.getPicSize());
        picture.setPicWidth(uploadResult.getPicWidth());
        picture.setPicHeight(uploadResult.getPicHeight());
        picture.setPicScale(uploadResult.getPicScale());
        picture.setPicFormat(uploadResult.getPicFormat());
        picture.setPicColor(uploadResult.getPicColor());
        picture.setUserId(oldPicture == null ? loginUser.getId() : oldPicture.getUserId());
        boolean keepsCompletedStatus = oldPicture != null
                && Objects.equals(oldPicture.getUploadStatus(), PictureUploadStatusEnum.COMPLETED.getValue());
        picture.setUploadStatus(completed || keepsCompletedStatus ? PictureUploadStatusEnum.COMPLETED.getValue()
                : PictureUploadStatusEnum.DRAFT.getValue());
        if (normalizedSourceUrl != null) {
            picture.setSourceUrl(normalizedSourceUrl);
        }

        picture.setName(StrUtil.blankToDefault(name, uploadResult.getPicName()));
        picture.setIntroduction(introduction);
        picture.setCategory(category);
        if (CollUtil.isNotEmpty(tags)) {
            picture.setTags(JSONUtil.toJsonStr(tags));
        }

        if (userService.isAdmin(loginUser)) {
            fillReviewPassParams(picture, loginUser, "管理员上传自动通过");
        } else {
            fillReviewingParams(picture);
        }

        boolean result = this.saveOrUpdate(picture);
        if (!result) {
            log.error("Failed to save picture, pictureId: {}", pictureId);
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "Failed to save picture");
        }
        if (pictureId != null && normalizedSourceUrl == null) {
            clearSourceUrl(picture.getId());
        }
        if (pictureId != null && StrUtil.isBlank(thumbnailUrl)) {
            clearThumbnailUrl(picture.getId());
        }
        if (pictureId != null && uploadResult.getPicColor() == null) {
            if (!this.update(new UpdateWrapper<Picture>().eq("id", picture.getId()).set("picColor", null))) {
                throw new BusinessException(ErrorCode.OPERATION_ERROR, "清除旧主色调失败");
            }
        }
        if (PictureReviewStatusEnum.REVIEWING.getValue() == picture.getReviewStatus()) {
            clearReviewDetail(picture.getId());
        }

        pictureListRevision.advance();
        return PictureVO.objToVo(this.getById(picture.getId()));
    }

    private String normalizeSourceUrl(String sourceUrl) {
        if (StrUtil.isBlank(sourceUrl)) {
            return null;
        }
        String trimmedSourceUrl = sourceUrl.trim();
        if (trimmedSourceUrl.length() > 1024) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "Source URL is too long");
        }
        return trimmedSourceUrl;
    }

    private Picture getAuthorizedOldPicture(Long pictureId, User loginUser) {
        if (pictureId == null) {
            return null;
        }
        Picture oldPicture = this.getById(pictureId);
        if (oldPicture == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "图片不存在");
        }
        pictureAccessManager.checkEdit(oldPicture, loginUser);
        return oldPicture;
    }

    private void rejectPrivateUpload(Long spaceId, User loginUser) {
        if (spaceId != null) {
            pictureAccessManager.requireSpacePermission(spaceId, loginUser, "picture:upload");
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "私有空间暂不支持批量搜索导入");
        }
    }

    private String getUploadPathPrefix(Picture oldPicture, User loginUser) {
        Long ownerId = oldPicture == null ? loginUser.getId() : oldPicture.getUserId();
        return String.format("public/%s", ownerId);
    }

    @Override
    public void validPicture(Picture picture) {
        if (picture == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "Picture info cannot be empty");
        }

        Long id = picture.getId();
        String url = picture.getUrl();
        String originalUrl = picture.getOriginalUrl();
        String thumbnailUrl = picture.getThumbnailUrl();
        String introduction = picture.getIntroduction();

        // 更新时必须携带主键
        if (id == null || id <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "Invalid picture ID");
        }
        // 访问地址需要校验长度
        if (StrUtil.isNotBlank(url) && url.length() > 512) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "Picture URL is too long");
        }
        if (StrUtil.isNotBlank(originalUrl) && originalUrl.length() > 512) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "Original picture URL is too long");
        }
        if (StrUtil.isNotBlank(thumbnailUrl) && thumbnailUrl.length() > 512) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "Thumbnail URL is too long");
        }
        // 简介也需要长度限制
        if (StrUtil.isNotBlank(introduction) && introduction.length() > 500) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "Picture introduction is too long");
        }
    }

    @Override
    public QueryWrapper<Picture> getQueryWrapper(PictureQueryRequest pictureQueryRequest) {
        pictureQueryRequest = PublicPictureQuery.normalizeFilters(pictureQueryRequest);
        QueryWrapper<Picture> queryWrapper = new QueryWrapper<>();
        // No spaceId means PUBLIC, never all spaces. Authorization precedes private callers.
        if (pictureQueryRequest.getSpaceId() == null) {
            queryWrapper.isNull("spaceId");
        } else {
            queryWrapper.eq("spaceId", pictureQueryRequest.getSpaceId());
        }
        // 从请求体读取各类过滤字段
        Long id = pictureQueryRequest.getId();
        String name = pictureQueryRequest.getName();
        String introduction = pictureQueryRequest.getIntroduction();
        String category = pictureQueryRequest.getCategory();
        List<String> tags = pictureQueryRequest.getTags();
        Long picSize = pictureQueryRequest.getPicSize();
        Integer picWidth = pictureQueryRequest.getPicWidth();
        Integer picHeight = pictureQueryRequest.getPicHeight();
        Double picScale = pictureQueryRequest.getPicScale();
        String picFormat = pictureQueryRequest.getPicFormat();
        String searchText = pictureQueryRequest.getSearchText();
        Long userId = pictureQueryRequest.getUserId();
        Integer uploadStatus = pictureQueryRequest.getUploadStatus();
        Integer reviewStatus = pictureQueryRequest.getReviewStatus();
        String sortField = pictureQueryRequest.getSortField();
        String sortOrder = pictureQueryRequest.getSortOrder();

        // 组装查询条件
        // 关键字匹配（名称或简介）
        if (StrUtil.isNotBlank(searchText)) {
            queryWrapper.and(qw -> qw.like("name", searchText).or().like("introduction", searchText));
        }
        // 精确匹配
        queryWrapper.eq(id != null, "id", id);
        queryWrapper.eq(userId != null, "userId", userId);
        queryWrapper.eq(uploadStatus != null, "uploadStatus", uploadStatus);
        queryWrapper.eq(StrUtil.isNotBlank(category), "category", category);
        queryWrapper.eq(picWidth != null, "picWidth", picWidth);
        queryWrapper.eq(picHeight != null, "picHeight", picHeight);
        queryWrapper.eq(picSize != null, "picSize", picSize);
        queryWrapper.eq(picScale != null, "picScale", picScale);
        queryWrapper.eq(StrUtil.isNotBlank(picFormat), "picFormat", picFormat);
        queryWrapper.eq(reviewStatus != null, "reviewStatus", reviewStatus);
        queryWrapper.ge(pictureQueryRequest.getStartEditTime() != null, "editTime",
                pictureQueryRequest.getStartEditTime());
        queryWrapper.lt(pictureQueryRequest.getEndEditTime() != null, "editTime",
                pictureQueryRequest.getEndEditTime());
        // 模糊匹配
        queryWrapper.like(StrUtil.isNotBlank(name), "name", name);
        queryWrapper.like(StrUtil.isNotBlank(introduction), "introduction", introduction);
        // 标签字段包含查询
        if (CollUtil.isNotEmpty(tags)) {
            for (String tag : tags) {
                queryWrapper.like("tags", "\"" + tag + "\"");
            }
        }
        // 排序
        queryWrapper.orderBy(StrUtil.isNotBlank(sortField), "asc".equals(sortOrder), sortField);
        if (!"id".equals(sortField)) {
            queryWrapper.orderByDesc("id");
        }

        return queryWrapper;
    }

    @Override
    public PictureVO getPictureVO(Picture picture, HttpServletRequest request) {
        User caller = pictureAccessManager.isPubliclyVisible(picture) ? null : userService.getLoginUser(request);
        pictureAccessManager.checkRead(picture, caller);
        // 转换成视图对象
        PictureVO pictureVO = PictureVO.objToVo(picture);
        protectPrivateUrls(picture, pictureVO, request);
        // 补充上传用户信息
        Long userId = picture.getUserId();
        if (userId != null && userId > 0) {
            User user = userService.getById(userId);
            UserVO userVO = userService.getUserVO(user);
            pictureVO.setUser(userVO);
        }
        return pictureVO;
    }

    @Override
    public List<PictureVO> getPictureVOList(List<Picture> pictureList, HttpServletRequest request) {
        if (CollUtil.isEmpty(pictureList)) {
            return Collections.emptyList();
        }
        // Validate each distinct private space once, not once per picture/author lookup.
        Set<Long> privateSpaceIds = pictureList.stream().map(Picture::getSpaceId)
                .filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, List<String>> permissionMap = new HashMap<>();
        if (!privateSpaceIds.isEmpty()) {
            User caller = userService.getLoginUser(request);
            privateSpaceIds.forEach(spaceId -> {
                learn.gjx.tuku1.model.entity.Space space = pictureAccessManager.requireSpacePermission(spaceId, caller, "picture:view");
                permissionMap.put(spaceId, pictureAccessManager.permissions(space, caller, false));
            });
        }
        // 实体列表转为视图对象列表
        List<PictureVO> pictureVOList = pictureList.stream()
                .map(picture -> {
                    PictureVO vo = PictureVO.objToVo(picture);
                    if (picture.getSpaceId() != null) {
                        vo.setPermissionList(permissionMap.get(picture.getSpaceId()));
                        applyPrivateUrls(picture, vo, request);
                    }
                    return vo;
                })
                .collect(Collectors.toList());

        // 1) 批量查询相关用户
        Set<Long> userIdSet = pictureList.stream()
                .map(Picture::getUserId)
                .filter(userId -> userId != null && userId > 0)
                .collect(Collectors.toSet());

        if (CollUtil.isEmpty(userIdSet)) {
            return pictureVOList;
        }

        // 按用户主键分组
        Map<Long, List<User>> userIdUserListMap = userService.listByIds(userIdSet).stream()
                .collect(Collectors.groupingBy(User::getId));

        // 2) 将用户信息写入视图对象
        pictureVOList.forEach(pictureVO -> {
            Long userId = pictureVO.getUserId();
            User user = null;
            if (userIdUserListMap.containsKey(userId)) {
                user = userIdUserListMap.get(userId).get(0);
            }
            pictureVO.setUser(userService.getUserVO(user));
        });

        return pictureVOList;
    }

    private void protectPrivateUrls(Picture picture, PictureVO vo, HttpServletRequest request) {
        if (picture.getSpaceId() == null) return;
        protectPrivateUrls(picture, vo, request, userService.getLoginUser(request));
    }

    private void protectPrivateUrls(Picture picture, PictureVO vo, HttpServletRequest request, User caller) {
        if (picture.getSpaceId() == null) {
            return;
        }
        learn.gjx.tuku1.model.entity.Space permittedSpace = pictureAccessManager.requireSpacePermission(
                picture.getSpaceId(), caller, "picture:view");
        vo.setPermissionList(pictureAccessManager.permissions(permittedSpace, caller, false));
        applyPrivateUrls(picture, vo, request);
    }

    private void applyPrivateUrls(Picture picture, PictureVO vo, HttpServletRequest request) {
        String base = (request == null ? "" : request.getContextPath()) + "/picture/private/file?id=" + picture.getId();
        vo.setUrl(base + "&variant=display");
        vo.setThumbnailUrl(StrUtil.isBlank(picture.getThumbnailUrl()) ? null : base + "&variant=thumbnail");
    }

    @Override
    public Page<PictureVO> getPictureVOPage(Page<Picture> picturePage, HttpServletRequest request) {
        List<Picture> pictureList = picturePage.getRecords();
        Page<PictureVO> pictureVOPage = new Page<>(picturePage.getCurrent(), picturePage.getSize(), picturePage.getTotal());

        if (CollUtil.isEmpty(pictureList)) {
            return pictureVOPage;
        }

        // 批量转换
        List<PictureVO> pictureVOList = getPictureVOList(pictureList, request);
        pictureVOPage.setRecords(pictureVOList);

        return pictureVOPage;
    }

    @Override
    public PictureProcessResult processPicture(PictureProcessRequest pictureProcessRequest, HttpServletRequest request) {
        if (pictureProcessRequest == null || pictureProcessRequest.getPictureId() == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "图片 ID 不能为空");
        }
        Picture picture = this.getById(pictureProcessRequest.getPictureId());
        if (picture == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "图片不存在");
        }
        // 任意处理规则的额外输出尚未接入私有记账，仍只允许公共图库。
        pictureAccessManager.requirePublic(picture);
        List<PictureProcessRule> rules = pictureProcessRequest.getRules();
        if (CollUtil.isEmpty(rules)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "请至少提供一条处理规则");
        }
        User loginUser = userService.getLoginUser(request);
        boolean isAdmin = userService.isAdmin(loginUser);
        if (!isAdmin && !Objects.equals(picture.getUserId(), loginUser.getId())) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "无权处理该图片");
        }
        String objectKey = fileManager.extractKeyFromUrl(picture.getUrl());
        if (StrUtil.isBlank(objectKey)) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "图片路径异常");
        }
        return fileManager.processPicture(objectKey, rules, pictureProcessRequest.getOutputDir());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean reviewPicture(PictureReviewRequest pictureReviewRequest, User loginUser) {
        if (loginUser == null) {
            throw new BusinessException(ErrorCode.NOT_LOGIN_ERROR);
        }
        if (!userService.isAdmin(loginUser)) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "仅管理员可审核图片");
        }
        if (pictureReviewRequest == null || pictureReviewRequest.getId() == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "图片 ID 不能为空");
        }
        Long pictureId = pictureReviewRequest.getId();
        if (pictureId <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "图片 ID 不合法");
        }
        Integer reviewStatus = pictureReviewRequest.getReviewStatus();
        if (!PictureReviewStatusEnum.isReviewResult(reviewStatus)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "审核状态只能为通过或拒绝");
        }
        Picture oldPicture = this.getById(pictureId);
        if (oldPicture == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "图片不存在");
        }
        if (!Objects.equals(oldPicture.getUploadStatus(), PictureUploadStatusEnum.COMPLETED.getValue())) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "草稿图片不能审核");
        }
        pictureAccessManager.requirePublic(oldPicture);
        if (Objects.equals(oldPicture.getReviewStatus(), reviewStatus)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "请勿重复审核");
        }
        if (StrUtil.length(pictureReviewRequest.getReviewMessage()) > 512) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "审核原因过长");
        }
        PictureReviewStatusEnum statusEnum = PictureReviewStatusEnum.getEnumByValue(reviewStatus);
        Picture picture = new Picture();
        picture.setId(pictureId);
        fillReviewResultParams(picture, loginUser, reviewStatus,
                StrUtil.blankToDefault(pictureReviewRequest.getReviewMessage(), statusEnum.getName()));

        boolean result = this.updateById(picture);
        if (!result) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "审核图片失败");
        }
        pictureListRevision.advance();
        return true;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean editPicture(PictureEditRequest pictureEditRequest, User loginUser) {
        if (loginUser == null) {
            throw new BusinessException(ErrorCode.NOT_LOGIN_ERROR);
        }
        if (pictureEditRequest == null || pictureEditRequest.getId() == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "图片 ID 不能为空");
        }
        validatePictureMetadata(pictureEditRequest.getName(), pictureEditRequest.getIntroduction(),
                pictureEditRequest.getCategory(), pictureEditRequest.getTags());
        Picture picture = new Picture();
        org.springframework.beans.BeanUtils.copyProperties(pictureEditRequest, picture);
        if (pictureEditRequest.getTags() != null) {
            picture.setTags(JSONUtil.toJsonStr(pictureEditRequest.getTags()));
        }
        picture.setEditTime(new Date());
        picture.setUploadStatus(PictureUploadStatusEnum.COMPLETED.getValue());
        validPicture(picture);

        Long pictureId = pictureEditRequest.getId();
        Picture oldPicture = this.getById(pictureId);
        if (oldPicture == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "图片不存在");
        }
        boolean isAdmin = userService.isAdmin(loginUser);
        pictureAccessManager.checkEdit(oldPicture, loginUser);
        boolean privatePicture = oldPicture.getSpaceId() != null;
        if (privatePicture) {
            // 锁内与替换/删除/草稿过期任务串行；完成编辑不会再增加数量或字节。
            privatePictureWriteManager.lockForEdit(oldPicture, loginUser);
        } else if (isAdmin) {
            fillReviewPassParams(picture, loginUser, "管理员编辑自动通过");
        } else {
            fillReviewingParams(picture);
        }
        boolean result = this.updateById(picture);
        if (!result) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "编辑失败");
        }
        if (!privatePicture && !isAdmin) {
            clearReviewDetail(pictureId);
        }
        if (!privatePicture) {
            pictureListRevision.advance();
        }
        return true;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean updatePicture(PictureUpdateRequest pictureUpdateRequest, User loginUser) {
        if (loginUser == null) {
            throw new BusinessException(ErrorCode.NOT_LOGIN_ERROR);
        }
        if (!userService.isAdmin(loginUser)) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "仅管理员可更新图片");
        }
        if (pictureUpdateRequest == null || pictureUpdateRequest.getId() == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "图片 ID 不能为空");
        }
        validatePictureMetadata(pictureUpdateRequest.getName(), pictureUpdateRequest.getIntroduction(),
                pictureUpdateRequest.getCategory(), pictureUpdateRequest.getTags());
        Picture picture = new Picture();
        org.springframework.beans.BeanUtils.copyProperties(pictureUpdateRequest, picture);
        picture.setUploadStatus(PictureUploadStatusEnum.COMPLETED.getValue());
        if (pictureUpdateRequest.getTags() != null) {
            picture.setTags(JSONUtil.toJsonStr(pictureUpdateRequest.getTags()));
        }
        validPicture(picture);

        Long pictureId = pictureUpdateRequest.getId();
        Picture oldPicture = this.getById(pictureId);
        if (oldPicture == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "图片不存在");
        }
        pictureAccessManager.requirePublic(oldPicture);
        fillReviewPassParams(picture, loginUser, "管理员更新自动通过");
        boolean result = this.updateById(picture);
        if (!result) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "更新失败");
        }
        pictureListRevision.advance();
        return true;
    }

    private void fillReviewingParams(Picture picture) {
        picture.setReviewStatus(PictureReviewStatusEnum.REVIEWING.getValue());
        picture.setReviewMessage(null);
        picture.setReviewerId(null);
        picture.setReviewTime(null);
    }

    private void fillReviewPassParams(Picture picture, User reviewer, String reviewMessage) {
        fillReviewResultParams(picture, reviewer, PictureReviewStatusEnum.PASS.getValue(), reviewMessage);
    }

    private void fillReviewResultParams(Picture picture, User reviewer, Integer reviewStatus, String reviewMessage) {
        picture.setReviewStatus(reviewStatus);
        picture.setReviewMessage(reviewMessage);
        picture.setReviewerId(reviewer.getId());
        picture.setReviewTime(new Date());
    }

    private void clearReviewDetail(Long pictureId) {
        if (pictureId == null) {
            return;
        }
        this.lambdaUpdate()
                .eq(Picture::getId, pictureId)
                .set(Picture::getReviewMessage, null)
                .set(Picture::getReviewerId, null)
                .set(Picture::getReviewTime, null)
                .update();
    }

    private void clearSourceUrl(Long pictureId) {
        if (pictureId == null) {
            return;
        }
        this.lambdaUpdate()
                .eq(Picture::getId, pictureId)
                .set(Picture::getSourceUrl, null)
                .update();
    }

    private void clearThumbnailUrl(Long pictureId) {
        if (pictureId == null) {
            return;
        }
        this.lambdaUpdate()
                .eq(Picture::getId, pictureId)
                .set(Picture::getThumbnailUrl, null)
                .update();
    }

    /**
     * 清理一条图片记录关联的全部受管 COS 文件。三个字段在兼容阶段可能指向同一对象，
     * 所以必须先去重；替换上传时还要保留新对象，避免误删仍被新记录引用的文件。
     */
    private void cleanupManagedPictureFiles(Picture picture, String scene, String... retainedUrls) {
        if (picture == null) {
            return;
        }
        Set<String> retained = Arrays.stream(retainedUrls == null ? new String[0] : retainedUrls)
                .filter(StrUtil::isNotBlank)
                .collect(Collectors.toSet());
        pictureFileCleanupManager.enqueueCommitted(Stream.of(picture.getOriginalUrl(), picture.getUrl(), picture.getThumbnailUrl())
                .filter(StrUtil::isNotBlank)
                .distinct()
                .filter(url -> !retained.contains(url))
                .collect(Collectors.toList()));
    }

    private void cleanupUploadedPictureFiles(PictureUploadResult uploadResult, String scene) {
        if (uploadResult == null) {
            return;
        }
        Stream.of(uploadResult.getOriginalUrl(), uploadResult.getUrl(), uploadResult.getThumbnailUrl())
                .filter(StrUtil::isNotBlank)
                .distinct()
                .forEach(url -> pictureFileCleanupManager.deleteQuietly(url, scene));
    }

    private void validatePictureMetadata(String name, String introduction, String category, List<String> tags) {
        if (StrUtil.length(name) > 128) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "图片名称过长");
        }
        if (StrUtil.length(introduction) > 500) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "图片简介过长");
        }
        if (StrUtil.length(category) > 64) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "图片分类过长");
        }
        if (CollUtil.isNotEmpty(tags) && JSONUtil.toJsonStr(tags).length() > 512) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "图片标签过多或过长");
        }
    }
}


