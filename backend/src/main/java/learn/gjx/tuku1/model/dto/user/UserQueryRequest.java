package learn.gjx.tuku1.model.dto.user;

import lombok.Data;

import java.io.Serializable;

/**
 * 管理员查询用户请求
 */
@Data
public class UserQueryRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /**
     * 用户id
     */
    private Long id;

    /**
     * 用户账号
     */
    private String userAccount;

    /**
     * 用户名
     */
    private String userName;

    /**
     * 用户角色 0-普通用户 1-管理员
     */
    private Integer userRole;

    /**
     * 用户简介
     */
    private String userProfile;

    /**
     * 当前页号
     */
    private Integer current = 1;

    /**
     * 每页大小
     */
    private Integer pageSize = 10;

    /**
     * 排序字段
     */
    private String sortField;

    /**
     * 排序方式 asc/desc
     */
    private String sortOrder;
}
