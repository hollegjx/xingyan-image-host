package learn.gjx.tuku1.controller;

import learn.gjx.tuku1.annotation.AuthCheck;
import learn.gjx.tuku1.api.imagesearch.ImageSearchResult;
import learn.gjx.tuku1.common.BaseResponse;
import learn.gjx.tuku1.model.dto.picture.SearchPictureByPictureRequest;
import learn.gjx.tuku1.service.PictureSearchService;
import learn.gjx.tuku1.service.UserService;
import org.springframework.web.bind.annotation.*;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.List;

@RestController
@RequestMapping("/picture")
public class PictureSearchController {
    private final PictureSearchService search;
    private final UserService users;
    public PictureSearchController(PictureSearchService search, UserService users) {
        this.search = search;
        this.users = users;
    }

    @PostMapping("/search/picture")
    @AuthCheck
    public BaseResponse<List<ImageSearchResult>> searchPictureByPicture(
            @RequestBody SearchPictureByPictureRequest body,
            HttpServletRequest request, HttpServletResponse response) {
        response.setHeader("Cache-Control", "private, no-store");
        response.setHeader("Vary", "Cookie");
        return BaseResponse.success(search.search(body, users.getLoginUser(request)));
    }
}
