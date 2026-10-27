package learn.gjx.tuku1.manager.space;

import learn.gjx.tuku1.model.vo.SpaceAnalysisVO.Distribution;
import learn.gjx.tuku1.model.vo.SpaceAnalysisVO.Ranking;
import learn.gjx.tuku1.model.vo.SpaceAnalysisVO.Usage;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import javax.annotation.Resource;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** MySQL 8 聚合查询。只拼接服务端固定 SQL，所有外部值绑定参数；不拉取图片或 URL。 */
@Repository
public class SpaceAnalysisRepository {
    @Resource
    private JdbcTemplate jdbc;
    private static final String BYTES = "CASE WHEN p.picSize > 0 THEN p.picSize ELSE 0 END";
    // 全局也排除已删除/孤立空间，避免把已经不可访问的内容纳入统计。
    private static final String LIVE = " p.isDelete=0 AND (p.spaceId IS NULL OR EXISTS "
            + "(SELECT 1 FROM space s WHERE s.id=p.spaceId AND s.isDelete=0)) ";

    private String where(String scope, Long spaceId, boolean completed, List<Object> args) {
        String sql = LIVE;
        if ("SPACE".equals(scope)) {
            sql += " AND p.spaceId=?";
            args.add(spaceId);
        } else if ("PUBLIC".equals(scope)) {
            sql += " AND p.spaceId IS NULL";
        } else if (!"ALL".equals(scope)) {
            throw new IllegalArgumentException("Unknown analysis scope");
        }
        return sql + (completed ? " AND p.uploadStatus=1" : "");
    }

    public Usage usage(String scope, Long spaceId) {
        List<Object> args = new ArrayList<>();
        String filter = where(scope, spaceId, false, args);
        return jdbc.queryForObject("SELECT COUNT(*) n, COALESCE(SUM(" + BYTES + "),0) size, "
                        + "COALESCE(SUM(CASE WHEN p.uploadStatus=1 THEN 1 ELSE 0 END),0) completed, "
                        + "COALESCE(SUM(CASE WHEN p.uploadStatus=0 THEN 1 ELSE 0 END),0) draft "
                        + "FROM picture p WHERE " + filter, (rs, row) -> {
                    Usage v = new Usage();
                    v.setUsedCount(rs.getLong("n"));
                    v.setUsedSize(rs.getLong("size"));
                    v.setCompletedCount(rs.getLong("completed"));
                    v.setDraftCount(rs.getLong("draft"));
                    return v;
                }, args.toArray());
    }

    public List<Distribution> categories(String scope, Long spaceId) {
        List<Object> args = new ArrayList<>();
        String filter = where(scope, spaceId, true, args);
        List<Distribution> result = jdbc.query("SELECT COALESCE(NULLIF(TRIM(p.category),''),'未分类') label, "
                        + "COUNT(*) n, COALESCE(SUM(" + BYTES + "),0) size FROM picture p WHERE "
                        + filter + " GROUP BY label ORDER BY n DESC,label ASC LIMIT 20",
                (rs, row) -> new Distribution(rs.getString("label"), rs.getLong("n"), rs.getLong("size")),
                args.toArray());
        // 尾部分类合并为“其他”，不能用 Top 20 的小计冒充全部分布。
        Distribution total = jdbc.queryForObject("SELECT COUNT(*) n, COALESCE(SUM(" + BYTES
                        + "),0) size FROM picture p WHERE " + filter,
                (rs, row) -> new Distribution("其他分类（合并）", rs.getLong("n"), rs.getLong("size")), args.toArray());
        if (total != null) {
            for (Distribution item : result) {
                total.setCount(total.getCount() - item.getCount());
                total.setTotalSize(total.getTotalSize() - item.getTotalSize());
            }
            if (total.getCount() > 0) result.add(total);
        }
        return result;
    }

    public List<Distribution> tags(String scope, Long spaceId) {
        List<Object> args = new ArrayList<>();
        String filter = where(scope, spaceId, true, args);
        // 非法 JSON 和非数组都安全转换为空数组；只接受字符串，单图重复标签只计一次。
        String safeJson = "CASE WHEN JSON_VALID(p.tags) THEN CASE WHEN JSON_TYPE(p.tags)='ARRAY' "
                + "THEN p.tags ELSE '[]' END ELSE '[]' END";
        String element = "JSON_EXTRACT(" + safeJson + ",CONCAT('$[',j.pos-1,']'))";
        String sql = "SELECT label,COUNT(*) n FROM (SELECT DISTINCT p.id, "
                + "TRIM(JSON_UNQUOTE(" + element + ")) COLLATE utf8mb4_bin label "
                + "FROM picture p JOIN JSON_TABLE(" + safeJson
                + ", '$[*]' COLUMNS (pos FOR ORDINALITY)) j ON TRUE WHERE " + filter
                + " AND JSON_TYPE(" + element + ")='STRING') tag_rows "
                + "WHERE CHAR_LENGTH(label) BETWEEN 1 AND 512 GROUP BY label ORDER BY n DESC,label ASC LIMIT 30";
        return jdbc.query(sql, (rs, row) -> new Distribution(rs.getString("label"), rs.getLong("n"), 0),
                args.toArray());
    }

    public List<Distribution> sizes(String scope, Long spaceId) {
        List<Object> args = new ArrayList<>();
        String filter = where(scope, spaceId, true, args);
        String bucket = "CASE WHEN p.picSize IS NULL OR p.picSize<0 THEN 5 "
                + "WHEN p.picSize<102400 THEN 0 WHEN p.picSize<524288 THEN 1 "
                + "WHEN p.picSize<1048576 THEN 2 WHEN p.picSize<2097152 THEN 3 ELSE 4 END";
        List<Distribution> result = new ArrayList<>();
        for (String name : Arrays.asList("小于 100 KiB", "100～512 KiB", "512 KiB～1 MiB", "1～2 MiB", "至少 2 MiB", "大小未知")) {
            result.add(new Distribution(name, 0, 0));
        }
        jdbc.query("SELECT " + bucket + " bucket,COUNT(*) n,COALESCE(SUM(" + BYTES
                + "),0) size FROM picture p WHERE " + filter + " GROUP BY bucket", rs -> {
            Distribution v = result.get(rs.getInt("bucket"));
            v.setCount(rs.getLong("n"));
            v.setTotalSize(rs.getLong("size"));
        }, args.toArray());
        return result;
    }

    public List<Distribution> daily(String scope, Long spaceId, Long userId, LocalDate start, LocalDate end) {
        List<Object> args = new ArrayList<>();
        String filter = where(scope, spaceId, true, args);
        // DATETIME 采用现有业务库 Asia/Shanghai 的墙上时间；不用 JVM 默认时区转换 Date。
        filter += " AND p.createTime>=? AND p.createTime<?";
        args.add(start + " 00:00:00");
        args.add(end.plusDays(1) + " 00:00:00");
        if (userId != null) {
            filter += " AND p.userId=?";
            args.add(userId);
        }
        return jdbc.query("SELECT CAST(p.createTime AS DATE) date_label,COUNT(*) n,COALESCE(SUM(" + BYTES
                        + "),0) size FROM picture p WHERE " + filter + " GROUP BY CAST(p.createTime AS DATE) ORDER BY date_label",
                (rs, row) -> new Distribution(rs.getString("date_label"), rs.getLong("n"), rs.getLong("size")), args.toArray());
    }

    public List<Ranking> rankings(int limit) {
        return jdbc.query("SELECT id,spaceName,totalSize,totalCount FROM space WHERE isDelete=0 "
                        + "ORDER BY totalSize DESC,id ASC LIMIT ?", (rs, row) -> {
                    Ranking v = new Ranking();
                    v.setSpaceId(rs.getLong("id"));
                    v.setSpaceName(rs.getString("spaceName"));
                    v.setUsedSize(rs.getLong("totalSize"));
                    v.setUsedCount(rs.getLong("totalCount"));
                    return v;
                }, limit);
    }
}
