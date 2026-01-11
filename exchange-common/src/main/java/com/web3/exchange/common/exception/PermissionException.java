package com.web3.exchange.common.exception;

/**
 * 权限异常
 */
public class PermissionException extends BaseException {
    public PermissionException(String message) {
        super(403, message);
    }

    public PermissionException(String message, Throwable cause) {
        super(403, message, cause);
    }
}
