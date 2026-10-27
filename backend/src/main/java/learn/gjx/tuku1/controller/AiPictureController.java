package learn.gjx.tuku1.controller;

import learn.gjx.tuku1.annotation.AuthCheck;
import learn.gjx.tuku1.common.BaseResponse;
import learn.gjx.tuku1.model.dto.picture.AiOutpaintingRequest;
import learn.gjx.tuku1.model.entity.User;
import learn.gjx.tuku1.model.vo.*;
import learn.gjx.tuku1.service.*;
import org.springframework.web.bind.annotation.*;
import javax.servlet.http.*;
import java.io.IOException;
import java.util.*;

@RestController
@RequestMapping("/picture")
public class AiPictureController {
    private final AiPictureService service;private final UserService users;
    public AiPictureController(AiPictureService service,UserService users){this.service=service;this.users=users;}
    @ModelAttribute
    public void privateHeaders(HttpServletResponse response){
        response.setHeader("Cache-Control","private, no-store");response.setHeader("Vary","Cookie");
        response.setHeader("X-Content-Type-Options","nosniff");response.setHeader("Referrer-Policy","no-referrer");
    }
    @GetMapping("/editing/source") @AuthCheck
    public void source(Long id,HttpServletRequest request,HttpServletResponse response)throws IOException {
        byte[] data=service.source(id,users.getLoginUser(request));
        response.setContentType("application/octet-stream");response.getOutputStream().write(data);
    }
    @GetMapping("/out_painting/capabilities") @AuthCheck
    public BaseResponse<Map<String,Boolean>> capabilities(HttpServletRequest request){
        User user=users.getLoginUser(request);Map<String,Boolean> result=new HashMap<>();
        result.put("enabled",service.enabled(user));result.put("privateEnabled",service.privateEnabled());return BaseResponse.success(result);
    }
    @PostMapping("/out_painting/create_task") @AuthCheck
    public BaseResponse<AiTaskVO> create(@RequestBody AiOutpaintingRequest body,HttpServletRequest request){return BaseResponse.success(service.create(body,users.getLoginUser(request),request.getContextPath()));}
    @GetMapping("/out_painting/get_task") @AuthCheck
    public BaseResponse<AiTaskVO> get(String taskId,HttpServletRequest request){return BaseResponse.success(service.get(taskId,users.getLoginUser(request),request.getContextPath()));}
    @GetMapping("/out_painting/latest") @AuthCheck
    public BaseResponse<AiTaskVO> latest(Long pictureId,HttpServletRequest request){return BaseResponse.success(service.latest(pictureId,users.getLoginUser(request),request.getContextPath()));}
    @GetMapping("/out_painting/result") @AuthCheck
    public void result(String taskId,HttpServletRequest request,HttpServletResponse response)throws IOException {
        byte[] data=service.result(taskId,users.getLoginUser(request));
        // Browser fetches this as a Blob; it is never an external URL/redirect.
        response.setContentType("application/octet-stream");response.getOutputStream().write(data);
    }
    @PostMapping("/out_painting/apply") @AuthCheck
    public BaseResponse<PictureVO> apply(@RequestParam String taskId,HttpServletRequest request){return BaseResponse.success(service.apply(taskId,users.getLoginUser(request),request));}
}
