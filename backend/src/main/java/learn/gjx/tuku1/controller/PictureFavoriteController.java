package learn.gjx.tuku1.controller;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import learn.gjx.tuku1.annotation.AuthCheck;
import learn.gjx.tuku1.common.BaseResponse;
import learn.gjx.tuku1.model.dto.picture.*;
import learn.gjx.tuku1.model.vo.*;
import learn.gjx.tuku1.service.*;
import org.springframework.web.bind.annotation.*;
import javax.annotation.Resource;
import javax.servlet.http.*;

@RestController @RequestMapping("/picture/favorite")
public class PictureFavoriteController {
    @Resource private PictureFavoriteService favorites;
    @Resource private UserService users;
    @ModelAttribute public void noStore(HttpServletResponse response) {
        response.setHeader("Cache-Control", "private, no-store"); response.setHeader("Vary", "Cookie");
    }
    /** 幂等设置当前用户的收藏状态；不能指定他人身份。 */
    @PostMapping("/set") @AuthCheck
    public BaseResponse<PictureFavoriteVO> set(@RequestBody PictureFavoriteRequest r, HttpServletRequest request) {
        return BaseResponse.success(favorites.set(r, users.getLoginUser(request)));
    }
    /** 分页返回当前用户仍可公开访问的收藏。 */
    @PostMapping("/mine") @AuthCheck
    public BaseResponse<Page<PictureVO>> mine(@RequestBody FavoritePageRequest r, HttpServletRequest request) {
        return BaseResponse.success(favorites.mine(r, users.getLoginUser(request), request));
    }
}
