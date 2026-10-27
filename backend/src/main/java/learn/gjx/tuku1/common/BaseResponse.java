package learn.gjx.tuku1.common;

import learn.gjx.tuku1.exception.ErrorCode;

/**
 * 通用响应类
 *
 * @param <T> 数据类型
 */
public class BaseResponse<T> {
    private int code;
    private String message;
    private T data;

    /**
     * 构造函数
     *
     * @param code 错误码
     * @param message 错误信息
     * @param data 数据
     */
    public BaseResponse(int code, String message, T data) {
        this.code = code;
        this.message = message;
        this.data = data;
    }

    /**
     * 构造函数
     *
     * @param code 错误码
     * @param message 错误信息
     */
    public BaseResponse(int code, String message) {
        this(code, message, null);
    }

    /**
     * 构造函数
     *
     * @param errorCode 错误码枚举
     */
    public BaseResponse(ErrorCode errorCode) {
        this(errorCode.getCode(), errorCode.getMessage(), null);
    }

    /**
     * 构造函数
     *
     * @param errorCode 错误码枚举
     * @param data 数据
     */
    public BaseResponse(ErrorCode errorCode, T data) {
        this(errorCode.getCode(), errorCode.getMessage(), data);
    }

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
        return new BaseResponse<>(errorCode);
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
     * 获取错误码
     *
     * @return 错误码
     */
    public int getCode() {
        return code;
    }

    /**
     * 设置错误码
     *
     * @param code 错误码
     */
    public void setCode(int code) {
        this.code = code;
    }

    /**
     * 获取错误信息
     *
     * @return 错误信息
     */
    public String getMessage() {
        return message;
    }

    /**
     * 设置错误信息
     *
     * @param message 错误信息
     */
    public void setMessage(String message) {
        this.message = message;
    }

    /**
     * 获取数据
     *
     * @return 数据
     */
    public T getData() {
        return data;
    }

    /**
     * 设置数据
     *
     * @param data 数据
     */
    public void setData(T data) {
        this.data = data;
    }
}
