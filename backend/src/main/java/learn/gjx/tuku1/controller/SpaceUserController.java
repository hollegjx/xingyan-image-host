package learn.gjx.tuku1.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import learn.gjx.tuku1.annotation.AuthCheck;
import learn.gjx.tuku1.common.BaseResponse;
import learn.gjx.tuku1.model.dto.space.*;
import learn.gjx.tuku1.model.vo.SpaceUserVO;
import learn.gjx.tuku1.service.*;
import org.springframework.web.bind.annotation.*;
import javax.annotation.Resource;
import javax.servlet.http.*;
import java.util.List;

@RestController
@RequestMapping("/spaceUser")
public class SpaceUserController {
    @Resource private SpaceUserService service;
    @Resource private UserService users;
    @ModelAttribute
    public void noStore(HttpServletResponse response) {
        response.setHeader("Cache-Control", "no-store"); response.setHeader("Vary", "Cookie");
    }
    @PostMapping("/add") @AuthCheck
    public BaseResponse<Long> addSpaceUser(@RequestBody SpaceUserAddRequest r, HttpServletRequest request) {
        return BaseResponse.success(service.add(r, users.getLoginUser(request)));
    }
    @PostMapping("/edit") @AuthCheck
    public BaseResponse<Boolean> editSpaceUser(@RequestBody SpaceUserEditRequest r, HttpServletRequest request) {
        return BaseResponse.success(service.edit(r, users.getLoginUser(request)));
    }
    @PostMapping("/delete") @AuthCheck
    public BaseResponse<Boolean> deleteSpaceUser(@RequestBody SpaceUserEditRequest r, HttpServletRequest request) {
        return BaseResponse.success(service.remove(r, users.getLoginUser(request)));
    }
    @PostMapping("/list") @AuthCheck
    public BaseResponse<List<SpaceUserVO>> listSpaceUser(@RequestBody SpaceUserQueryRequest r, HttpServletRequest request) {
        return BaseResponse.success(service.list(r, users.getLoginUser(request)));
    }
    @PostMapping("/get") @AuthCheck
    public BaseResponse<SpaceUserVO> getSpaceUser(@RequestBody SpaceUserQueryRequest r, HttpServletRequest request) {
        return BaseResponse.success(service.get(r, users.getLoginUser(request)));
    }
    @PostMapping("/my/list/page") @AuthCheck
    public BaseResponse<Page<SpaceUserVO>> listMyTeams(@RequestBody SpaceUserQueryRequest r, HttpServletRequest request) {
        return BaseResponse.success(service.myTeams(r, users.getLoginUser(request)));
    }
}
