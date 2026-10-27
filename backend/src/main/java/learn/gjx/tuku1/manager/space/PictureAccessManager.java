package learn.gjx.tuku1.manager.space;

import learn.gjx.tuku1.exception.BusinessException;
import learn.gjx.tuku1.exception.ErrorCode;
import learn.gjx.tuku1.mapper.SpaceMapper;
import learn.gjx.tuku1.mapper.SpaceUserMapper;
import learn.gjx.tuku1.model.entity.SpaceUser;
import learn.gjx.tuku1.model.enums.SpaceRoleEnum;
import learn.gjx.tuku1.model.enums.SpaceTypeEnum;
import learn.gjx.tuku1.model.entity.Picture;
import learn.gjx.tuku1.model.entity.Space;
import learn.gjx.tuku1.model.entity.User;
import learn.gjx.tuku1.model.enums.UserRoleEnum;
import learn.gjx.tuku1.model.enums.PictureUploadStatusEnum;
import learn.gjx.tuku1.model.enums.PictureReviewStatusEnum;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.Objects;
import java.util.*;
import static learn.gjx.tuku1.manager.space.SpacePermission.*;

/** Protected content uses private ownership or team membership, never a client-supplied identity/role. */
@Component
public class PictureAccessManager {
    @Resource
    private SpaceMapper spaceMapper;
    @Resource private SpaceUserMapper members;

    public static void requireActiveUser(User user) {
        if (user == null || user.getId() == null || user.getId() <= 0)
            throw new BusinessException(ErrorCode.NOT_LOGIN_ERROR);
        if (Objects.equals(user.getIsDelete(), 1)
                || (!Objects.equals(user.getUserRole(), 0) && !Objects.equals(user.getUserRole(), 1)))
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "用户不可用");
    }

    public Space requireSpacePermission(Long spaceId, User user, String permission) {
        requireActiveUser(user);
        if (spaceId == null || spaceId <= 0)
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "空间 ID 不合法");
        Space space = spaceMapper.selectById(spaceId);
        requireSpacePermission(space, user, permission, false);
        return space;
    }

    public void requireSpacePermission(Space space, User user, String permission, boolean currentRead) {
        requireActiveUser(user);
        if (space == null || Objects.equals(space.getIsDelete(), 1))
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "空间不存在");
        if (!permissions(space, user, currentRead).contains(permission))
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "没有此空间的操作权限");
    }

    public List<String> permissions(Space space, User user, boolean currentRead) {
        if (space == null || user == null || user.getId() == null || Objects.equals(space.getIsDelete(), 1)
                || Objects.equals(user.getIsDelete(), 1)
                || (!Objects.equals(user.getUserRole(), 0) && !Objects.equals(user.getUserRole(), 1)))
            return Collections.emptyList();
        // Null is tolerated only for pre-migration Java fixtures; persisted rows are NOT NULL DEFAULT 0.
        if (space.getSpaceType() == null || Objects.equals(space.getSpaceType(), SpaceTypeEnum.PRIVATE.getValue())) {
            return Objects.equals(space.getUserId(), user.getId())
                    ? Arrays.asList(VIEW, UPLOAD, EDIT, DELETE, ANALYZE) : Collections.emptyList();
        }
        if (!Objects.equals(space.getSpaceType(), SpaceTypeEnum.TEAM.getValue())) return Collections.emptyList();
        SpaceUser member = currentRead ? members.lockMember(space.getId(), user.getId())
                : members.findMember(space.getId(), user.getId());
        SpaceRoleEnum role = member == null ? null : SpaceRoleEnum.from(member.getSpaceRole());
        return role == null ? Collections.emptyList() : role.getPermissions();
    }

    public void requireSpaceOwner(Long spaceId, User user) {
        if (user == null || user.getId() == null || user.getId() <= 0) {
            throw new BusinessException(ErrorCode.NOT_LOGIN_ERROR);
        }
        if (Objects.equals(user.getIsDelete(), 1)
                || (!Objects.equals(user.getUserRole(), UserRoleEnum.USER.getValue())
                && !Objects.equals(user.getUserRole(), UserRoleEnum.ADMIN.getValue()))) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "用户不可访问私有图片");
        }
        if (spaceId == null || spaceId <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "空间 ID 不合法");
        }
        Space space = spaceMapper.selectById(spaceId);
        if (space == null || Objects.equals(space.getIsDelete(), 1)) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "空间不存在");
        }
        if (!Objects.equals(space.getUserId(), user.getId())) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "仅空间所有者可访问私有图片");
        }
    }

    public boolean isPubliclyVisible(Picture picture) {
        return picture.getSpaceId() == null
                && Objects.equals(picture.getUploadStatus(), PictureUploadStatusEnum.COMPLETED.getValue())
                && Objects.equals(picture.getReviewStatus(), PictureReviewStatusEnum.PASS.getValue())
                && !Objects.equals(picture.getIsDelete(), 1);
    }

    public void checkRead(Picture picture, User user) {
        if (picture.getSpaceId() != null) {
            requireSpacePermission(picture.getSpaceId(), user, VIEW);
        } else if (!isPubliclyVisible(picture)) {
            checkEdit(picture, user);
        }
    }

    public void checkEdit(Picture picture, User user) {
        if (picture.getSpaceId() != null) {
            requireSpacePermission(picture.getSpaceId(), user, EDIT);
        } else {
            if (user == null || user.getId() == null) {
                throw new BusinessException(ErrorCode.NOT_LOGIN_ERROR);
            }
            if (!Objects.equals(picture.getUserId(), user.getId())
                    && !Objects.equals(user.getUserRole(), UserRoleEnum.ADMIN.getValue())) {
                throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "无权操作该图片");
            }
        }
    }

    public void requirePublic(Picture picture) {
        if (picture.getSpaceId() != null) {
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "公共图库操作不支持私有图片");
        }
    }
}
