package learn.gjx.tuku1.controller;

import learn.gjx.tuku1.annotation.AuthCheck;
import learn.gjx.tuku1.common.BaseResponse;
import learn.gjx.tuku1.model.dto.space.analyze.SpaceAnalyzeRequest;
import learn.gjx.tuku1.model.vo.SpaceAnalysisVO;
import learn.gjx.tuku1.model.vo.SpaceAnalysisVO.*;
import learn.gjx.tuku1.service.SpaceAnalyzeService;
import learn.gjx.tuku1.service.UserService;
import org.springframework.web.bind.annotation.*;
import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.List;

@RestController
@RequestMapping("/space/analyze")
public class SpaceAnalyzeController {
    @Resource private SpaceAnalyzeService service;
    @Resource private UserService users;

    @ModelAttribute
    public void noStore(HttpServletResponse response) {
        response.setHeader("Cache-Control", "no-store");
        response.setHeader("Vary", "Cookie");
    }

    @PostMapping("/dashboard") @AuthCheck
    public BaseResponse<SpaceAnalysisVO> dashboard(@RequestBody SpaceAnalyzeRequest body, HttpServletRequest request) {
        return BaseResponse.success(service.dashboard(body, users.getLoginUser(request)));
    }
    @PostMapping("/usage") @AuthCheck
    public BaseResponse<Usage> usage(@RequestBody SpaceAnalyzeRequest body, HttpServletRequest request) {
        return BaseResponse.success(service.usage(body, users.getLoginUser(request)));
    }
    @PostMapping("/category") @AuthCheck
    public BaseResponse<List<Distribution>> category(@RequestBody SpaceAnalyzeRequest body, HttpServletRequest request) {
        return BaseResponse.success(service.categories(body, users.getLoginUser(request)));
    }
    @PostMapping("/tag") @AuthCheck
    public BaseResponse<List<Distribution>> tag(@RequestBody SpaceAnalyzeRequest body, HttpServletRequest request) {
        return BaseResponse.success(service.tags(body, users.getLoginUser(request)));
    }
    @PostMapping("/size") @AuthCheck
    public BaseResponse<List<Distribution>> size(@RequestBody SpaceAnalyzeRequest body, HttpServletRequest request) {
        return BaseResponse.success(service.sizes(body, users.getLoginUser(request)));
    }
    @PostMapping("/user") @AuthCheck
    public BaseResponse<Trend> user(@RequestBody SpaceAnalyzeRequest body, HttpServletRequest request) {
        return BaseResponse.success(service.trend(body, users.getLoginUser(request)));
    }
    @PostMapping("/rank") @AuthCheck
    public BaseResponse<List<Ranking>> rank(@RequestBody SpaceAnalyzeRequest body, HttpServletRequest request) {
        return BaseResponse.success(service.rankings(body, users.getLoginUser(request)));
    }
}
