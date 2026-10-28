package learn.gjx.tuku1.controller;

import learn.gjx.tuku1.common.BaseResponse;
import learn.gjx.tuku1.config.ImageSearchProperties;
import learn.gjx.tuku1.exception.BusinessException;
import learn.gjx.tuku1.manager.PrivatePictureFileManager;
import org.springframework.web.bind.annotation.*;
import javax.servlet.http.HttpServletResponse;
import java.util.*;

@RestController
@RequestMapping("/picture")
public class PictureCapabilitiesController {
    private final PrivatePictureFileManager storage;
    private final ImageSearchProperties search;
    public PictureCapabilitiesController(PrivatePictureFileManager storage, ImageSearchProperties search) {
        this.storage=storage;this.search=search;
    }
    /** Configuration gates, not a claim that cloud credentials/ACLs have passed acceptance. */
    @GetMapping("/capabilities")
    public BaseResponse<Map<String,Boolean>> capabilities(HttpServletResponse response) {
        response.setHeader("Cache-Control","no-store");
        boolean configured=false;
        try {storage.bucket(1L);configured=true;} catch (BusinessException ignored) { }
        Map<String,Boolean> result=new HashMap<>();
        result.put("privateStorageConfigured",configured);result.put("externalImageSearchEnabled",search.isEnabled());
        return BaseResponse.success(result);
    }
}
