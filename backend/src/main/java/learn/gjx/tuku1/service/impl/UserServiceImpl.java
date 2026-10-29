package learn.gjx.tuku1.service.impl;

import cn.hutool.core.util.RandomUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.crypto.digest.DigestUtil;
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
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements UserService {

    /**
     * 盐值：和原始密码拼接后再做 md5，避免直接存明文密码。
     */
    private static final String SALT = "tuku1_salt_2025";

    /**
     * 管理员创建用户但未指定密码时使用的默认密码。
     */
    private static final String DEFAULT_PASSWORD = "12345678";

    @Override
    public long userRegister(String username, String password, String confirmPassword) {
        if (StrUtil.hasBlank(username, password, confirmPassword)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "参数不能为空");
        }
        if (username.length() < 4) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "账号长度不能小于4位");
        }
        if (password.length() < 6) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "密码长度不能小于6位");
        }
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
        return DigestUtil.md5Hex(SALT + userPassword);
    }

    @Override
    public LoginUserVO userLogin(String username, String password, HttpServletRequest request) {
        if (StrUtil.hasBlank(username, password)) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "参数不能为空");
        }
        if (username.length() < 4) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "账号长度不能小于4位");
        }
        if (password.length() < 6) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "密码长度不能小于6位");
        }

        // 加密密码
        String encryptedPassword = getEncrptPassword(password);

        // 查询用户
        LambdaQueryWrapper<User> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(User::getUserAccount, username);
        queryWrapper.eq(User::getUserPassword, encryptedPassword);
        User user = this.getOne(queryWrapper);
        if (user == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "用户不存在或密码错误");
        }

        // 脱敏后的登录用户信息
        LoginUserVO loginUserVO = userLogin(user);

        // 登录成功后更换会话 ID，避免沿用登录前的会话标识。
        HttpSession session = request.getSession();
        request.changeSessionId();
        session.setAttribute(UserConstant.USER_LOGIN_STATE, loginUserVO);

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
        if (StrUtil.isBlank(userPassword)) {
            userPassword = DEFAULT_PASSWORD;
        }
        if (userPassword.length() < 6) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "密码长度不能小于6位");
        }

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
        user.setUserRole(userRole != null ? userRole : 0);
        user.setUserProfile(userAddRequest.getUserProfile());

        boolean result = this.save(user);
        if (!result) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "创建用户失败");
        }
        return user.getId();
    }

    @Override
    public boolean updateUser(UserUpdateRequest userUpdateRequest) {
        if (userUpdateRequest == null || userUpdateRequest.getId() == null) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "请求参数为空");
        }
        Long id = userUpdateRequest.getId();
        if (id <= 0) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "用户ID不合法");
        }

        User oldUser = this.getById(id);
        if (oldUser == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "用户不存在");
        }

        User user = new User();
        user.setId(id);
        user.setUserName(userUpdateRequest.getUserName());
        user.setUserAvatar(userUpdateRequest.getUserAvatar());
        user.setUserRole(userUpdateRequest.getUserRole());
        user.setUserProfile(userUpdateRequest.getUserProfile());

        boolean result = this.updateById(user);
        if (!result) {
            throw new BusinessException(ErrorCode.OPERATION_ERROR, "更新用户失败");
        }
        return true;
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
    public boolean isAdmin(User user) {
        return user != null && UserRoleEnum.ADMIN.getValue() == user.getUserRole();
    }
}
