package learn.gjx.tuku1.model.enums;

import cn.hutool.core.util.ObjUtil;

public enum UserRoleEnum {
    USER("用户",0),
    ADMIN("管理员",1);

    private String name;
    private int value;
    UserRoleEnum(String name, int value) {
        this.name = name;
        this.value = value;
    }

    public String getName() {
        return name;
    }

    public int getValue() {
        return value;
    }

    public static  UserRoleEnum getEnumByValue(int value) {
        if(ObjUtil.isEmpty(value)){
            return null;
        }
        for (UserRoleEnum userRoleEnum : UserRoleEnum.values()) {
            if(userRoleEnum.value==value){
                return userRoleEnum;
            }
        }
        return null;
    }
}
