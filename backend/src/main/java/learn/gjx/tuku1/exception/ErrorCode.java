package learn.gjx.tuku1.exception;

public enum ErrorCode {
    SUCCESS(0,"ok"),
    PARAMS_ERROR(40000,"参数请求错误"),
    NOT_LOGIN_ERROR(40100,"未登录"),
    NO_AUTH_ERROR(40101,"无权限"),
    NOT_FOUND_ERROR(40400,"请求数据不存在"),
    FORBIDDEN_ERROR(40300,"禁止访问"),
    SYSTEM_ERROR(50000,"系统内部异常"),
    OPERATION_ERROR(50001,"操作失败"),
    DATABASE_ERROR(50002,"数据库错误"),
    TOO_MANY_REQUEST(42900,"请求过于频繁"),
    RESOURCE_CONFLICT(40900,"资源已存在"),
    SERVICE_UNAVAILABLE(50300,"服务暂时不可用");

    private final int code;
    private final String message;
    ErrorCode(int code, String message) {
        this.code = code;
        this.message = message;
    }

    public int getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }

}
