package learn.gjx.tuku1.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import learn.gjx.tuku1.model.enums.UserRoleEnum;

@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface AuthCheck {
    /**
     * 需要具备的角色，默认登录即可访问。
     */
    UserRoleEnum mustRole() default UserRoleEnum.USER;
}
