package com.web3.exchange.common.exception;

import lombok.Data;

/**
 * 异常基类
 */
@Data
public class BaseException extends RuntimeException {
    private Integer code;
    private String message;
    private Object data;
    public Throwable cause;
    public BaseException(Integer errorCode, String message) {
        this.code = errorCode;
        this.message = message;
    }
    public BaseException(Integer code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
        this.message = message;
        this.cause = cause;
    }
}
