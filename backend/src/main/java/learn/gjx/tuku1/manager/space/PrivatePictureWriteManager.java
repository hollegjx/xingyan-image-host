package learn.gjx.tuku1.manager.space;

import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import learn.gjx.tuku1.exception.BusinessException;
import learn.gjx.tuku1.exception.ErrorCode;
import learn.gjx.tuku1.manager.PrivatePictureCleanupManager;
import learn.gjx.tuku1.manager.PrivatePictureFileManager;
import learn.gjx.tuku1.mapper.PictureMapper;
import learn.gjx.tuku1.mapper.SpaceMapper;
import learn.gjx.tuku1.model.entity.*;
import learn.gjx.tuku1.model.enums.PictureUploadStatusEnum;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import javax.annotation.Resource;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import static learn.gjx.tuku1.manager.space.SpacePermission.*;

/** 私有写入统一使用空间行锁 -> 图片行锁 -> 清理凭据，数量/字节与图片同事务。 */
@Component
public class PrivatePictureWriteManager {
    @Resource private SpaceMapper spaceMapper;
    @Resource private PictureMapper pictureMapper;
    @Resource private PictureAccessManager access;
    @Resource private PrivatePictureFileManager storage;
    @Resource private PrivatePictureCleanupManager cleanup;
    @Resource private TransactionTemplate transactionTemplate;
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private learn.gjx.tuku1.infrastructure.collaboration.CollaborationWriteGuard collaboration;

    public void prepare(Long spaceId, User user) {
        standalone();
        access.requireSpacePermission(spaceId, user, UPLOAD);
        storage.bucket(spaceId);
    }

    public Picture save(Picture uploaded, Picture expected, User user) {
        return save(uploaded, expected, user, null, null);
    }

    public Picture save(Picture uploaded, Picture expected, User user, String editToken, Long editRevision) {
        standalone();
        try {
            access.requireSpacePermission(uploaded.getSpaceId(), user, expected == null ? UPLOAD : EDIT);
            storage.validatedKey(uploaded.getSpaceId(), uploaded.getUrl());
            storage.validatedKey(uploaded.getSpaceId(), uploaded.getOriginalUrl());
            if (uploaded.getThumbnailUrl() != null) { storage.validatedKey(uploaded.getSpaceId(), uploaded.getThumbnailUrl()); }
            return transactionTemplate.execute(status -> {
                Space space = lockSpace(uploaded.getSpaceId(), user, expected == null ? UPLOAD : EDIT);
                Picture current = expected == null ? null : lockPicture(expected.getId(), space.getId());
                if (current != null && (!Objects.equals(current.getOriginalUrl(), expected.getOriginalUrl())
                        || !Objects.equals(current.getUrl(), expected.getUrl()))) {
                    throw new BusinessException(ErrorCode.OPERATION_ERROR, "图片已被其他请求替换，请刷新后重试");
                }
                if (current != null && collaboration != null)
                    collaboration.protect(current, uploaded, user.getId(), editToken, editRevision);
                long oldSize = current == null ? 0 : size(current);
                adjust(space, size(uploaded) - oldSize, current == null ? 1 : 0);
                cleanup.adopt(space.getId(), files(uploaded));
                uploaded.setUserId(current == null ? user.getId() : current.getUserId());
                uploaded.setUploadStatus(current == null ? PictureUploadStatusEnum.DRAFT.getValue() : current.getUploadStatus());
                // 私有图片不进入公共审核队列；PASS 不会使 spaceId 非空的内容成为公开图片。
                uploaded.setReviewStatus(1);
                uploaded.setReviewMessage("私有空间图片，无需公共审核");
                uploaded.setEditTime(new Date());
                int changed = current == null ? pictureMapper.insert(uploaded) : pictureMapper.updateById(uploaded);
                if (changed != 1) { throw failure("保存私有图片失败"); }
                if (current != null) {
                    // MyBatis 的默认非空更新策略不能清掉旧缩略图/来源，必须显式 SET NULL。
                    UpdateWrapper<Picture> clear = new UpdateWrapper<Picture>().eq("id", uploaded.getId())
                            .set("thumbnailUrl", uploaded.getThumbnailUrl()).set("sourceUrl", uploaded.getSourceUrl())
                            .set("picColor", uploaded.getPicColor())
                            .set("reviewerId", null).set("reviewTime", null);
                    if (pictureMapper.update(null, clear) != 1) { throw failure("更新图片信息失败"); }
                    Set<String> retained = new HashSet<>(files(uploaded));
                    List<String> obsolete = files(current).stream().filter(url -> !retained.contains(url)).collect(Collectors.toList());
                    cleanup.enqueueCommitted(space.getId(), obsolete);
                }
                return pictureMapper.selectById(uploaded.getId());
            });
        } catch (RuntimeException error) {
            cleanup.abandon(uploaded.getSpaceId(), files(uploaded));
            throw error;
        }
    }

    public boolean delete(Picture expected, User user) {
        standalone();
        access.requireSpacePermission(expected.getSpaceId(), user, DELETE);
        return Boolean.TRUE.equals(transactionTemplate.execute(status -> {
            Space space = lockSpace(expected.getSpaceId(), user, DELETE);
            Picture current = pictureMapper.selectForUpdate(expected.getId());
            if (current == null) { return false; }
            sameSpace(current, space.getId());
            if (collaboration != null) collaboration.protect(current, current, user.getId(), null, null);
            remove(space, current);
            return true;
        }));
    }

    /** 后台任务不伪造用户身份；锁内重新检查是否仍为过期草稿。 */
    public boolean deleteExpiredDraft(Picture candidate, Date expireBefore) {
        standalone();
        return Boolean.TRUE.equals(transactionTemplate.execute(status -> {
            Space space = lockSpace(candidate.getSpaceId(), null, DELETE);
            Picture current = pictureMapper.selectForUpdate(candidate.getId());
            if (current == null) { return false; }
            sameSpace(current, space.getId());
            if (!Objects.equals(current.getUploadStatus(), PictureUploadStatusEnum.DRAFT.getValue())
                    || !current.getCreateTime().before(expireBefore)) { return false; }
            if (collaboration != null) collaboration.protect(current, current, null, null, null);
            remove(space, current);
            return true;
        }));
    }

    /** edit 的 @Transactional 已生效，用相同行锁协议与草稿清理、替换互斥。 */
    public Picture lockForEdit(Picture expected, User user) {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) { throw failure("图片编辑需要事务"); }
        access.requireSpacePermission(expected.getSpaceId(), user, EDIT);
        Space space = lockSpace(expected.getSpaceId(), user, EDIT);
        return lockPicture(expected.getId(), space.getId());
    }

    private void remove(Space space, Picture current) {
        adjust(space, -size(current), -1);
        if (pictureMapper.deleteById(current.getId()) != 1) { throw failure("删除私有图片失败"); }
        cleanup.enqueueCommitted(space.getId(), files(current));
    }

    private Space lockSpace(Long id, User user, String permission) {
        Space space = spaceMapper.selectForUpdate(id);
        if (space == null) { throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "空间不存在"); }
        if (user != null) {
            // Membership changes take the same space lock. Use a current membership read here.
            access.requireSpacePermission(space, user, permission, true);
        }
        return space;
    }

    private Picture lockPicture(Long id, Long spaceId) {
        Picture picture = pictureMapper.selectForUpdate(id);
        if (picture == null) { throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "图片已删除，请刷新后重试"); }
        sameSpace(picture, spaceId);
        return picture;
    }

    private void sameSpace(Picture picture, Long spaceId) {
        if (!Objects.equals(picture.getSpaceId(), spaceId)) { throw failure("不支持更换图片所属空间"); }
    }

    private void adjust(Space space, long sizeDelta, long countDelta) {
        if (space.getTotalSize() == null || space.getTotalCount() == null || space.getMaxSize() == null
                || space.getMaxCount() == null || space.getTotalSize() < 0 || space.getTotalCount() < 0) {
            throw failure("空间用量数据异常，请先核对");
        }
        long totalSize;
        long totalCount;
        try {
            totalSize = Math.addExact(space.getTotalSize(), sizeDelta);
            totalCount = Math.addExact(space.getTotalCount(), countDelta);
        } catch (ArithmeticException error) { throw failure("空间用量溢出"); }
        if (totalSize < 0 || totalCount < 0) { throw failure("空间用量不一致，请先核对"); }
        if (totalSize > space.getMaxSize() || totalCount > space.getMaxCount()) { throw failure("空间容量或图片数量已达上限"); }
        Space update = new Space(); update.setId(space.getId()); update.setTotalSize(totalSize); update.setTotalCount(totalCount);
        if (spaceMapper.updateById(update) != 1) { throw failure("更新空间用量失败"); }
    }

    private long size(Picture picture) {
        if (picture.getPicSize() == null || picture.getPicSize() <= 0) { throw failure("图片原始大小无效"); }
        return picture.getPicSize();
    }

    public static List<String> files(Picture picture) {
        return Stream.of(picture.getOriginalUrl(), picture.getUrl(), picture.getThumbnailUrl())
                .filter(Objects::nonNull).filter(s -> !s.trim().isEmpty()).distinct().collect(Collectors.toList());
    }

    private void standalone() {
        if (TransactionSynchronizationManager.isActualTransactionActive()) { throw failure("私有图片写入不支持外层事务"); }
    }

    private BusinessException failure(String message) { return new BusinessException(ErrorCode.OPERATION_ERROR, message); }
}
