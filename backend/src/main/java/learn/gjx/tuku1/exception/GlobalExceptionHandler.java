package learn.gjx.tuku1.exception;


import learn.gjx.tuku1.common.BaseResponse;
import learn.gjx.tuku1.common.ResultUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.http.converter.HttpMessageNotReadableException;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public BaseResponse handleUnreadableRequest(HttpMessageNotReadableException e) {
        // Do not echo request bodies, parser internals or private data to the client/log.
        return ResultUtils.error(ErrorCode.PARAMS_ERROR, "请求参数格式错误，请检查日期和数字格式");
    }

    @ExceptionHandler(BusinessException.class)
    public BaseResponse handleBusinessException(BusinessException e){
        log.error(e.getMessage());
        return ResultUtils.error(e);
    }

}
