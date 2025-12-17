package com.web3.exchange.common.model;

import com.web3.exchange.common.constant.ErrorCode;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import java.io.Serializable;

@Data
@Schema(description = "统一响应结果")
public class Result<T> implements Serializable {
    @Schema(description = "状态码", example = "200")
    private Integer code;
    @Schema(description = "提示信息", example = "操作成功")
    private String message;
    @Schema(description = "响应数据")
    private T data;
    @Schema(description = "时间戳", example = "1672502400000")
    private Long timestamp = System.currentTimeMillis();

    public Result() {
    }

    public Result(Integer code, String message, T data) {
        this.code = code;
        this.message = message;
        this.data = data;
    }
    // =============== 成功响应 ================
    public static <T> Result<T> success() {
        return success(null);
    }

    public static <T> Result<T> success(T data) {
        return success(data, "success");
    }

    public static <T> Result<T> success(T data, String message) {
        return new Result<>(200, message, data);
    }
    // =============== 失败响应 ================
    public static <T> Result<T> error(String message) {
        return error(500, message);
    }
    public static <T> Result<T> error(Integer code, String message) {
        return new Result<>(code, message,null);
    }

    public static <T> Result<T> error(ErrorCode errorCode) {
        return error(errorCode.getCode(),errorCode.getMessage());
    }
    // =================== 业务异常 ===================
    public static <T> Result<T> badRequest(String message) {
        return error(400, message);
    }

    public static <T> Result<T> unauthorized(String message) {
        return error(401, message);
    }

    public static <T> Result<T> forbidden(String message) {
        return error(403, message);
    }

    public static <T> Result<T> notFound(String message) {
        return error(404, message);
    }

    public static <T> Result<T> methodNotAllowed(String message) {
        return error(405, message);
    }

    public static <T> Result<T> conflict(String message) {
        return error(409, message);
    }

    public static <T> Result<T> tooManyRequests(String message) {
        return error(429, message);
    }

    public static <T> Result<T> internalError(String message) {
        return error(500, message);
    }

    public static <T> Result<T> serviceUnavailable(String message) {
        return error(503, message);
    }

    //  =================== 判断方法 ===================
    public boolean isSuccess() {
        return code != null && code == 200;
    }
    public boolean isError() {
        return !isSuccess();
    }

}
