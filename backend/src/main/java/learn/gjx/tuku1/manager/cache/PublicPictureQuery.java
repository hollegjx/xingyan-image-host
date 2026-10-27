package learn.gjx.tuku1.manager.cache;

import learn.gjx.tuku1.exception.BusinessException;
import learn.gjx.tuku1.exception.ErrorCode;
import learn.gjx.tuku1.model.dto.picture.PictureQueryRequest;
import learn.gjx.tuku1.model.enums.PictureReviewStatusEnum;
import learn.gjx.tuku1.model.enums.PictureUploadStatusEnum;
import org.springframework.beans.BeanUtils;

import java.util.*;
import java.util.stream.Collectors;

/** Normalize once, then use the same request for both SQL and cache keys. */
public final class PublicPictureQuery {
    private static final Set<String> SORT_FIELDS = new HashSet<>(Arrays.asList(
            "id", "name", "createTime", "editTime", "updateTime", "picSize",
            "picWidth", "picHeight", "picScale"));

    private PublicPictureQuery() {
    }

    public static PictureQueryRequest normalize(PictureQueryRequest source) {
        require(source == null || source.getSpaceId() == null, "公开列表不接受空间 ID");
        PictureQueryRequest query = normalizeFilters(source);
        require(query.getPageSize() <= 20, "每页数量必须在 1 到 20 之间");
        // Never trust client-supplied visibility, even for logged-in administrators.
        query.setReviewStatus(PictureReviewStatusEnum.PASS.getValue());
        query.setUploadStatus(PictureUploadStatusEnum.COMPLETED.getValue());
        return query;
    }

    /** Shared input validation only; this does NOT grant access to a private space. */
    public static PictureQueryRequest normalizeFilters(PictureQueryRequest source) {
        PictureQueryRequest query = new PictureQueryRequest();
        if (source != null) {
            BeanUtils.copyProperties(source, query);
        }
        if (query.getCurrent() == null) {
            query.setCurrent(1);
        }
        if (query.getPageSize() == null) {
            query.setPageSize(10);
        }
        require(query.getCurrent() > 0, "页码必须大于 0");
        // Shared admin filters preserve its existing 10/20/50/100 page-size options.
        require(query.getPageSize() > 0 && query.getPageSize() <= 100, "每页数量必须在 1 到 100 之间");
        query.setName(text(query.getName(), 128));
        query.setIntroduction(text(query.getIntroduction(), 500));
        query.setCategory(text(query.getCategory(), 64));
        query.setSearchText(text(query.getSearchText(), 128));
        query.setPicFormat(text(query.getPicFormat(), 32));
        if (query.getPicFormat() != null) {
            query.setPicFormat(query.getPicFormat().toLowerCase(Locale.ROOT));
        }
        require(query.getPicWidth() == null || query.getPicWidth() > 0, "图片宽度必须大于 0");
        require(query.getPicHeight() == null || query.getPicHeight() > 0, "图片高度必须大于 0");
        require(query.getPicSize() == null || query.getPicSize() > 0, "图片体积必须大于 0");
        query.setStartEditTime(date(query.getStartEditTime()));
        query.setEndEditTime(date(query.getEndEditTime()));
        require(query.getStartEditTime() == null || query.getEndEditTime() == null
                || query.getStartEditTime().before(query.getEndEditTime()), "开始编辑时间必须早于结束编辑时间");
        if (query.getTags() != null) {
            require(query.getTags().size() <= 20, "标签数量不能超过 20");
            List<String> tags = query.getTags().stream().map(tag -> text(tag, 64))
                    .filter(Objects::nonNull).distinct().sorted().collect(Collectors.toList());
            query.setTags(tags.isEmpty() ? null : tags);
        }
        String sortField = text(query.getSortField(), 32);
        String sortOrder = text(query.getSortOrder(), 8);
        query.setSortField(sortField == null ? "createTime" : sortField);
        query.setSortOrder(sortOrder == null ? "desc" : sortOrder.toLowerCase(Locale.ROOT));
        require(SORT_FIELDS.contains(query.getSortField()), "不支持的排序字段");
        require(Arrays.asList("asc", "desc").contains(query.getSortOrder()), "排序方式必须是 asc 或 desc");
        require(query.getPicScale() == null || (Double.isFinite(query.getPicScale()) && query.getPicScale() > 0),
                "图片比例必须是正数");
        require(query.getSpaceId() == null || query.getSpaceId() > 0, "空间 ID 不合法");
        return query;
    }

    private static String text(String value, int maxLength) {
        if (value == null || value.trim().isEmpty()) {
            return null;
        }
        require(value.length() <= maxLength, "查询条件过长");
        return value.trim();
    }

    private static Date date(Date value) {
        if (value == null) { return null; }
        // Reject timestamps outside the application's supported calendar range before SQL.
        long millis = value.getTime();
        require(millis >= -28800000L && millis <= 253402214400000L, "编辑时间超出支持范围（1970—9999 年）");
        return new Date(millis); // Date is mutable: SQL and the cache key must use a stable snapshot.
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, message);
        }
    }
}
