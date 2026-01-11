package com.web3.exchange.common.exception;

/**
 * 资源未找到
 */
public class NotFoundException extends BaseException {
    public NotFoundException(String message) {
        super(404, message);
    }

    public NotFoundException(String message, Throwable cause) {
        super(404, message, cause);
    }
}
