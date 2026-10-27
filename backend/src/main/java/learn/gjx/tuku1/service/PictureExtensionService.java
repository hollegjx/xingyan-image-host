package learn.gjx.tuku1.service;

import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper;
import learn.gjx.tuku1.exception.*;
import learn.gjx.tuku1.manager.space.PictureAccessManager;
import learn.gjx.tuku1.mapper.PictureMapper;
import learn.gjx.tuku1.mapper.SpaceMapper;
import learn.gjx.tuku1.model.dto.picture.*;
import learn.gjx.tuku1.model.entity.*;
import learn.gjx.tuku1.model.vo.PictureVO;
import learn.gjx.tuku1.utils.PictureColor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.servlet.http.HttpServletRequest;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class PictureExtensionService {
    private final PictureMapper pictures;
    private final SpaceMapper spaces;
    private final PictureAccessManager access;
    private final PictureService pictureService;

    public PictureExtensionService(PictureMapper pictures, SpaceMapper spaces,
                                   PictureAccessManager access, PictureService pictureService) {
        this.pictures = pictures; this.spaces = spaces;
        this.access = access; this.pictureService = pictureService;
    }

    public List<PictureVO> searchColor(SearchPictureByColorRequest body, User user, HttpServletRequest request) {
        require(body != null && body.getPicColor() != null
                && body.getPicColor().matches("#[0-9a-fA-F]{6}"), "颜色必须是 #RRGGBB 格式");
        access.requireSpacePermission(body.getSpaceId(), user, learn.gjx.tuku1.manager.space.SpacePermission.VIEW);
        final int target = PictureColor.rgb(PictureColor.normalize(body.getPicColor()));
        Comparator<Match> order = Comparator.comparingInt((Match m) -> m.distance).thenComparingLong(m -> m.id);
        PriorityQueue<Match> best = new PriorityQueue<>(12, order.reversed());
        long cursor = 0;
        // Keyset scan: bounded batches and a top-12 heap instead of retaining all space pictures.
        while (true) {
            List<Picture> rows = pictures.selectList(new QueryWrapper<Picture>().select("id", "picColor")
                    .eq("spaceId", body.getSpaceId()).eq("isDelete", 0).isNotNull("picColor")
                    .gt("id", cursor).orderByAsc("id").last("LIMIT 500"));
            if (rows.isEmpty()) break;
            for (Picture row : rows) {
                String color = PictureColor.normalize(row.getPicColor());
                if (color == null) continue; // Legacy/malformed metadata is not a match.
                Match match = new Match(row.getId(), PictureColor.distanceSquared(target, PictureColor.rgb(color)));
                if (best.size() < 12) best.add(match);
                else if (order.compare(match, best.peek()) < 0) { best.poll(); best.add(match); }
            }
            cursor = rows.get(rows.size() - 1).getId();
            if (rows.size() < 500) break;
        }
        List<Match> sorted = new ArrayList<>(best); sorted.sort(order);
        if (sorted.isEmpty()) return Collections.emptyList();
        List<Long> ids = sorted.stream().map(m -> m.id).collect(Collectors.toList());
        Map<Long, Picture> found = pictures.selectList(new QueryWrapper<Picture>()
                .eq("spaceId", body.getSpaceId()).eq("isDelete", 0).in("id", ids))
                .stream().collect(Collectors.toMap(Picture::getId, p -> p));
        List<Picture> ordered = ids.stream().map(found::get).filter(Objects::nonNull).collect(Collectors.toList());
        // Reuse authorized VO conversion: private COS addresses MUST NOT escape in color results.
        return pictureService.getPictureVOList(ordered, request);
    }

    @Transactional(rollbackFor = Exception.class)
    public int editBatch(PictureEditByBatchRequest body, User user) {
        require(body != null, "请求不能为空");
        List<Long> ids = body.getPictureIdList();
        require(ids != null && !ids.isEmpty() && ids.size() <= 100, "请选择 1 至 100 张图片");
        require(ids.stream().allMatch(id -> id != null && id > 0), "图片 ID 不合法");
        require(new HashSet<>(ids).size() == ids.size(), "图片 ID 不能重复");
        require(body.getCategory() != null || body.getTags() != null || body.getNameRule() != null,
                "请至少选择一项修改内容");
        String category = body.getCategory() == null ? null : body.getCategory().trim();
        require(category == null || category.length() <= 64, "分类不能超过 64 个字符");
        String tags = null;
        if (body.getTags() != null) {
            require(body.getTags().size() <= 20, "标签最多 20 个");
            LinkedHashSet<String> clean = new LinkedHashSet<>();
            for (String tag : body.getTags()) {
                require(tag != null && !tag.trim().isEmpty() && tag.trim().length() <= 32, "标签需为 1 至 32 个字符");
                clean.add(tag.trim());
            }
            tags = JSONUtil.toJsonStr(clean);
            require(tags.length() <= 512, "标签总长度过长");
        }
        List<String> names = new ArrayList<>();
        if (body.getNameRule() != null) {
            String rule = body.getNameRule().trim();
            require(!rule.isEmpty() && rule.length() <= 128, "命名规则需为 1 至 128 个字符");
            String literal = rule.replace("{序号}", "");
            require(!literal.contains("{") && !literal.contains("}") && !rule.matches("(?s).*\\p{Cntrl}.*"),
                    "仅支持 {序号} 占位符，不支持表达式");
            for (int i = 1; i <= ids.size(); i++) {
                String name = rule.replace("{序号}", String.valueOf(i));
                require(name.length() <= 128, "生成的图片名称超过 128 个字符");
                names.add(name);
            }
        }
        access.requireSpacePermission(body.getSpaceId(), user, learn.gjx.tuku1.manager.space.SpacePermission.EDIT);
        // Same lock order as private upload/edit/delete: space first, pictures in ascending ID order.
        Space space = spaces.selectForUpdate(body.getSpaceId());
        ThrowUtils.throwIf(space == null, ErrorCode.NOT_FOUND_ERROR, "空间不存在");
        access.requireSpacePermission(space, user, learn.gjx.tuku1.manager.space.SpacePermission.EDIT, true);
        List<Long> lockOrder = new ArrayList<>(ids); Collections.sort(lockOrder);
        for (Long id : lockOrder) {
            Picture picture = pictures.selectForUpdate(id);
            ThrowUtils.throwIf(picture == null || !Objects.equals(picture.getSpaceId(), space.getId()),
                    ErrorCode.NO_AUTH_ERROR, "所选图片已删除或不属于当前空间，请刷新后重试");
        }
        // All rows are validated before the first write. No worker threads: one transaction owns every update.
        Date now = new Date();
        for (int i = 0; i < ids.size(); i++) {
            UpdateWrapper<Picture> update = new UpdateWrapper<Picture>().eq("id", ids.get(i))
                    .eq("spaceId", space.getId()).eq("isDelete", 0).set("editTime", now).set("updateTime", now);
            if (category != null) update.set("category", category);
            if (tags != null) update.set("tags", tags);
            if (!names.isEmpty()) update.set("name", names.get(i));
            ThrowUtils.throwIf(pictures.update(null, update) != 1, ErrorCode.OPERATION_ERROR, "批量更新失败，已回滚");
        }
        // Metadata only: do not change ownership, quota, files, upload status, public review or public cache.
        return ids.size();
    }

    private static void require(boolean condition, String message) {
        ThrowUtils.throwIf(!condition, ErrorCode.PARAMS_ERROR, message);
    }
    private static final class Match {
        private final long id;
        private final int distance;
        private Match(long id, int distance) { this.id = id; this.distance = distance; }
    }
}
