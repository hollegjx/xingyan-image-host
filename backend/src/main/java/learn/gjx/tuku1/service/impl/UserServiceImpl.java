package learn.gjx.tuku1.service.impl;

import cn.hutool.core.util.RandomUtil;
import cn.hutool.core.util.StrUtil;
import learn.gjx.tuku1.security.UserPasswords;
import learn.gjx.tuku1.security.UserSessionSecurity;
import learn.gjx.tuku1.model.dto.user.UserProfileUpdateRequest;
import learn.gjx.tuku1.model.dto.user.UserPasswordUpdateRequest;
import java.util.Date;
import learn.gjx.tuku1.security.LoginAttemptLimiter;
import javax.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import learn.gjx.tuku1.constant.UserConstant;
import learn.gjx.tuku1.exception.BusinessException;
import learn.gjx.tuku1.exception.ErrorCode;
import learn.gjx.tuku1.mapper.UserMapper;
import learn.gjx.tuku1.model.dto.user.UserAddRequest;
import learn.gjx.tuku1.model.dto.user.UserQueryRequest;
import learn.gjx.tuku1.model.dto.user.UserUpdateRequest;
import learn.gjx.tuku1.model.entity.User;
import learn.gjx.tuku1.model.enums.UserRoleEnum;
import learn.gjx.tuku1.model.vo.LoginUserVO;
import learn.gjx.tuku1.model.vo.UserVO;
import learn.gjx.tuku1.model.vo.AdminUserVO;
import org.springframework.beans.BeanUtils;
import learn.gjx.tuku1.service.UserService;
import org.springframework.stereotype.Service;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 用户服务实现类。
 */
@Service
@Slf4j
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements UserService {



    @Resource
    private LoginAttemptLimiter loginAttemptLimiter;

    @Resource
    private learn.gjx.tuku1.service.UserAdministrationService administration;

    @Override
    public long userRegister(String username, String password, String confirmPassword) {
        if (StrUtil.hasBlank(username, password, confirmPassword)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "参数不能为空");
        }
        if (username.length() < 4) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "账号长度不能小于4位");
        }
        UserPasswords.validateNew(password);
        if (!password.equals(confirmPassword)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "两次密码输入不一致");
        }

        LambdaQueryWrapper<User> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(User::getUserAccount, username);
        long count = this.count(queryWrapper);
        if (count > 0) {
            throw new BusinessException(ErrorCode.RESOURCE_CONFLICT, "账号已存在");
        }

        String encryptedPassword = getEncrptPassword(password);

        User user = new User();
        user.setUserAccount(username);
        user.setUserPassword(encryptedPassword);
        user.setUserName("用户" + RandomUtil.randomString(5));
        user.setUserRole(0);
        boolean result = this.save(user);
        if (!result) {
            throw new BusinessException(ErrorCode.SYSTEM_ERROR, "注册失败，数据库错误");
        }
        return user.getId();
    }

    @Override
    public String getEncrptPassword(String userPassword) {
        return UserPasswords.hash(userPassword);
    }

    @Override
    public LoginUserVO userLogin(String username, String password, HttpServletRequest request) {
        String ip = request.getRemoteAddr();
        loginAttemptLimiter.check(username, ip);
        if (StrUtil.hasBlank(username, password) || username.length() < 4 || password.length() < 6) {
            loginAttemptLimiter.failed(username, ip);
            throw LoginAttemptLimiter.rejected();
        }

        LambdaQueryWrapper<User> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(User::getUserAccount, username);
        User user = this.getOne(queryWrapper);
        if (user == null || !UserPasswords.matches(password, user.getUserPassword())) {
            loginAttemptLimiter.failed(username, ip);
            throw LoginAttemptLimiter.rejected();
        }
        if (UserPasswords.isLegacy(user.getUserPassword())) {
            String oldHash = user.getUserPassword();
            String upgraded = UserPasswords.hashLegacy(password);
            User patch = new User(); patch.setUserPassword(upgraded);
            boolean updated = this.update(patch, new LambdaQueryWrapper<User>()
                    .eq(User::getId, user.getId()).eq(User::getUserPassword, oldHash));
            if (!updated) {
                User latest = this.getById(user.getId());
                if (latest == null || !UserPasswords.matches(password, latest.getUserPassword())) {
                    log.warn("升级密码期间凭据已变更");
                    throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "账号或密码错误，请稍后重试");
                }
                user = latest;
            } else {
                user.setUserPassword(upgraded);
            }
        }

        loginAttemptLimiter.succeeded(username, ip);

        // 脱敏后的登录用户信息
        LoginUserVO loginUserVO = userLogin(user);

        // 登录成功后更换会话 ID，避免沿用登录前的会话标识。
        HttpSession session = request.getSession();
        request.changeSessionId();
        session.setAttribute(UserConstant.USER_LOGIN_STATE, loginUserVO);
        session.setAttribute(UserSessionSecurity.VERSION, UserSessionSecurity.version(user));

        return loginUserVO;
    }

    @Override
    public LoginUserVO userLogin(User user) {
        LoginUserVO loginUserVO = new LoginUserVO();
        loginUserVO.setId(user.getId());
        loginUserVO.setUserName(user.getUserName());
        loginUserVO.setUserAccount(user.getUserAccount());
        loginUserVO.setUserAvatar(user.getUserAvatar());
        loginUserVO.setUserRole(user.getUserRole());
        loginUserVO.setUserProfile(user.getUserProfile());
        loginUserVO.setCreateTime(user.getCreateTime());
        loginUserVO.setUpdateTime(user.getUpdateTime());
        loginUserVO.setEditTime(user.getEditTime());
        return loginUserVO;
    }

    @Override
    public UserVO getUserVO(User user) {
        if (user == null) {
            return null;
        }
        UserVO userVO = new UserVO();
        userVO.setId(user.getId());
        userVO.setUserName(user.getUserName());
        userVO.setUserAvatar(user.getUserAvatar());
        userVO.setUserProfile(user.getUserProfile());
        userVO.setCreateTime(user.getCreateTime());
        userVO.setUpdateTime(user.getUpdateTime());
        userVO.setEditTime(user.getEditTime());
        return userVO;
    }

    @Override
    public AdminUserVO getAdminUserVO(User user) {
        if (user == null) return null;
        AdminUserVO result = new AdminUserVO();
        BeanUtils.copyProperties(getUserVO(user), result);
        result.setUserAccount(user.getUserAccount());
        result.setUserRole(user.getUserRole());
        return result;
    }

    @Override
    public List<UserVO> getUserVOList(List<User> userList) {
        if (userList == null || userList.isEmpty()) {
            return new ArrayList<>();
        }
        return userList.stream().map(this::getUserVO).collect(Collectors.toList());
    }

    @Override
    public User getLoginUser(HttpServletRequest request) {
        // 从 session 获取登录态
        HttpSession session = request.getSession(false);
        Object userObj = session == null ? null : session.getAttribute(UserConstant.USER_LOGIN_STATE);
        if (!(userObj instanceof LoginUserVO)) {
            throw new BusinessException(ErrorCode.NOT_LOGIN_ERROR, "未登录");
        }

        LoginUserVO loginUserVO = (LoginUserVO) userObj;

        Long userId = loginUserVO.getId();

        // 查询最新用户信息
        User user = this.getById(userId);
        if (user == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "用户不存在");
        }

        // 校验用户是否已删除
        if (user.getIsDelete() != null && user.getIsDelete() == 1) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "用户已被删除");
        }

        UserSessionSecurity.requireCurrent(session.getAttribute(UserSessionSecurity.VERSION), user);

        return user;
    }

    @Override
    public boolean userLogout(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        Object userObj = session == null ? null : session.getAttribute(UserConstant.USER_LOGIN_STATE);
        if (userObj == null) {
            throw new BusinessException(ErrorCode.NOT_LOGIN_ERROR, "未登录");
        }

        session.invalidate();
        return true;
    }

    @Override
    public QueryWrapper<User> getQueryWrapper(UserQueryRequest userQueryRequest) {
        if (userQueryRequest == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "请求参数为空");
        }
        Long id = userQueryRequest.getId();
        String userAccount = userQueryRequest.getUserAccount();
        String userName = userQueryRequest.getUserName();
        Integer userRole = userQueryRequest.getUserRole();
        String userProfile = userQueryRequest.getUserProfile();
        String sortField = userQueryRequest.getSortField();
        String sortOrder = userQueryRequest.getSortOrder();

        QueryWrapper<User> queryWrapper = new QueryWrapper<>();
        queryWrapper.eq(id != null, "id", id);
        queryWrapper.eq(userRole != null, "userRole", userRole);
        queryWrapper.like(StrUtil.isNotBlank(userAccount), "userAccount", userAccount);
        queryWrapper.like(StrUtil.isNotBlank(userName), "userName", userName);
        queryWrapper.like(StrUtil.isNotBlank(userProfile), "userProfile", userProfile);
        queryWrapper.orderBy(StrUtil.isNotBlank(sortField), "asc".equals(sortOrder), sortField);
        return queryWrapper;
    }

    @Override
    public long addUser(UserAddRequest userAddRequest) {
        if (userAddRequest == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "请求参数为空");
        }
        String userAccount = userAddRequest.getUserAccount();
        if (StrUtil.isBlank(userAccount)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "账号不能为空");
        }
        if (userAccount.length() < 4) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "账号长度不能小于4位");
        }

        String userPassword = userAddRequest.getUserPassword();
        UserPasswords.validateNew(userPassword);

        LambdaQueryWrapper<User> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(User::getUserAccount, userAccount);
        long count = this.count(queryWrapper);
        if (count > 0) {
            throw new BusinessException(ErrorCode.RESOURCE_CONFLICT, "账号已存在");
        }

        User user = new User();
        user.setUserAccount(userAccount);
        user.setUserPassword(getEncrptPassword(userPassword));
        String userName = userAddRequest.getUserName();
        user.setUserName(StrUtil.isBlank(userName) ? "用户" + RandomUtil.randomString(5) : userName);
        user.setUserAvatar(userAddRequest.getUserAvatar());
        Integer userRole = userAddRequest.getUserRole();
        if (userRole != null && userRole != 0 && userRole != 1) {
            log.warn("创建用户的角色不合法");
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "角色不合法");
        }
        user.setUserRole(userRole != null ? userRole : 0);
        user.setUserProfile(userAddRequest.getUserProfile());

        boolean result = this.save(user);
        if (!result) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "创建用户失败");
        }
        return user.getId();
    }

    @Override
    public boolean updateUser(UserUpdateRequest update, HttpServletRequest request) {
        return administration.update(update, getLoginUser(request).getId());
    }

    @Override
    public boolean deleteUser(Long target, HttpServletRequest request) {
        return administration.delete(target, getLoginUser(request).getId());
    }

    @Override
    public learn.gjx.tuku1.model.vo.UserPasswordResetVO resetPassword(Long target, HttpServletRequest request) {
        return administration.resetPassword(target, getLoginUser(request).getId());
    }

    @Override
    public Page<AdminUserVO> listUserVOByPage(UserQueryRequest userQueryRequest) {
        if (userQueryRequest == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "请求参数为空");
        }
        int current = userQueryRequest.getCurrent();
        int pageSize = userQueryRequest.getPageSize();
        if (pageSize > 50) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "每页数量不能超过50");
        }

        QueryWrapper<User> queryWrapper = getQueryWrapper(userQueryRequest);
        Page<User> userPage = this.page(new Page<>(current, pageSize), queryWrapper);

        Page<AdminUserVO> userVOPage = new Page<>(current, pageSize, userPage.getTotal());
        List<AdminUserVO> userVOList = userPage.getRecords().stream()
                .map(this::getAdminUserVO).collect(Collectors.toList());
        userVOPage.setRecords(userVOList);

        return userVOPage;
    }

    @Override
    public LoginUserVO updateMyProfile(UserProfileUpdateRequest update, HttpServletRequest request) {
        User current = getLoginUser(request);
        if (update == null || StrUtil.isBlank(update.getUserName()) || update.getUserName().trim().length() > 32
                || (update.getUserProfile() != null && update.getUserProfile().length() > 128)) {
            log.warn("本人资料参数不符合长度规则");
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "昵称为1至32位，简介不超过128位");
        }
        User patch = new User(); patch.setId(current.getId());
        patch.setUserName(update.getUserName().trim());
        patch.setUserProfile(update.getUserProfile() == null ? "" : update.getUserProfile());
        patch.setEditTime(new Date());
        if (!this.updateById(patch)) {
            log.warn("本人资料更新失败");
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "资料保存失败");
        }
        current.setUserName(patch.getUserName()); current.setUserProfile(patch.getUserProfile());
        current.setEditTime(patch.getEditTime());
        LoginUserVO result = userLogin(current);
        request.getSession().setAttribute(UserConstant.USER_LOGIN_STATE, result);
        return result;
    }

    @Override
    public boolean updateMyPassword(UserPasswordUpdateRequest update, HttpServletRequest request) {
        User current = getLoginUser(request);
        if (update == null) {
            log.warn("修改密码参数缺失");
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "请求参数不能为空");
        }
        UserPasswords.validateNew(update.getNewPassword());
        if (!update.getNewPassword().equals(update.getCheckPassword())) {
            log.warn("修改密码确认不一致");
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "两次密码输入不一致");
        }
        String ip = request.getRemoteAddr();
        loginAttemptLimiter.check(current.getUserAccount(), ip);
        if (!UserPasswords.matches(update.getOldPassword(), current.getUserPassword())) {
            loginAttemptLimiter.failed(current.getUserAccount(), ip);
            throw LoginAttemptLimiter.rejected();
        }
        User patch = new User(); patch.setUserPassword(UserPasswords.hash(update.getNewPassword()));
        patch.setEditTime(new Date());
        if (!this.update(patch, new LambdaQueryWrapper<User>().eq(User::getId, current.getId())
                .eq(User::getUserPassword, current.getUserPassword()))) {
            log.warn("修改密码时凭据已被并发更新");
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "密码已变更，请重新登录后重试");
        }
        current.setUserPassword(patch.getUserPassword()); current.setEditTime(patch.getEditTime());
        HttpSession session = request.getSession(); request.changeSessionId();
        session.setAttribute(UserConstant.USER_LOGIN_STATE, userLogin(current));
        session.setAttribute(UserSessionSecurity.VERSION, UserSessionSecurity.version(current));
        return true;
    }

    @Override
    public boolean isAdmin(User user) {
        return user != null && UserRoleEnum.ADMIN.getValue() == user.getUserRole();
    }
}
