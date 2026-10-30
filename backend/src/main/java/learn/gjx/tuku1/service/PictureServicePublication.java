package learn.gjx.tuku1.service;

import learn.gjx.tuku1.exception.*;
import learn.gjx.tuku1.manager.*;
import learn.gjx.tuku1.manager.cache.PictureListRevision;
import learn.gjx.tuku1.manager.space.PictureAccessManager;
import learn.gjx.tuku1.manager.space.PrivatePictureWriteManager;
import learn.gjx.tuku1.mapper.*;
import learn.gjx.tuku1.model.entity.*;
import learn.gjx.tuku1.model.vo.PrivatePictureLinkVO;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import javax.annotation.Resource;
import java.util.*;
import static learn.gjx.tuku1.manager.space.SpacePermission.MEMBERS;

/** 投稿与临时直链仅由个人空间主人或团队管理员发起。 */
@Service
@Slf4j
public class PictureServicePublication {
    @Resource private PictureMapper pictures;
    @Resource private SpaceMapper spaces;
    @Resource private PictureAccessManager access;
    @Resource private FileManager files;
    @Resource private PrivatePictureFileManager privateFiles;
    @Resource private PictureFileCleanupManager cleanup;
    @Resource private PictureListRevision revision;
    @Resource private JdbcTemplate jdbc;
    @Resource private TransactionTemplate transactionTemplate;

    public long submit(Long id, User actor) {
        Picture expected = readableSource(id, actor);
        rejectDuplicate(id);
        // 云端 IO 不持有业务锁；所有目标对象在上传前已独立登记清理凭据。
        Picture copied = files.copyPrivateForSubmission(expected, actor.getId());
        try {
            return Objects.requireNonNull(transactionTemplate.execute(status -> {
                Picture current = lockedSource(expected, actor);
                rejectDuplicate(id);
                Picture result = new Picture();
                BeanUtils.copyProperties(current, result);
                result.setId(null); result.setSpaceId(null); result.setUserId(actor.getId());
                result.setUrl(copied.getUrl()); result.setOriginalUrl(copied.getOriginalUrl());
                result.setThumbnailUrl(copied.getThumbnailUrl()); result.setSourceUrl(null);
                // 管理员投稿也必须审核，不能沿用私有图片的 PASS 状态。
                result.setUploadStatus(1); result.setReviewStatus(0); result.setReviewMessage(null);
                result.setReviewerId(null); result.setReviewTime(null); result.setIsDelete(0);
                Date now = new Date(); result.setCreateTime(now); result.setEditTime(now); result.setUpdateTime(now);
                cleanup.adopt(PrivatePictureWriteManager.files(result));
                if (pictures.insert(result) != 1) throw failure(ErrorCode.OPERATION_ERROR, "保存投稿失败");
                jdbc.update("INSERT INTO picture_submission(sourcePictureId,publicPictureId) VALUES(?,?)", id, result.getId());
                revision.advance();
                return result.getId();
            }));
        } catch (RuntimeException error) {
            for (String url : PrivatePictureWriteManager.files(copied)) cleanup.deleteQuietly(url, "submission-failed");
            // 不传播可能含存储地址的底层异常；业务拒绝仍保留明确提示。
            if (error instanceof BusinessException) throw error;
            throw failure(ErrorCode.OPERATION_ERROR, "投稿失败，请稍后重试");
        }
    }

    public PrivatePictureLinkVO temporaryLink(Long id, Integer hours, User actor) {
        if (!Arrays.asList(1, 24, 168).contains(hours)) throw failure(ErrorCode.PARAMS_ERROR, "有效期仅支持1小时、24小时或7天");
        Picture expected = readableSource(id, actor);
        return transactionTemplate.execute(status -> privateFiles.temporaryDisplayLink(lockedSource(expected, actor), hours));
    }

    private Picture readableSource(Long id, User actor) {
        PictureAccessManager.requireActiveUser(actor);
        if (id == null || id <= 0) throw failure(ErrorCode.PARAMS_ERROR, "图片ID不合法");
        Picture picture = pictures.selectById(id);
        validSource(picture);
        requirePublisher(spaces.selectById(picture.getSpaceId()), actor, false);
        return picture;
    }

    private Picture lockedSource(Picture expected, User actor) {
        // 与私有写入、成员撤权保持空间 -> 成员 -> 图片的相同锁序。
        requirePublisher(spaces.selectForUpdate(expected.getSpaceId()), actor, true);
        Picture current = pictures.selectForUpdate(expected.getId());
        validSource(current);
        if (!Objects.equals(current.getSpaceId(), expected.getSpaceId())
                || !Objects.equals(current.getOriginalUrl(), expected.getOriginalUrl())
                || !Objects.equals(current.getUrl(), expected.getUrl())
                || !Objects.equals(current.getThumbnailUrl(), expected.getThumbnailUrl()))
            throw failure(ErrorCode.OPERATION_ERROR, "源图已变化，请刷新后重试");
        return current;
    }

    private void validSource(Picture picture) {
        if (picture == null || Objects.equals(picture.getIsDelete(), 1) || picture.getSpaceId() == null
                || !Objects.equals(picture.getUploadStatus(), 1))
            throw failure(ErrorCode.NOT_FOUND_ERROR, "仅支持已保存的空间图片");
    }

    private void requirePublisher(Space space, User actor, boolean lock) {
        PictureAccessManager.requireActiveUser(actor);
        if (space == null || Objects.equals(space.getIsDelete(), 1)) throw failure(ErrorCode.NOT_FOUND_ERROR, "空间不存在");
        if (Objects.equals(space.getSpaceType(), 0) && Objects.equals(space.getUserId(), actor.getId())) return;
        if (Objects.equals(space.getSpaceType(), 1) && access.permissions(space, actor, lock).contains(MEMBERS)) return;
        throw failure(ErrorCode.NO_AUTH_ERROR, "仅空间主人或团队管理员可执行此操作");
    }

    private void rejectDuplicate(Long id) {
        Long count = jdbc.queryForObject("SELECT COUNT(*) FROM picture_submission s JOIN picture p ON p.id=s.publicPictureId WHERE s.sourcePictureId=? AND p.isDelete=0 AND p.reviewStatus IN (0,1)", Long.class, id);
        if (count == null || count > 0) throw failure(ErrorCode.OPERATION_ERROR, "这张图片已有待审核或已通过的投稿，请查看我的投稿");
    }

    private BusinessException failure(ErrorCode code, String message) {
        log.warn("图片投稿或临时直链请求被拒绝：{}", message);
        return new BusinessException(code, message);
    }
}
