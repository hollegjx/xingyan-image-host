package learn.gjx.tuku1.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import learn.gjx.tuku1.model.dto.user.UserAddRequest;
import learn.gjx.tuku1.model.dto.user.UserQueryRequest;
import learn.gjx.tuku1.model.dto.user.UserUpdateRequest;
import learn.gjx.tuku1.model.entity.User;
import com.baomidou.mybatisplus.extension.service.IService;
import learn.gjx.tuku1.model.vo.LoginUserVO;
import learn.gjx.tuku1.model.vo.UserVO;
import learn.gjx.tuku1.model.vo.AdminUserVO;

import javax.servlet.http.HttpServletRequest;
import java.util.List;

/**
* @author GJX
* @description 针对表【user(用户表)】的数据库操作Service
* @createDate 2025-11-11 17:59:36
*/
public interface UserService extends IService<User> {

    /**
     * 用户注册
     * @param username 用户账号
     * @param password 用户密码
     * @param confirmPassword 确认密码
     * @return 新注册用户的 ID
     */
    long userRegister(String username, String password, String confirmPassword);

    /**
     * 密码加密
     * @param userPassword 用户原始密码
     * @return 加密后的密码
     */
    String getEncrptPassword(String userPassword);

    /**
     * 用户登录
     * @param username 用户账号
     * @param password 用户密码
     * @param request HTTP 请求对象，用于保存登录态
     * @return 登录成功的用户信息（脱敏）
     */
    LoginUserVO userLogin(String username, String password, HttpServletRequest request);

    /**
     * 将 User 实体转换为 LoginUserVO（脱敏）
     * @param user 用户实体对象
     * @return 脱敏后的用户视图对象
     */
    LoginUserVO userLogin(User user);

    /**
     * 获取脱敏后的用户信息
     * @param user 用户实体对象
     * @return 脱敏后的用户视图对象
     */
    UserVO getUserVO(User user);

    /** 获取仅供管理员接口使用的用户信息。 */
    AdminUserVO getAdminUserVO(User user);

    /**
     * 获取脱敏后的用户信息列表
     * @param userList 用户实体列表
     * @return 脱敏后的用户视图对象列表
     */
    List<UserVO> getUserVOList(List<User> userList);

    /**
     * 获取当前登录用户
     * @param request HTTP 请求对象
     * @return 当前登录的用户实体
     */
    User getLoginUser(HttpServletRequest request);

    /**
     * 根据查询条件构建 QueryWrapper
     * @param userQueryRequest 用户查询请求参数
     * @return 构建好的查询条件包装器
     */
    QueryWrapper<User> getQueryWrapper(UserQueryRequest userQueryRequest);

    /**
     * 用户登出
     * @param request HTTP 请求对象
     * @return 登出是否成功
     */
    boolean userLogout(HttpServletRequest request);

    /**
     * 管理员添加用户
     * @param userAddRequest 用户添加请求参数
     * @return 新创建用户的ID
     */
    long addUser(UserAddRequest userAddRequest);

    /**
     * 管理员更新用户
     * @param userUpdateRequest 用户更新请求参数
     * @return 更新是否成功
     */
    boolean updateUser(UserUpdateRequest userUpdateRequest);

    /**
     * 分页查询用户脱敏信息
     * @param userQueryRequest 用户查询请求参数（含分页）
     * @return 分页后的用户脱敏信息
     */
    Page<AdminUserVO> listUserVOByPage(UserQueryRequest userQueryRequest);

    /**
     * 判断用户是否为管理员
     * @param user 用户实体
     * @return 是否为管理员
     */
    boolean isAdmin(User user);
}
