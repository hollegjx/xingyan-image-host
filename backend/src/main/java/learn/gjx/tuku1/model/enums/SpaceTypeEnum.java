package learn.gjx.tuku1.model.enums;

import lombok.Getter;

@Getter
public enum SpaceTypeEnum {
    PRIVATE(0), TEAM(1);
    private final int value;
    SpaceTypeEnum(int value) { this.value = value; }
    public static boolean valid(Integer value) { return value != null && (value == 0 || value == 1); }
}
