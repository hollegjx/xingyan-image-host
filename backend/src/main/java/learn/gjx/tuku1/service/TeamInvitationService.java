package learn.gjx.tuku1.service;

import cn.hutool.crypto.digest.DigestUtil;
import learn.gjx.tuku1.exception.*;
import learn.gjx.tuku1.manager.space.PictureAccessManager;
import learn.gjx.tuku1.mapper.*;
import learn.gjx.tuku1.model.dto.space.*;
import learn.gjx.tuku1.model.entity.*;
import learn.gjx.tuku1.model.vo.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import javax.annotation.Resource;
import java.security.SecureRandom;
import java.sql.*;
import java.util.*;
import static learn.gjx.tuku1.manager.space.SpacePermission.MEMBERS;

@Service
@Slf4j
public class TeamInvitationService {
    @Resource private JdbcTemplate jdbc;
    @Resource private SpaceMapper spaces;
    @Resource private SpaceUserMapper members;
    @Resource(name="userMapper") private UserMapper users;
    @Resource private PictureAccessManager access;
    @Resource private SpaceUserService membership;
    private final SecureRandom random = new SecureRandom();
    private static final RowMapper<TeamInvitationVO> VIEW = (rs, i) -> {
        TeamInvitationVO v = new TeamInvitationVO(); v.setId(rs.getLong("id")); v.setSpaceId(rs.getLong("spaceId"));
        v.setSpaceRole(rs.getString("spaceRole")); v.setExpiresAt(rs.getTimestamp("expiresAt"));
        v.setMaxUses((Integer) rs.getObject("maxUses")); v.setUsedCount(rs.getInt("usedCount")); return v;
    };
    private Space space(Long id, boolean lock) {
        if (id == null || id <= 0) throw failure(ErrorCode.PARAMS_ERROR, "空间 ID 不合法");
        Space space = lock ? spaces.selectForUpdate(id) : spaces.selectById(id);
        if (space == null || !Objects.equals(space.getIsDelete(), 0) || !Objects.equals(space.getSpaceType(), 1))
            throw failure(ErrorCode.NOT_FOUND_ERROR, "团队空间不存在");
        return space;
    }
    @Transactional
    public TeamInvitationCreatedVO create(TeamInviteCreateRequest r, User actor) {
        PictureAccessManager.requireActiveUser(actor);
        if (r == null || !(Objects.equals(r.getValidDays(), 1) || Objects.equals(r.getValidDays(), 7))
                || !("viewer".equals(r.getSpaceRole()) || "editor".equals(r.getSpaceRole()))
                || (r.getMaxUses() != null && r.getMaxUses() < 1)) throw failure(ErrorCode.PARAMS_ERROR, "邀请角色、有效期或次数不合法");
        Space team = space(r.getSpaceId(), true); access.requireSpacePermission(team, actor, MEMBERS, true);
        byte[] bytes = new byte[32]; random.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        Timestamp expires = new Timestamp(System.currentTimeMillis() + r.getValidDays() * 86400000L);
        GeneratedKeyHolder key = new GeneratedKeyHolder();
        jdbc.update(c -> { PreparedStatement p = c.prepareStatement("INSERT INTO team_invitation(spaceId,creatorId,tokenHash,spaceRole,expiresAt,maxUses) VALUES(?,?,?,?,?,?)", new String[]{"id"});
            p.setLong(1, team.getId()); p.setLong(2, actor.getId()); p.setString(3, DigestUtil.sha256Hex(token));
            p.setString(4, r.getSpaceRole()); p.setTimestamp(5, expires); p.setObject(6, r.getMaxUses()); return p; }, key);
        return new TeamInvitationCreatedVO(key.getKey().longValue(), token);
    }
    public List<TeamInvitationVO> list(Long spaceId, User actor) {
        Space team = space(spaceId, false); access.requireSpacePermission(team, actor, MEMBERS, false);
        return jdbc.query("SELECT id,spaceId,spaceRole,expiresAt,maxUses,usedCount FROM team_invitation WHERE spaceId=? AND revoked=0 AND expiresAt>CURRENT_TIMESTAMP AND (maxUses IS NULL OR usedCount<maxUses) ORDER BY id DESC", VIEW, spaceId);
    }
    @Transactional
    public boolean revoke(TeamInviteManageRequest r, User actor) {
        Space team = space(r.getSpaceId(), true); access.requireSpacePermission(team, actor, MEMBERS, true);
        if (jdbc.update("UPDATE team_invitation SET revoked=1 WHERE id=? AND spaceId=?", r.getId(), team.getId()) != 1)
            throw failure(ErrorCode.NOT_FOUND_ERROR, "邀请不存在");
        return true;
    }
    public TeamInvitationVO preview(String token, User actor) {
        PictureAccessManager.requireActiveUser(actor);
        Map<String,Object> invite = find(token, false);
        Space team = space(number(invite, "spaceId"), false);
        validate(invite, team, actor, false);
        TeamInvitationVO view = jdbc.queryForObject("SELECT id,spaceId,spaceRole,expiresAt,maxUses,usedCount FROM team_invitation WHERE id=?", VIEW, number(invite,"id"));
        view.setSpaceName(team.getSpaceName()); return view;
    }
    @Transactional(isolation = Isolation.READ_COMMITTED, rollbackFor = Exception.class)
    public long join(String token, User actor) {
        PictureAccessManager.requireActiveUser(actor);
        Map<String,Object> located = find(token, false);
        // 与直接添加成员保持一致的空间→邀请→成员锁顺序；READ_COMMITTED 避免定位阶段的旧快照绕过成员上限。
        Space team = space(number(located,"spaceId"), true);
        Map<String,Object> invite = find(token, true);
        User issuer = validate(invite, team, actor, true);
        SpaceUserAddRequest add = new SpaceUserAddRequest(); add.setSpaceId(team.getId()); add.setUserId(actor.getId());
        add.setSpaceRole((String) invite.get("spaceRole")); membership.add(add, issuer);
        jdbc.update("UPDATE team_invitation SET usedCount=usedCount+1 WHERE id=?", number(invite,"id"));
        return team.getId();
    }
    private User validate(Map<String,Object> invite, Space team, User actor, boolean lock) {
        if (((Number) invite.get("revoked")).intValue() != 0) throw failure(ErrorCode.OPERATION_ERROR, "邀请已作废");
        Timestamp expires = (Timestamp) invite.get("expiresAt");
        if (!expires.toInstant().isAfter(java.time.Instant.now())) throw failure(ErrorCode.OPERATION_ERROR, "邀请已过期");
        Number maximum = (Number) invite.get("maxUses");
        if (maximum != null && ((Number) invite.get("usedCount")).longValue() >= maximum.longValue())
            throw failure(ErrorCode.OPERATION_ERROR, "邀请次数已用完");
        if ((lock ? members.lockMember(team.getId(), actor.getId()) : members.findMember(team.getId(), actor.getId())) != null)
            throw failure(ErrorCode.OPERATION_ERROR, "你已经是团队成员");
        User issuer = users.selectById(number(invite,"creatorId"));
        if (issuer == null || !access.permissions(team, issuer, lock).contains(MEMBERS))
            throw failure(ErrorCode.NO_AUTH_ERROR, "邀请创建者已无管理权限，请联系团队管理员");
        return issuer;
    }
    private Map<String,Object> find(String token, boolean lock) {
        if (token == null || !token.matches("[A-Za-z0-9_-]{43}")) throw failure(ErrorCode.PARAMS_ERROR, "邀请链接无效");
        List<Map<String,Object>> rows = jdbc.query("SELECT id,spaceId,creatorId,spaceRole,expiresAt,maxUses,usedCount,revoked FROM team_invitation WHERE tokenHash=?" + (lock ? " FOR UPDATE" : ""),
                (rs, i) -> {
                    Map<String,Object> row = new HashMap<>();
                    row.put("id", rs.getLong("id")); row.put("spaceId", rs.getLong("spaceId")); row.put("creatorId", rs.getLong("creatorId"));
                    row.put("spaceRole", rs.getString("spaceRole")); row.put("expiresAt", rs.getTimestamp("expiresAt"));
                    row.put("maxUses", rs.getObject("maxUses")); row.put("usedCount", rs.getInt("usedCount")); row.put("revoked", rs.getInt("revoked")); return row;
                }, DigestUtil.sha256Hex(token));
        if (rows.isEmpty()) throw failure(ErrorCode.NOT_FOUND_ERROR, "邀请链接无效");
        return rows.get(0);
    }
    private long number(Map<String,Object> row, String key) { return ((Number) row.get(key)).longValue(); }
    private BusinessException failure(ErrorCode code, String message) {
        log.warn("团队邀请请求被拒绝：{}", message); return new BusinessException(code, message);
    }
}
