package com.web3.exchange.common.exception;

/**
 * 认证异常
 */
public class AuthException extends BaseException {
    public AuthException(String message) {
        super(401, message);
    }

    public AuthException(String message, Throwable cause) {
        super(401, message, cause);
    }
}
