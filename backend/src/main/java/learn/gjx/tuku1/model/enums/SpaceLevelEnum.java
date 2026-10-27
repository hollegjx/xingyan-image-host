package learn.gjx.tuku1.model.enums;

import lombok.Getter;

@Getter
public enum SpaceLevelEnum {
    COMMON(0, "普通版", 100L, 100L * 1024 * 1024),
    PROFESSIONAL(1, "专业版", 1000L, 1000L * 1024 * 1024),
    FLAGSHIP(2, "旗舰版", 10000L, 10000L * 1024 * 1024);

    private final int value;
    private final String text;
    private final long maxCount;
    private final long maxSize;

    SpaceLevelEnum(int value, String text, long maxCount, long maxSize) {
        this.value = value;
        this.text = text;
        this.maxCount = maxCount;
        this.maxSize = maxSize;
    }

    public static SpaceLevelEnum getEnumByValue(Integer value) {
        if (value == null) {
            return null;
        }
        for (SpaceLevelEnum level : values()) {
            if (level.value == value) {
                return level;
            }
        }
        return null;
    }
}
