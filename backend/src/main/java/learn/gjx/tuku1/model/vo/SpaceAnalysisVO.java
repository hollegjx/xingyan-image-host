package learn.gjx.tuku1.model.vo;

import lombok.Data;
import java.util.List;

/** 仅返回统计结果，不包含图片实体、对象地址或签名。 */
@Data
public class SpaceAnalysisVO {
    private String scope;
    private Long spaceId;
    private String spaceName;
    private String generatedAt;
    private Usage usage;
    private List<Distribution> categories;
    private List<Distribution> tags;
    private List<Distribution> sizes;
    private Trend trend;
    private List<Ranking> rankings;

    @Data
    public static class Usage {
        private Long usedSize;
        private Long usedCount;
        private Long completedCount;
        private Long draftCount;
        private Long maxSize;
        private Long maxCount;
        /** 无配额或配额为零时为 null，绝不除零或伪称 0%。 */
        private Double sizeUsageRatio;
        private Double countUsageRatio;
    }

    @Data
    public static class Distribution {
        private String name;
        private Long count;
        private Long totalSize;
        public Distribution() { }
        public Distribution(String name, long count, long totalSize) {
            this.name = name;
            this.count = count;
            this.totalSize = totalSize;
        }
    }

    @Data
    public static class Trend {
        private String timeDimension;
        private String startDate;
        private String endDate;
        private String timezone;
        private List<Distribution> points;
    }

    @Data
    public static class Ranking {
        private Long spaceId;
        private String spaceName;
        private Long usedSize;
        private Long usedCount;
    }
}
