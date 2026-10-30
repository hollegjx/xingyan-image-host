package learn.gjx.tuku1.service;

import learn.gjx.tuku1.exception.BusinessException;
import learn.gjx.tuku1.exception.ErrorCode;
import learn.gjx.tuku1.model.dto.user.UserUpdateRequest;
import learn.gjx.tuku1.model.vo.UserPasswordResetVO;
import learn.gjx.tuku1.security.UserPasswords;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.security.SecureRandom;
import java.util.*;

/** 管理员集合使用数据库行锁串行化，避免多实例同时删除或降级后失去管理员。 */
@Service
@Slf4j
public class UserAdministrationService {
    private final JdbcTemplate jdbc;
    private final SecureRandom random = new SecureRandom();
    public UserAdministrationService(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    private List<Long> lockAdministrators(Long actor) {
        List<Long> admins = jdbc.queryForList("SELECT id FROM user WHERE userRole=1 AND isDelete=0 ORDER BY id FOR UPDATE", Long.class);
        if (!admins.contains(actor)) throw failure(ErrorCode.NO_AUTH_ERROR, "需要管理员权限");
        return admins;
    }
    private int lockTarget(Long id) {
        if (id == null || id <= 0) throw failure(ErrorCode.PARAMS_ERROR, "用户ID不合法");
        List<Integer> roles = jdbc.queryForList("SELECT userRole FROM user WHERE id=? AND isDelete=0 FOR UPDATE", Integer.class, id);
        if (roles.isEmpty()) throw failure(ErrorCode.NOT_FOUND_ERROR, "用户不存在");
        return roles.get(0);
    }
    private void protectLast(List<Long> admins, Long target, int role) {
        if (role == 1 && admins.size() <= 1 && admins.contains(target))
            throw failure(ErrorCode.OPERATION_ERROR, "不能移除最后一个管理员");
    }
    @Transactional
    public boolean delete(Long target, Long actor) {
        List<Long> admins = lockAdministrators(actor);
        int role = lockTarget(target);
        protectLast(admins, target, role);
        if (Objects.equals(target, actor)) throw failure(ErrorCode.OPERATION_ERROR, "不能删除自己的账号");
        return jdbc.update("UPDATE user SET isDelete=1,updateTime=CURRENT_TIMESTAMP WHERE id=? AND isDelete=0", target) == 1;
    }
    @Transactional
    public boolean update(UserUpdateRequest request, Long actor) {
        if (request == null) throw failure(ErrorCode.PARAMS_ERROR, "请求不能为空");
        List<Long> admins = lockAdministrators(actor);
        int oldRole = lockTarget(request.getId());
        Integer role = request.getUserRole();
        if (role != null) {
            if (role != 0 && role != 1) throw failure(ErrorCode.PARAMS_ERROR, "角色不合法");
            if (role != oldRole) {
                protectLast(admins, request.getId(), oldRole);
                if (Objects.equals(actor, request.getId())) throw failure(ErrorCode.OPERATION_ERROR, "不能修改自己的角色");
            }
        }
        List<String> fields = new ArrayList<>(); List<Object> values = new ArrayList<>();
        if (role != null) { fields.add("userRole=?"); values.add(role); }
        if (request.getUserName() != null) { fields.add("userName=?"); values.add(request.getUserName()); }
        if (request.getUserProfile() != null) { fields.add("userProfile=?"); values.add(request.getUserProfile()); }
        if (request.getUserAvatar() != null) { fields.add("userAvatar=?"); values.add(request.getUserAvatar()); }
        if (fields.isEmpty()) throw failure(ErrorCode.PARAMS_ERROR, "没有要修改的字段");
        values.add(request.getId());
        return jdbc.update("UPDATE user SET " + String.join(",", fields) + ",editTime=CURRENT_TIMESTAMP WHERE id=? AND isDelete=0", values.toArray()) == 1;
    }
    @Transactional
    public UserPasswordResetVO resetPassword(Long target, Long actor) {
        lockAdministrators(actor); lockTarget(target);
        byte[] bytes = new byte[24]; random.nextBytes(bytes);
        String temporary = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        String hash = UserPasswords.hash(temporary);
        if (jdbc.update("UPDATE user SET userPassword=?,editTime=CURRENT_TIMESTAMP WHERE id=? AND isDelete=0", hash, target) != 1)
            throw failure(ErrorCode.OPERATION_ERROR, "重置密码失败");
        return new UserPasswordResetVO(temporary);
    }
    private BusinessException failure(ErrorCode code, String message) {
        log.warn("管理员用户操作被拒绝：{}", message);
        return new BusinessException(code, message);
    }
}
