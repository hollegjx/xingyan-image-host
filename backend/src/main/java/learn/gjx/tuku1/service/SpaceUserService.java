package learn.gjx.tuku1.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import learn.gjx.tuku1.exception.*;
import learn.gjx.tuku1.manager.space.PictureAccessManager;
import learn.gjx.tuku1.mapper.*;
import learn.gjx.tuku1.model.dto.space.*;
import learn.gjx.tuku1.model.entity.*;
import learn.gjx.tuku1.model.enums.SpaceRoleEnum;
import learn.gjx.tuku1.model.vo.*;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import javax.annotation.Resource;
import java.util.*;
import java.util.stream.Collectors;
import static learn.gjx.tuku1.manager.space.SpacePermission.*;

@Service
public class SpaceUserService {
    @Resource private SpaceMapper spaces;
    @Resource private SpaceUserMapper members;
    @Resource(name = "userMapper") private UserMapper users;
    @Resource private PictureAccessManager access;

    /** Space row serializes membership changes with all picture writes/quota changes. */
    private Space managed(Long id, User actor, boolean lock) {
        PictureAccessManager.requireActiveUser(actor);
        require(id != null && id > 0, "空间 ID 不合法");
        Space space = lock ? spaces.selectForUpdate(id) : spaces.selectById(id);
        if (space == null) throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "空间不存在");
        require(Objects.equals(space.getSpaceType(), 1), "只有团队空间可以管理成员");
        access.requireSpacePermission(space, actor, MEMBERS, lock);
        return space;
    }

    @Transactional(rollbackFor = Exception.class)
    public long add(SpaceUserAddRequest r, User actor) {
        require(r != null, "请求不能为空");
        Space space = managed(r.getSpaceId(), actor, true);
        require(r.getUserId() != null && r.getUserId() > 0, "用户 ID 不合法");
        SpaceRoleEnum role = SpaceRoleEnum.from(r.getSpaceRole());
        require(role != null, "角色只能为 viewer/editor/admin");
        User target = users.selectById(r.getUserId());
        if (target == null || Objects.equals(target.getIsDelete(), 1)
                || (!Objects.equals(target.getUserRole(), 0) && !Objects.equals(target.getUserRole(), 1)))
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "用户不存在或不可用");
        require(members.lockMember(space.getId(), r.getUserId()) == null, "用户已经在此团队中");
        require(members.selectCount(new QueryWrapper<SpaceUser>().eq("spaceId", space.getId())) < 200,
                "每个团队最多 200 名成员");
        SpaceUser member = new SpaceUser();
        member.setSpaceId(space.getId()); member.setUserId(r.getUserId()); member.setSpaceRole(role.getValue());
        if (members.insert(member) != 1) throw new BusinessException(ErrorCode.OPERATION_ERROR, "添加失败");
        return member.getId();
    }

    @Transactional(rollbackFor = Exception.class)
    public boolean edit(SpaceUserEditRequest r, User actor) {
        require(r != null, "请求不能为空");
        Space space = managed(r.getSpaceId(), actor, true);
        SpaceRoleEnum role = SpaceRoleEnum.from(r.getSpaceRole());
        require(role != null, "角色只能为 viewer/editor/admin");
        SpaceUser member = target(r.getId(), space);
        protectOwner(member, space);
        if (Objects.equals(role.getValue(), member.getSpaceRole())) return true;
        SpaceUser update = new SpaceUser(); update.setId(member.getId()); update.setSpaceRole(role.getValue());
        update.setUpdateTime(new Date());
        if (members.updateById(update) != 1) throw new BusinessException(ErrorCode.OPERATION_ERROR, "修改失败");
        return true;
    }

    @Transactional(rollbackFor = Exception.class)
    public boolean remove(SpaceUserEditRequest r, User actor) {
        require(r != null, "请求不能为空");
        Space space = managed(r.getSpaceId(), actor, true);
        SpaceUser member = target(r.getId(), space);
        protectOwner(member, space);
        if (members.deleteById(member.getId()) != 1) throw new BusinessException(ErrorCode.OPERATION_ERROR, "移除失败");
        return true;
    }

    private SpaceUser target(Long id, Space space) {
        require(id != null && id > 0, "成员记录 ID 不合法");
        SpaceUser member = members.selectById(id);
        if (member == null || !Objects.equals(member.getSpaceId(), space.getId()))
            throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "成员不属于当前空间");
        return member;
    }

    private void protectOwner(SpaceUser member, Space space) {
        if (Objects.equals(member.getUserId(), space.getUserId()))
            throw new BusinessException(ErrorCode.NO_AUTH_ERROR, "不能移除或降级空间创建者");
    }

    @Transactional(readOnly = true)
    public List<SpaceUserVO> list(SpaceUserQueryRequest r, User actor) {
        require(r != null, "请求不能为空");
        Space space = managed(r.getSpaceId(), actor, false);
        return wrap(members.selectList(new QueryWrapper<SpaceUser>().eq("spaceId", space.getId())
                .orderByAsc("createTime", "id").last("LIMIT 200")), actor);
    }

    public SpaceUserVO get(SpaceUserQueryRequest r, User actor) {
        require(r != null && r.getUserId() != null && r.getUserId() > 0, "参数不合法");
        managed(r.getSpaceId(), actor, false);
        SpaceUser member = members.findMember(r.getSpaceId(), r.getUserId());
        if (member == null) throw new BusinessException(ErrorCode.NOT_FOUND_ERROR, "成员不存在");
        return wrap(Collections.singletonList(member), actor).get(0);
    }

    public Page<SpaceUserVO> myTeams(SpaceUserQueryRequest r, User actor) {
        PictureAccessManager.requireActiveUser(actor);
        require(r != null && r.getCurrent() != null && r.getCurrent() > 0
                && r.getPageSize() != null && r.getPageSize() > 0 && r.getPageSize() <= 50, "分页参数不合法");
        // Only the authenticated user; client userId/spaceId cannot broaden this scope.
        QueryWrapper<SpaceUser> query = new QueryWrapper<SpaceUser>().eq("userId", actor.getId())
                .inSql("spaceId", "SELECT id FROM space WHERE isDelete=0 AND spaceType=1")
                .orderByDesc("createTime", "id");
        Page<SpaceUser> page = members.selectPage(new Page<>(r.getCurrent(), r.getPageSize()), query);
        Page<SpaceUserVO> result = new Page<>(page.getCurrent(), page.getSize(), page.getTotal());
        result.setRecords(wrap(page.getRecords(), actor));
        return result;
    }

    private List<SpaceUserVO> wrap(List<SpaceUser> list, User actor) {
        if (list.isEmpty()) return Collections.emptyList();
        Map<Long, User> userMap = users.selectBatchIds(list.stream().map(SpaceUser::getUserId)
                .collect(Collectors.toSet())).stream().collect(Collectors.toMap(User::getId, u -> u));
        Map<Long, Space> spaceMap = spaces.selectBatchIds(list.stream().map(SpaceUser::getSpaceId)
                .collect(Collectors.toSet())).stream().collect(Collectors.toMap(Space::getId, s -> s));
        Map<Long, List<String>> permissions = new HashMap<>();
        spaceMap.forEach((id, space) -> permissions.put(id, access.permissions(space, actor, false)));
        return list.stream().filter(m -> spaceMap.containsKey(m.getSpaceId())).map(m -> {
            SpaceUserVO vo = new SpaceUserVO(); BeanUtils.copyProperties(m, vo);
            User user = userMap.get(m.getUserId());
            vo.setUserName(user == null ? "已注销用户" : user.getUserName());
            vo.setUserAvatar(user == null ? null : user.getUserAvatar());
            Space space = spaceMap.get(m.getSpaceId());
            vo.setOwner(Objects.equals(space.getUserId(), m.getUserId()));
            SpaceVO detail = SpaceVO.objToVo(space);
            detail.setPermissionList(permissions.get(space.getId()));
            detail.setCanManageSpace(Objects.equals(space.getUserId(), actor.getId()));
            vo.setSpace(detail);
            return vo;
        }).collect(Collectors.toList());
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new BusinessException(ErrorCode.PARAMS_ERROR, message);
    }
}
