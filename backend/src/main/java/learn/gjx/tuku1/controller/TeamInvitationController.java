package learn.gjx.tuku1.controller;

import learn.gjx.tuku1.annotation.AuthCheck;
import learn.gjx.tuku1.common.BaseResponse;
import learn.gjx.tuku1.model.dto.space.*;
import learn.gjx.tuku1.model.vo.*;
import learn.gjx.tuku1.service.*;
import org.springframework.web.bind.annotation.*;
import javax.annotation.Resource;
import javax.servlet.http.*;
import java.util.List;

@RestController
@RequestMapping("/spaceUser/invite")
public class TeamInvitationController {
    @Resource private TeamInvitationService invitations;
    @Resource private UserService users;
    @ModelAttribute public void noStore(HttpServletResponse response) {
        response.setHeader("Cache-Control", "no-store"); response.setHeader("Referrer-Policy", "no-referrer");
    }
    /** 创建邀请，只在此次响应返回原始 token。 */
    @PostMapping("/create") @AuthCheck
    public BaseResponse<TeamInvitationCreatedVO> create(@RequestBody TeamInviteCreateRequest r, HttpServletRequest request) {
        return BaseResponse.success(invitations.create(r, users.getLoginUser(request)));
    }
    /** 查看有效邀请的角色、到期时间和使用次数，不返回 token。 */
    @PostMapping("/list") @AuthCheck
    public BaseResponse<List<TeamInvitationVO>> list(@RequestBody TeamInviteManageRequest r, HttpServletRequest request) {
        return BaseResponse.success(invitations.list(r.getSpaceId(), users.getLoginUser(request)));
    }
    /** 团队管理员作废邀请。 */
    @PostMapping("/revoke") @AuthCheck
    public BaseResponse<Boolean> revoke(@RequestBody TeamInviteManageRequest r, HttpServletRequest request) {
        return BaseResponse.success(invitations.revoke(r, users.getLoginUser(request)));
    }
    /** 登录后查看邀请；不执行加入，不把凭据放入查询 URL。 */
    @PostMapping("/preview") @AuthCheck
    public BaseResponse<TeamInvitationVO> preview(@RequestBody TeamInviteTokenRequest r, HttpServletRequest request) {
        return BaseResponse.success(invitations.preview(r.getToken(), users.getLoginUser(request)));
    }
    /** 用户明确确认后加入，服务端决定身份和邀请角色。 */
    @PostMapping("/join") @AuthCheck
    public BaseResponse<Long> join(@RequestBody TeamInviteTokenRequest r, HttpServletRequest request) {
        return BaseResponse.success(invitations.join(r.getToken(), users.getLoginUser(request)));
    }
}
