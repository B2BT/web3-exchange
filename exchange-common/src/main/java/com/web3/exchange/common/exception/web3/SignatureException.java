package com.web3.exchange.common.exception.web3;

import com.web3.exchange.common.exception.BaseException;

/**
 * 签名异常
 */
public class SignatureException extends BaseException {
    public SignatureException(String message) {
        super(1004, message);
    }
}
