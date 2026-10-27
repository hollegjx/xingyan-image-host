package learn.gjx.tuku1.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import learn.gjx.tuku1.exception.BusinessException;
import learn.gjx.tuku1.exception.ErrorCode;
import learn.gjx.tuku1.exception.ThrowUtils;
import learn.gjx.tuku1.mapper.SpaceMapper;
import learn.gjx.tuku1.mapper.SpaceUserMapper;
import learn.gjx.tuku1.model.entity.SpaceUser;
import learn.gjx.tuku1.model.enums.SpaceTypeEnum;
import learn.gjx.tuku1.manager.space.PictureAccessManager;
import learn.gjx.tuku1.manager.space.SpacePermission;
import learn.gjx.tuku1.manager.space.UserSpaceCreationLock;
import learn.gjx.tuku1.model.dto.space.*;
import learn.gjx.tuku1.model.entity.Space;
import learn.gjx.tuku1.model.entity.User;
import learn.gjx.tuku1.model.enums.SpaceLevelEnum;
import learn.gjx.tuku1.model.enums.UserRoleEnum;
import learn.gjx.tuku1.model.vo.SpaceVO;
import learn.gjx.tuku1.service.SpaceService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import javax.annotation.Resource;
import java.util.Arrays;
import java.util.Date;
import java.util.HashSet;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class SpaceServiceImpl extends ServiceImpl<SpaceMapper, Space> implements SpaceService {
    @Resource
    private TransactionTemplate transactionTemplate;
    @Resource
    private UserSpaceCreationLock creationLock;
    @Resource private SpaceUserMapper members;
    @Resource private PictureAccessManager access;

    private static final Set<String> SORT_FIELDS = new HashSet<>(Arrays.asList(
            "id", "spaceName", "spaceLevel", "createTime", "editTime", "updateTime",
            "maxSize", "maxCount", "totalSize", "totalCount"));

    @Override
    public long addSpace(SpaceAddRequest request, User loginUser) {
        requireLogin(loginUser);
        require(request != null, "请求不能为空");
        int type = request.getSpaceType() == null ? 0 : request.getSpaceType();
        require(SpaceTypeEnum.valid(type), "空间类型只能是 0 或 1");
        String name = text(request.getSpaceName());
        String normalizedName = validName(name == null ? "默认空间" : name);
        SpaceLevelEnum level = request.getSpaceLevel() == null ? SpaceLevelEnum.COMMON
                : SpaceLevelEnum.getEnumByValue(request.getSpaceLevel());
        require(level != null, "空间级别不存在");
        ThrowUtils.throwIf(level != SpaceLevelEnum.COMMON
                        && !Objects.equals(loginUser.getUserRole(), UserRoleEnum.ADMIN.getValue()),
                ErrorCode.NO_AUTH_ERROR, "普通用户只能创建普通版空间");
        // 默认 REQUIRED 若加入外层事务，execute 返回时可能还未提交，不能在此释放本地锁。
        // 当前创建是独立入口；拒绝这种调用，而不是 REQUIRES_NEW 后悄悄改变外层回滚语义。
        ThrowUtils.throwIf(TransactionSynchronizationManager.isActualTransactionActive(),
                ErrorCode.OPERATION_ERROR, "创建空间需在外层事务之外调用");

        Long userId = loginUser.getId();
        return creationLock.execute(userId, () -> {
            Long id = transactionTemplate.execute(status -> {
                boolean exists = lambdaQuery().eq(Space::getUserId, userId).eq(Space::getSpaceType, type).exists();
                boolean admin = Objects.equals(loginUser.getUserRole(), 1);
                ThrowUtils.throwIf(exists && !admin, ErrorCode.OPERATION_ERROR, "每个普通用户每类空间仅能有一个");
                Space space = new Space();
                space.setSpaceType(type);
                space.setSpaceName(normalizedName);
                space.setSpaceLevel(level.getValue());
                space.setMaxSize(level.getMaxSize());
                space.setMaxCount(level.getMaxCount());
                space.setTotalSize(0L);
                space.setTotalCount(0L);
                space.setUserId(userId);
                space.setIsDelete(0);
                ThrowUtils.throwIf(!save(space), ErrorCode.OPERATION_ERROR, "创建空间失败");
                if (type == 1) {
                    SpaceUser owner = new SpaceUser(); owner.setSpaceId(space.getId());
                    owner.setUserId(userId); owner.setSpaceRole("admin");
                    ThrowUtils.throwIf(members.insert(owner) != 1, ErrorCode.OPERATION_ERROR, "初始化团队成员失败");
                }
                return space.getId();
            });
            // execute 完成提交（或异常回滚）之后，才离开按用户加锁的范围。
            ThrowUtils.throwIf(id == null, ErrorCode.OPERATION_ERROR, "创建空间失败");
            return id;
        });
    }

    @Override
    public Space getSpaceForAdmin(Long id, User loginUser) {
        requireAdmin(loginUser);
        return findSpace(id, false);
    }

    @Override
    public SpaceVO getSpaceVO(Long id, User loginUser) {
        requireLogin(loginUser);
        Space space = findSpace(id, false);
        if (Objects.equals(space.getSpaceType(), 1))
            access.requireSpacePermission(space, loginUser, SpacePermission.VIEW, false);
        else requireOwnerOrAdmin(space, loginUser);
        return view(space, loginUser);
    }

    @Override
    public Page<Space> listSpaces(SpaceQueryRequest query, User loginUser) {
        requireAdmin(loginUser);
        return queryPage(query, null);
    }

    @Override
    public Page<SpaceVO> listMySpaces(SpaceQueryRequest query, User loginUser) {
        requireLogin(loginUser);
        // 不相信客户端 userId，即使管理员访问“我的空间”也只返回自己。
        Page<Space> page = queryPage(query, loginUser.getId());
        Page<SpaceVO> result = new Page<>(page.getCurrent(), page.getSize(), page.getTotal());
        result.setRecords(page.getRecords().stream().map(s -> view(s, loginUser)).collect(Collectors.toList()));
        return result;
    }

    private SpaceVO view(Space space, User user) {
        SpaceVO vo = SpaceVO.objToVo(space);
        vo.setPermissionList(access.permissions(space, user, false));
        vo.setCanManageSpace(Objects.equals(space.getUserId(), user.getId()));
        return vo;
    }

    private Page<Space> queryPage(SpaceQueryRequest input, Long forcedUserId) {
        SpaceQueryRequest query = input == null ? new SpaceQueryRequest() : input;
        Integer type = query.getSpaceType();
        if (type == null && forcedUserId != null) type = 0;
        require(type == null || SpaceTypeEnum.valid(type), "空间类型只能是 0 或 1");
        int current = query.getCurrent() == null ? 1 : query.getCurrent();
        int pageSize = query.getPageSize() == null ? 10 : query.getPageSize();
        require(current > 0 && pageSize > 0 && pageSize <= 50, "页码必须为正，每页数量为 1~50");
        if (query.getId() != null) {
            requireId(query.getId());
        }
        Long userId = forcedUserId == null ? query.getUserId() : forcedUserId;
        if (userId != null) {
            requireId(userId);
        }
        if (query.getSpaceLevel() != null) {
            require(SpaceLevelEnum.getEnumByValue(query.getSpaceLevel()) != null, "空间级别不存在");
        }
        String name = text(query.getSpaceName());
        require(name == null || name.length() <= 30, "空间名称不能超过 30 字符");
        String sortField = text(query.getSortField());
        String sortOrder = text(query.getSortOrder());
        sortField = sortField == null ? "createTime" : sortField;
        sortOrder = sortOrder == null ? "desc" : sortOrder.toLowerCase(Locale.ROOT);
        require(SORT_FIELDS.contains(sortField), "不支持的排序字段");
        require("asc".equals(sortOrder) || "desc".equals(sortOrder), "排序方向只能为 asc 或 desc");
        QueryWrapper<Space> wrapper = new QueryWrapper<Space>()
                .eq(query.getId() != null, "id", query.getId())
                .eq(userId != null, "userId", userId)
                .eq(query.getSpaceLevel() != null, "spaceLevel", query.getSpaceLevel())
                .eq(type != null, "spaceType", type)
                .like(name != null, "spaceName", name)
                .orderBy(true, "asc".equals(sortOrder), sortField);
        if (!"id".equals(sortField)) {
            wrapper.orderByDesc("id");
        }
        return page(new Page<>(current, pageSize), wrapper);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean editSpace(SpaceEditRequest request, User loginUser) {
        requireLogin(loginUser);
        require(request != null, "请求不能为空");
        String name = validName(request.getSpaceName());
        Space space = findSpace(request.getId(), true);
        ThrowUtils.throwIf(!Objects.equals(space.getUserId(), loginUser.getId()),
                ErrorCode.NO_AUTH_ERROR, "只能编辑自己的空间");
        Space change = new Space();
        change.setId(space.getId());
        change.setSpaceName(name);
        change.setEditTime(new Date());
        ThrowUtils.throwIf(!updateById(change), ErrorCode.OPERATION_ERROR, "修改失败");
        return true;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean updateSpace(SpaceUpdateRequest request, User loginUser) {
        requireAdmin(loginUser);
        require(request != null, "请求不能为空");
        require(request.getSpaceName() != null || request.getSpaceLevel() != null
                || request.getMaxSize() != null || request.getMaxCount() != null, "没有可更新的字段");
        Space space = findSpace(request.getId(), true);
        Space change = new Space();
        change.setId(space.getId());
        if (request.getSpaceName() != null) {
            change.setSpaceName(validName(request.getSpaceName()));
        }
        SpaceLevelEnum level = request.getSpaceLevel() == null ? null
                : SpaceLevelEnum.getEnumByValue(request.getSpaceLevel());
        require(request.getSpaceLevel() == null || level != null, "空间级别不存在");
        boolean levelChanged = level != null && !Objects.equals(space.getSpaceLevel(), level.getValue());
        long maxSize = request.getMaxSize() != null ? request.getMaxSize()
                : levelChanged ? level.getMaxSize() : space.getMaxSize();
        long maxCount = request.getMaxCount() != null ? request.getMaxCount()
                : levelChanged ? level.getMaxCount() : space.getMaxCount();
        require(maxSize > 0 && maxCount > 0, "空间额度必须大于 0");
        require(maxSize >= space.getTotalSize() && maxCount >= space.getTotalCount(),
                "空间额度不能小于已使用量");
        if (level != null) {
            change.setSpaceLevel(level.getValue());
        }
        change.setMaxSize(maxSize);
        change.setMaxCount(maxCount);
        change.setEditTime(new Date());
        ThrowUtils.throwIf(!updateById(change), ErrorCode.OPERATION_ERROR, "更新失败");
        return true;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean deleteEmptySpace(Long id, User loginUser) {
        requireLogin(loginUser);
        Space space = findSpace(id, true);
        requireOwnerOrAdmin(space, loginUser);
        // 不用 totalCount 单独判断，避免历史账目不准时丢失图片归属。
        ThrowUtils.throwIf(space.getTotalCount() != 0 || space.getTotalSize() != 0
                        || baseMapper.countPictureReferences(id) != 0,
                ErrorCode.OPERATION_ERROR, "当前阶段仅允许删除空空间");
        ThrowUtils.throwIf(!removeById(id), ErrorCode.OPERATION_ERROR, "删除失败");
        members.delete(new QueryWrapper<SpaceUser>().eq("spaceId", id));
        return true;
    }

    private Space findSpace(Long id, boolean lock) {
        requireId(id);
        Space space = lock ? baseMapper.selectForUpdate(id) : getById(id);
        ThrowUtils.throwIf(space == null, ErrorCode.NOT_FOUND_ERROR, "空间不存在");
        return space;
    }

    private static void requireLogin(User user) {
        PictureAccessManager.requireActiveUser(user);
        ThrowUtils.throwIf(user == null || user.getId() == null || user.getId() <= 0,
                ErrorCode.NOT_LOGIN_ERROR);
        ThrowUtils.throwIf(!Objects.equals(user.getUserRole(), UserRoleEnum.USER.getValue())
                        && !Objects.equals(user.getUserRole(), UserRoleEnum.ADMIN.getValue()),
                ErrorCode.NO_AUTH_ERROR);
    }

    private static void requireAdmin(User user) {
        requireLogin(user);
        ThrowUtils.throwIf(!Objects.equals(user.getUserRole(), UserRoleEnum.ADMIN.getValue()),
                ErrorCode.NO_AUTH_ERROR, "需要管理员权限");
    }

    private static void requireOwnerOrAdmin(Space space, User user) {
        ThrowUtils.throwIf(!Objects.equals(space.getUserId(), user.getId())
                        && !Objects.equals(user.getUserRole(), UserRoleEnum.ADMIN.getValue()),
                ErrorCode.NO_AUTH_ERROR, "没有空间权限");
    }

    private static String validName(String name) {
        String normalized = text(name);
        require(normalized != null && normalized.length() <= 30, "空间名称必须为 1~30 字符");
        return normalized;
    }

    private static String text(String value) {
        return value == null || value.trim().isEmpty() ? null : value.trim();
    }

    private static void requireId(Long id) {
        require(id != null && id > 0, "ID 必须为正");
    }

    private static void require(boolean condition, String message) {
        if (!condition) {
            throw new BusinessException(ErrorCode.PARAMS_ERROR, message);
        }
    }
}
