package learn.gjx.tuku1.controller;



import learn.gjx.tuku1.annotation.AuthCheck;
import learn.gjx.tuku1.common.BaseResponse;
import learn.gjx.tuku1.model.dto.user.UserAddRequest;
import learn.gjx.tuku1.model.dto.user.UserProfileUpdateRequest;
import learn.gjx.tuku1.model.dto.user.UserPasswordUpdateRequest;
import learn.gjx.tuku1.model.dto.user.UserDeleteRequest;
import learn.gjx.tuku1.model.dto.user.UserLoginRequest;
import learn.gjx.tuku1.model.dto.user.UserQueryRequest;
import learn.gjx.tuku1.model.dto.user.UserRegisterRequest;
import learn.gjx.tuku1.model.dto.user.UserUpdateRequest;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import learn.gjx.tuku1.model.entity.User;
import learn.gjx.tuku1.model.enums.UserRoleEnum;
import learn.gjx.tuku1.exception.ErrorCode;
import learn.gjx.tuku1.exception.ThrowUtils;
import learn.gjx.tuku1.model.vo.LoginUserVO;
import learn.gjx.tuku1.model.vo.UserVO;
import learn.gjx.tuku1.model.vo.AdminUserVO;
import learn.gjx.tuku1.service.UserService;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;

@RestController
@RequestMapping("/user")
public class UserController {
    @Resource
    private UserService userService;

    /**
     * 用户注册
     * @param userRegisterRequest 用户注册请求体，包含账号、密码和确认密码
     * @return 注册成功返回包含用户ID的成功响应，格式为 BaseResponse<Long>
     */
    @PostMapping("/register")
    public BaseResponse<Long> userRegister(@RequestBody UserRegisterRequest userRegisterRequest) {
        String userAccount = userRegisterRequest.getUserAccount();
        String userPassword = userRegisterRequest.getUserPassword();
        String checkPassword = userRegisterRequest.getCheckPassword();
        long userId = userService.userRegister(userAccount, userPassword, checkPassword);
        return BaseResponse.success(userId);
    }

    /**
     * 用户登录
     * @param userLoginRequest 用户登录请求体，包含账号和密码
     * @param request HTTP 请求对象
     * @return 登录成功返回包含用户信息（脱敏）的成功响应
     */
    @PostMapping("/login")
    public BaseResponse<LoginUserVO> userLogin(@RequestBody UserLoginRequest userLoginRequest, HttpServletRequest request) {
        String userAccount = userLoginRequest.getUserAccount();
        String userPassword = userLoginRequest.getUserPassword();
        LoginUserVO loginUserVO = userService.userLogin(userAccount, userPassword, request);
        return BaseResponse.success(loginUserVO);
    }

    /**
     * 获取当前登录用户
     * @param request HTTP 请求对象
     * @return 当前登录用户信息（脱敏）
     */
    @GetMapping("/get/login")
    public BaseResponse<LoginUserVO> getLoginUser(HttpServletRequest request) {
        User user = userService.getLoginUser(request);
        LoginUserVO loginUserVO = userService.userLogin(user);
        return BaseResponse.success(loginUserVO);
    }

    /**
     * 用户登出
     * @param request HTTP 请求对象
     * @return 登出成功返回 true
     */
    @PostMapping("/logout")
    public BaseResponse<Boolean> userLogout(HttpServletRequest request) {
        boolean result = userService.userLogout(request);
        return BaseResponse.success(result);
    }

    /**
     * 管理员创建用户
     * @param userAddRequest 用户创建请求体，包含账号、密码、用户名、头像、角色、简介
     * @return 创建成功返回新用户的ID
     */
    @PostMapping("/add")
    @AuthCheck(mustRole = UserRoleEnum.ADMIN)
    public BaseResponse<Long> addUser(@RequestBody UserAddRequest userAddRequest) {
        long userId = userService.addUser(userAddRequest);
        return BaseResponse.success(userId);
    }

    /**
     * 根据ID获取用户（仅管理员）
     * @param id 用户ID
     * @return 管理员用户信息（不含密码哈希）
     */
    @GetMapping("/get")
    @AuthCheck(mustRole = UserRoleEnum.ADMIN)
    public BaseResponse<AdminUserVO> getUserById(long id) {
        ThrowUtils.throwIf(id <= 0, ErrorCode.PARAMS_ERROR, "用户ID不合法");
        User user = userService.getById(id);
        ThrowUtils.throwIf(user == null, ErrorCode.NOT_FOUND_ERROR, "用户不存在");
        return BaseResponse.success(userService.getAdminUserVO(user));
    }

    /**
     * 根据ID获取用户脱敏信息
     * @param id 用户ID
     * @return 用户脱敏信息（不含密码等敏感字段）
     */
    @GetMapping("/get/vo")
    public BaseResponse<UserVO> getUserVOById(long id) {
        ThrowUtils.throwIf(id <= 0, ErrorCode.PARAMS_ERROR, "用户ID不合法");
        User user = userService.getById(id);
        ThrowUtils.throwIf(user == null, ErrorCode.NOT_FOUND_ERROR, "用户不存在");
        return BaseResponse.success(userService.getUserVO(user));
    }

    /**
     * 管理员删除用户
     * @param userDeleteRequest 删除请求体，包含用户ID
     * @return 删除成功返回 true
     */
    @PostMapping("/delete")
    @AuthCheck(mustRole = UserRoleEnum.ADMIN)
    public BaseResponse<Boolean> deleteUser(@RequestBody UserDeleteRequest userDeleteRequest, HttpServletRequest request) {
        ThrowUtils.throwIf(userDeleteRequest == null || userDeleteRequest.getId() == null, ErrorCode.PARAMS_ERROR);
        Long id = userDeleteRequest.getId();
        ThrowUtils.throwIf(id <= 0, ErrorCode.PARAMS_ERROR, "用户ID不合法");
        boolean result = userService.deleteUser(id, request);
        ThrowUtils.throwIf(!result, ErrorCode.OPERATION_ERROR, "删除用户失败");
        return BaseResponse.success(true);
    }

    /**
     * 管理员更新用户
     * @param userUpdateRequest 更新请求体，包含用户ID和需要更新的字段
     * @return 更新成功返回 true
     */
    @PostMapping("/update")
    @AuthCheck(mustRole = UserRoleEnum.ADMIN)
    public BaseResponse<Boolean> updateUser(@RequestBody UserUpdateRequest userUpdateRequest, HttpServletRequest request) {
        boolean result = userService.updateUser(userUpdateRequest, request);
        return BaseResponse.success(result);
    }

    /**
     * 分页查询用户脱敏信息
     * @param userQueryRequest 查询请求体，包含查询条件和分页参数
     * @return 分页后的用户脱敏信息列表
     */
    @PostMapping("/list/page/vo")
    @AuthCheck(mustRole = UserRoleEnum.ADMIN)
    public BaseResponse<Page<AdminUserVO>> listUserVOByPage(@RequestBody UserQueryRequest userQueryRequest) {
        Page<AdminUserVO> userVOPage = userService.listUserVOByPage(userQueryRequest);
        return BaseResponse.success(userVOPage);
    }

    /**
     * 修改本人资料。
     * @param update 昵称、简介白名单
     * @param request 当前会话
     * @return 更新后的本人资料
     */
    @PostMapping("/update/my")
    @AuthCheck
    public BaseResponse<LoginUserVO> updateMyProfile(@RequestBody UserProfileUpdateRequest update, HttpServletRequest request) {
        return BaseResponse.success(userService.updateMyProfile(update, request));
    }

    /**
     * 修改本人密码，保留当前会话并使其他会话失效。
     * @param update 旧密码、新密码和确认密码
     * @param request 当前会话
     * @return 修改成功状态
     */
    @PostMapping("/password/update")
    @AuthCheck
    public BaseResponse<Boolean> updateMyPassword(@RequestBody UserPasswordUpdateRequest update, HttpServletRequest request) {
        return BaseResponse.success(userService.updateMyPassword(update, request));
    }

    /**
     * 管理员重置密码；响应不缓存，生成的临时密码只返回本次。
     * @param target 目标用户 ID
     * @param request 当前管理员会话
     * @param response 禁止凭据响应缓存
     * @return 一次性展示的临时密码
     */
    @PostMapping("/password/reset")
    @AuthCheck(mustRole = UserRoleEnum.ADMIN)
    public BaseResponse<learn.gjx.tuku1.model.vo.UserPasswordResetVO> resetPassword(
            @RequestBody UserDeleteRequest target, HttpServletRequest request, javax.servlet.http.HttpServletResponse response) {
        response.setHeader("Cache-Control", "no-store");
        response.setHeader("Pragma", "no-cache");
        return BaseResponse.success(userService.resetPassword(target.getId(), request));
    }

}
