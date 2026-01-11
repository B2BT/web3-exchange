package com.web3.exchange.common.exception;

/**
 * 业务异常
 */
public class BusinessException extends BaseException {
    public BusinessException(String message) {
        super(400, message);
    }

    public BusinessException(String message, Throwable cause) {
        super(400, message, cause);
    }

    public BusinessException(Integer code, String message) {
        super(code, message);
    }
}
