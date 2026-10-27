package learn.gjx.tuku1.model.dto.user;

import lombok.Data;

import java.io.Serializable;

/**
 * 管理员更新用户请求
 */
@Data
public class UserUpdateRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 用户id
     */
    private Long id;

    /**
     * 用户名
     */
    private String userName;

    /**
     * 用户头像
     */
    private String userAvatar;

    /**
     * 用户角色 0-普通用户 1-管理员
     */
    private Integer userRole;

    /**
     * 用户简介
     */
    private String userProfile;
}
