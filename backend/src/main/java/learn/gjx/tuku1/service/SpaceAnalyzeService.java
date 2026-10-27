package learn.gjx.tuku1.service;

import learn.gjx.tuku1.exception.BusinessException;
import learn.gjx.tuku1.exception.ErrorCode;
import learn.gjx.tuku1.manager.space.PictureAccessManager;
import learn.gjx.tuku1.manager.space.SpaceAnalysisRepository;
import learn.gjx.tuku1.mapper.SpaceMapper;
import learn.gjx.tuku1.model.dto.space.analyze.SpaceAnalyzeRequest;
import learn.gjx.tuku1.model.entity.Space;
import learn.gjx.tuku1.model.entity.User;
import learn.gjx.tuku1.model.vo.SpaceAnalysisVO;
import learn.gjx.tuku1.model.vo.SpaceAnalysisVO.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalAdjusters;
import java.util.*;

@Service
@Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ, timeout = 10)
public class SpaceAnalyzeService {
    @Resource private PictureAccessManager access;
    @Resource private SpaceMapper spaces;
    @Resource private SpaceAnalysisRepository repository;
    private static final ZoneId ZONE = ZoneId.of("Asia/Shanghai");

    /** 每个公开入口均先鉴权，再查询；管理员也不能以 SPACE 读取别人的私有分类和标签。 */
    private Space authorize(SpaceAnalyzeRequest r, User user) {
        if (user == null || user.getId() == null || user.getId() <= 0)
            throw new BusinessException(ErrorCode.NOT_LOGIN_ERROR);
        if (Objects.equals(user.getIsDelete(), 1) || !Arrays.asList(0, 1).contains(user.getUserRole()))
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR);
        if (r == null || !Arrays.asList("SPACE", "PUBLIC", "ALL").contains(r.getScope()))
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "请选择有效的统计范围");
        if ("SPACE".equals(r.getScope())) {
            access.requireSpacePermission(r.getSpaceId(), user, learn.gjx.tuku1.manager.space.SpacePermission.ANALYZE);
            return spaces.selectById(r.getSpaceId());
        }
        if (r.getSpaceId() != null)
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "公共或全局统计不能同时指定空间");
        if (!Objects.equals(user.getUserRole(), 1))
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "仅管理员可查看公共或全局统计");
        return null;
    }

    private void requireContentScope(SpaceAnalyzeRequest r) {
        if ("ALL".equals(r.getScope()))
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "全局视图不提供私有分类或标签");
    }

    public Usage usage(SpaceAnalyzeRequest r, User user) {
        Space space = authorize(r, user);
        return usageAuthorized(r, space);
    }

    private Usage usageAuthorized(SpaceAnalyzeRequest r, Space space) {
        Usage usage = repository.usage(r.getScope(), r.getSpaceId());
        if (space != null) {
            // 配额账本包含占用资源的草稿，与上传时的额度检查一致。
            usage.setUsedSize(space.getTotalSize());
            usage.setUsedCount(space.getTotalCount());
            usage.setMaxSize(space.getMaxSize());
            usage.setMaxCount(space.getMaxCount());
            usage.setSizeUsageRatio(ratio(usage.getUsedSize(), usage.getMaxSize()));
            usage.setCountUsageRatio(ratio(usage.getUsedCount(), usage.getMaxCount()));
        }
        return usage;
    }

    static Double ratio(Long value, Long limit) {
        if (value == null || limit == null || limit <= 0) return null;
        return Math.round(value.doubleValue() / limit * 10000.0) / 100.0;
    }

    public List<Distribution> categories(SpaceAnalyzeRequest r, User user) {
        authorize(r, user);
        requireContentScope(r);
        return repository.categories(r.getScope(), r.getSpaceId());
    }

    public List<Distribution> tags(SpaceAnalyzeRequest r, User user) {
        authorize(r, user);
        requireContentScope(r);
        return repository.tags(r.getScope(), r.getSpaceId());
    }

    public List<Distribution> sizes(SpaceAnalyzeRequest r, User user) {
        authorize(r, user);
        return repository.sizes(r.getScope(), r.getSpaceId());
    }

    public Trend trend(SpaceAnalyzeRequest r, User user) {
        authorize(r, user);
        return trendAuthorized(r);
    }

    private Trend trendAuthorized(SpaceAnalyzeRequest r) {
        if (!Arrays.asList("day", "week", "month").contains(r.getTimeDimension()))
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "时间维度只能是 day、week、month");
        if (r.getUserId() != null && (r.getUserId() <= 0 || "ALL".equals(r.getScope())))
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "用户筛选不合法或不适用于全局统计");
        LocalDate end = LocalDate.now(ZONE);
        LocalDate start;
        try {
            if ((r.getStartDate() == null) != (r.getEndDate() == null))
                throw new IllegalArgumentException();
            if (r.getStartDate() != null) {
                start = LocalDate.parse(r.getStartDate());
                end = LocalDate.parse(r.getEndDate());
            } else if ("week".equals(r.getTimeDimension())) {
                start = end.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).minusWeeks(11);
            } else if ("month".equals(r.getTimeDimension())) {
                start = end.withDayOfMonth(1).minusMonths(11);
            } else {
                start = end.minusDays(29);
            }
            if (start.isAfter(end) || ChronoUnit.DAYS.between(start, end) > 365
                    || start.isBefore(LocalDate.of(2000, 1, 1)) || end.isAfter(LocalDate.now(ZONE)))
                throw new IllegalArgumentException();
        } catch (RuntimeException ex) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "日期须为 yyyy-MM-dd，不超过今天且范围为 1～366 天");
        }
        Map<String, Distribution> points = new LinkedHashMap<>();
        for (LocalDate day = start; !day.isAfter(end); day = day.plusDays(1)) {
            String label = label(day, r.getTimeDimension());
            points.putIfAbsent(label, new Distribution(label, 0, 0));
        }
        for (Distribution day : repository.daily(r.getScope(), r.getSpaceId(), r.getUserId(), start, end)) {
            Distribution point = points.get(label(LocalDate.parse(day.getName()), r.getTimeDimension()));
            if (point != null) {
                point.setCount(point.getCount() + day.getCount());
                point.setTotalSize(point.getTotalSize() + day.getTotalSize());
            }
        }
        Trend result = new Trend();
        result.setTimeDimension(r.getTimeDimension());
        result.setStartDate(start.toString());
        result.setEndDate(end.toString());
        result.setTimezone(ZONE.toString());
        result.setPoints(new ArrayList<>(points.values()));
        return result;
    }

    private String label(LocalDate day, String dimension) {
        if ("week".equals(dimension))
            return day.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).toString();
        return "month".equals(dimension) ? day.toString().substring(0, 7) : day.toString();
    }

    public List<Ranking> rankings(SpaceAnalyzeRequest r, User user) {
        authorize(r, user);
        if (!"ALL".equals(r.getScope()))
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "空间排行仅用于管理员全局视图");
        return repository.rankings(validLimit(r));
    }

    private int validLimit(SpaceAnalyzeRequest r) {
        if (r.getTopN() == null || r.getTopN() < 1 || r.getTopN() > 20)
            throw new BusinessException(ErrorCode.PARAMS_ERROR, "排行数量须为 1～20");
        return r.getTopN();
    }

    /** 同一只读事务快照，避免六个卡片读取跨越不同次写入；不进入公共图片缓存。 */
    public SpaceAnalysisVO dashboard(SpaceAnalyzeRequest r, User user) {
        Space space = authorize(r, user);
        validLimit(r);
        SpaceAnalysisVO result = new SpaceAnalysisVO();
        result.setScope(r.getScope());
        result.setSpaceId(space == null ? null : space.getId());
        result.setSpaceName(space != null ? space.getSpaceName() : ("ALL".equals(r.getScope()) ? "全站图库" : "公共图库"));
        result.setGeneratedAt(ZonedDateTime.now(ZONE).toOffsetDateTime().toString());
        result.setTrend(trendAuthorized(r)); // 先验证时间参数，避免非法输入触发其余聚合。
        result.setUsage(usageAuthorized(r, space));
        result.setSizes(repository.sizes(r.getScope(), r.getSpaceId()));
        boolean all = "ALL".equals(r.getScope());
        result.setCategories(all ? Collections.emptyList() : repository.categories(r.getScope(), r.getSpaceId()));
        result.setTags(all ? Collections.emptyList() : repository.tags(r.getScope(), r.getSpaceId()));
        result.setRankings(all ? repository.rankings(r.getTopN()) : Collections.emptyList());
        return result;
    }
}
