package learn.gjx.tuku1.infrastructure.collaboration;

import learn.gjx.tuku1.application.collaboration.CollaborationAccess;
import learn.gjx.tuku1.constant.UserConstant;
import learn.gjx.tuku1.exception.*;
import learn.gjx.tuku1.manager.space.PictureAccessManager;
import learn.gjx.tuku1.mapper.*;
import learn.gjx.tuku1.model.entity.*;
import learn.gjx.tuku1.model.vo.LoginUserVO;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.session.Session;
import org.springframework.session.SessionRepository;
import org.springframework.stereotype.Component;
import javax.annotation.Resource;
import java.util.Objects;
import static learn.gjx.tuku1.manager.space.SpacePermission.EDIT;

@Component
public class SessionCollaborationAccess implements CollaborationAccess {
    @Resource private ObjectProvider<SessionRepository> repositories;
    @Resource private PictureMapper pictures;
    @Resource private SpaceMapper spaces;
    @Resource(name = "userMapper") private UserMapper users;
    @Resource private PictureAccessManager access;

    @Override public Actor authorize(String sessionId, Long userId, Long pictureId) {
        SessionRepository repository = repositories.getIfAvailable();
        Session session = repository == null ? null : repository.findById(sessionId);
        Object login = session == null ? null : session.getAttribute(UserConstant.USER_LOGIN_STATE);
        if (!(login instanceof LoginUserVO) || !Objects.equals(((LoginUserVO) login).getId(), userId))
            throw new BusinessException(ErrorCode.NOT_LOGIN_ERROR, "登录已失效");
        User user = users.selectById(userId);
        PictureAccessManager.requireActiveUser(user);
        Picture picture = pictures.selectById(pictureId);
        if (picture == null || picture.getSpaceId() == null)
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "仅团队图片支持协同编辑");
        Space space = spaces.selectById(picture.getSpaceId());
        if (space == null || !Objects.equals(space.getSpaceType(), 1))
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "仅团队图片支持协同编辑");
        access.requireSpacePermission(space, user, EDIT, false);
        return new Actor(user.getId(), user.getUserName(), picture);
    }
}
