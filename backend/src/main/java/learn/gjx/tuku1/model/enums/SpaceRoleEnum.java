package learn.gjx.tuku1.model.enums;

import java.util.*;
import static learn.gjx.tuku1.manager.space.SpacePermission.*;

/** Fixed RBAC policy: platform userRole never implies membership in a team. */
public enum SpaceRoleEnum {
    VIEWER("viewer", VIEW),
    EDITOR("editor", VIEW, UPLOAD, EDIT, DELETE),
    ADMIN("admin", VIEW, UPLOAD, EDIT, DELETE, MEMBERS, ANALYZE);
    private final String value;
    private final List<String> permissions;
    SpaceRoleEnum(String value, String... permissions) {
        this.value = value;
        this.permissions = Collections.unmodifiableList(Arrays.asList(permissions));
    }
    public String getValue() { return value; }
    public List<String> getPermissions() { return permissions; }
    public static SpaceRoleEnum from(String value) {
        for (SpaceRoleEnum role : values()) if (role.value.equals(value)) return role;
        return null;
    }
}
