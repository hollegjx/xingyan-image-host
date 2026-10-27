package learn.gjx.tuku1.model.enums;

/**
 * 图片审核状态枚举
 */
public enum PictureReviewStatusEnum {

    REVIEWING("待审核", 0),
    PASS("通过", 1),
    REJECT("拒绝", 2);

    private final String name;

    private final int value;

    PictureReviewStatusEnum(String name, int value) {
        this.name = name;
        this.value = value;
    }

    public String getName() {
        return name;
    }

    public int getValue() {
        return value;
    }

    public static PictureReviewStatusEnum getEnumByValue(Integer value) {
        if (value == null) {
            return null;
        }
        for (PictureReviewStatusEnum statusEnum : PictureReviewStatusEnum.values()) {
            if (statusEnum.value == value) {
                return statusEnum;
            }
        }
        return null;
    }

    public static boolean isReviewResult(Integer value) {
        if (value == null) {
            return false;
        }
        return PASS.value == value || REJECT.value == value;
    }
}
