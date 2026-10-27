package learn.gjx.tuku1.controller;

import learn.gjx.tuku1.annotation.AuthCheck;
import learn.gjx.tuku1.common.BaseResponse;
import learn.gjx.tuku1.model.dto.picture.*;
import learn.gjx.tuku1.model.vo.PictureVO;
import learn.gjx.tuku1.service.PictureExtensionService;
import learn.gjx.tuku1.service.UserService;
import org.springframework.web.bind.annotation.*;
import javax.servlet.http.*;
import java.util.List;

@RestController
@RequestMapping("/picture")
public class PictureExtensionController {
    private final PictureExtensionService service;
    private final UserService users;
    public PictureExtensionController(PictureExtensionService service, UserService users) {
        this.service = service; this.users = users;
    }
    @PostMapping("/search/color")
    @AuthCheck
    public BaseResponse<List<PictureVO>> searchPictureByColor(@RequestBody SearchPictureByColorRequest body,
            HttpServletRequest request, HttpServletResponse response) {
        protect(response);
        return BaseResponse.success(service.searchColor(body, users.getLoginUser(request), request));
    }
    @PostMapping("/edit/batch")
    @AuthCheck
    public BaseResponse<Integer> editPictureByBatch(@RequestBody PictureEditByBatchRequest body,
            HttpServletRequest request, HttpServletResponse response) {
        protect(response);
        return BaseResponse.success(service.editBatch(body, users.getLoginUser(request)));
    }
    private void protect(HttpServletResponse response) {
        response.setHeader("Cache-Control", "private, no-store");
        response.setHeader("Vary", "Cookie");
    }
}
