package com.web3.exchange.common.exception;

/**
 * 服务异常
 */
public class ServiceException extends BaseException {
    public ServiceException(String message) {
        super(500, message);
    }

    public ServiceException(String message, Throwable cause) {
        super(500, message, cause);
    }
}
