package learn.gjx.tuku1.common;

import learn.gjx.tuku1.exception.BusinessException;
import learn.gjx.tuku1.exception.ErrorCode;

/**
 * 响应工具类
 */
public class ResultUtils {

    /**
     * 成功返回（带数据）
     *
     * @param data 数据
     * @return 响应对象
     * @param <T> 数据类型
     */
    public static <T> BaseResponse<T> success(T data) {
        return new BaseResponse<>(0, "ok", data);
    }

    /**
     * 成功返回（无数据）
     *
     * @return 响应对象
     * @param <T> 数据类型
     */
    public static <T> BaseResponse<T> success() {
        return new BaseResponse<>(0, "ok", null);
    }

    /**
     * 失败返回（使用ErrorCode）
     *
     * @param errorCode 错误码枚举
     * @return 响应对象
     * @param <T> 数据类型
     */
    public static <T> BaseResponse<T> error(ErrorCode errorCode) {
        return new BaseResponse<>(errorCode.getCode(), errorCode.getMessage(), null);
    }

    /**
     * 失败返回（使用ErrorCode和自定义消息）
     *
     * @param errorCode 错误码枚举
     * @param message 错误信息
     * @return 响应对象
     * @param <T> 数据类型
     */
    public static <T> BaseResponse<T> error(ErrorCode errorCode, String message) {
        return new BaseResponse<>(errorCode.getCode(), message, null);
    }

    /**
     * 失败返回（自定义code和message）
     *
     * @param code 错误码
     * @param message 错误信息
     * @return 响应对象
     * @param <T> 数据类型
     */
    public static <T> BaseResponse<T> error(int code, String message) {
        return new BaseResponse<>(code, message, null);
    }

    /**
     * 失败返回（使用BusinessException）
     *
     * @param e 业务异常
     * @return 响应对象
     * @param <T> 数据类型
     */
    public static <T> BaseResponse<T> error(BusinessException e) {
        return new BaseResponse<>(e.getCode(), e.getMessage(), null);
    }
}
