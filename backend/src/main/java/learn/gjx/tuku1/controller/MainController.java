package learn.gjx.tuku1.controller;


import learn.gjx.tuku1.common.BaseResponse;
import learn.gjx.tuku1.common.ResultUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/")
public class MainController {

    @GetMapping("/health")
    public BaseResponse<String>health(){
        return ResultUtils.success("ok");
    }
}
