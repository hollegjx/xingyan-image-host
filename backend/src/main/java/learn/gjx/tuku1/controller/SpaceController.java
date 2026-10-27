package learn.gjx.tuku1.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import learn.gjx.tuku1.annotation.AuthCheck;
import learn.gjx.tuku1.common.BaseResponse;
import learn.gjx.tuku1.exception.ErrorCode;
import learn.gjx.tuku1.exception.ThrowUtils;
import learn.gjx.tuku1.model.dto.space.*;
import learn.gjx.tuku1.model.entity.Space;
import learn.gjx.tuku1.model.enums.UserRoleEnum;
import learn.gjx.tuku1.model.vo.SpaceLevelVO;
import learn.gjx.tuku1.model.vo.SpaceVO;
import learn.gjx.tuku1.service.SpaceService;
import learn.gjx.tuku1.service.UserService;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import java.util.List;

@RestController
@RequestMapping("/space")
public class SpaceController {
    @ModelAttribute
    public void noStore(javax.servlet.http.HttpServletResponse response) {
        response.setHeader("Cache-Control", "no-store"); response.setHeader("Vary", "Cookie");
    }
    @Resource
    private SpaceService spaceService;
    @Resource
    private UserService userService;

    @PostMapping("/add")
    @AuthCheck
    public BaseResponse<Long> addSpace(@RequestBody SpaceAddRequest add, HttpServletRequest request) {
        return BaseResponse.success(spaceService.addSpace(add, userService.getLoginUser(request)));
    }

    @GetMapping("/list/level")
    public BaseResponse<List<SpaceLevelVO>> listSpaceLevels() {
        return BaseResponse.success(SpaceLevelVO.listLevels());
    }

    @GetMapping("/get")
    @AuthCheck(mustRole = UserRoleEnum.ADMIN)
    public BaseResponse<Space> getSpace(@RequestParam Long id, HttpServletRequest request) {
        return BaseResponse.success(spaceService.getSpaceForAdmin(id, userService.getLoginUser(request)));
    }

    @GetMapping("/get/vo")
    @AuthCheck
    public BaseResponse<SpaceVO> getSpaceVO(@RequestParam Long id, HttpServletRequest request) {
        return BaseResponse.success(spaceService.getSpaceVO(id, userService.getLoginUser(request)));
    }

    @PostMapping("/list/page")
    @AuthCheck(mustRole = UserRoleEnum.ADMIN)
    public BaseResponse<Page<Space>> listSpaces(@RequestBody SpaceQueryRequest query,
                                              HttpServletRequest request) {
        return BaseResponse.success(spaceService.listSpaces(query, userService.getLoginUser(request)));
    }

    @PostMapping("/my/list/page/vo")
    @AuthCheck
    public BaseResponse<Page<SpaceVO>> listMySpaces(@RequestBody SpaceQueryRequest query,
                                                  HttpServletRequest request) {
        return BaseResponse.success(spaceService.listMySpaces(query, userService.getLoginUser(request)));
    }

    @PostMapping("/edit")
    @AuthCheck
    public BaseResponse<Boolean> editSpace(@RequestBody SpaceEditRequest edit, HttpServletRequest request) {
        return BaseResponse.success(spaceService.editSpace(edit, userService.getLoginUser(request)));
    }

    @PostMapping("/update")
    @AuthCheck(mustRole = UserRoleEnum.ADMIN)
    public BaseResponse<Boolean> updateSpace(@RequestBody SpaceUpdateRequest update,
                                            HttpServletRequest request) {
        return BaseResponse.success(spaceService.updateSpace(update, userService.getLoginUser(request)));
    }

    @PostMapping("/delete")
    @AuthCheck
    public BaseResponse<Boolean> deleteSpace(@RequestBody SpaceDeleteRequest delete,
                                            HttpServletRequest request) {
        ThrowUtils.throwIf(delete == null, ErrorCode.PARAMS_ERROR);
        return BaseResponse.success(spaceService.deleteEmptySpace(delete.getId(),
                userService.getLoginUser(request)));
    }
}
