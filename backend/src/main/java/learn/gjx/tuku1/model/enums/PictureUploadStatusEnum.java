package learn.gjx.tuku1.model.enums;

/**
 * 图片上传流程状态。
 *
 * <p>文件上传成功后先保存为草稿，用户提交图片信息后才标记为已完成。</p>
 */
public enum PictureUploadStatusEnum {

    DRAFT("草稿", 0),
    COMPLETED("已完成", 1);

    private final String name;

    private final int value;

    PictureUploadStatusEnum(String name, int value) {
        this.name = name;
        this.value = value;
    }

    public String getName() {
        return name;
    }

    public int getValue() {
        return value;
    }
}
