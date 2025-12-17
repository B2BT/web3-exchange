package com.web3.exchange.common.constant;

import lombok.Getter;

@Getter
public enum ErrorCode {
    // =================== 系统错误码 ===================
    SUCCESS(200, "操作成功"),
    FAILURE(500, "系统异常"),

    // =================== 客户端错误 ===================
    BAD_REQUEST(400, "请求参数错误"),
    UNAUTHORIZED(401, "未授权"),
    FORBIDDEN(403, "禁止访问"),
    NOT_FOUND(404, "资源不存在"),
    METHOD_NOT_ALLOWED(405, "请求方法不允许"),

    // =================== 业务错误码 ===================
    USER_NOT_EXIST(10001, "用户不存在"),
    USER_DISABLED(10002, "用户已禁用"),
    USERNAME_EXISTS(10003, "用户名已存在"),
    EMAIL_EXISTS(10004, "邮箱已存在"),
    PHONE_EXISTS(10005, "手机号已存在"),
    PASSWORD_ERROR(10006, "密码错误"),
    OLD_PASSWORD_ERROR(10007, "原密码错误"),

    // =================== 权限相关 ===================
    NO_PERMISSION(20001, "无操作权限"),
    TOKEN_EXPIRED(20002, "Token已过期"),
    TOKEN_INVALID(20003, "Token无效"),

    // =================== 数据相关 ===================
    DATA_NOT_EXIST(30001, "数据不存在"),
    DATA_EXISTS(30002, "数据已存在"),
    DATA_ERROR(30003, "数据错误"),

    // =================== 文件相关 ===================
    FILE_EMPTY(40001, "文件为空"),
    FILE_TOO_LARGE(40002, "文件过大"),
    FILE_TYPE_ERROR(40003, "文件类型错误"),
    FILE_UPLOAD_ERROR(40004, "文件上传失败"),

    // =================== 系统配置 ===================
    CONFIG_ERROR(50001, "系统配置错误"),
    SYSTEM_BUSY(50002, "系统繁忙，请稍后重试");

    private final Integer code;
    private final String message;

    ErrorCode(int code, String message) {
        this.code = code;
        this.message = message;
    }

    /**
     * 根据code获取枚举
     *
     * @param code
     * @return
     */
    public static ErrorCode getByCode(Integer code) {
        for (ErrorCode errorCode : ErrorCode.values()) {
            if (errorCode.code.equals(code)) {
                return errorCode;
            }
        }
        return null;
    }

    /**
     * 根据message获取枚举
     *
     * @param message
     * @return
     */
    public static ErrorCode getByMessage(String message) {
        for (ErrorCode errorCode : ErrorCode.values()) {
            if (errorCode.message.equals(message)) {
                return errorCode;
            }
        }
        return null;
    }


}
