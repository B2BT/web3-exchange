package com.web3.exchange.common.exception.web3;

import com.web3.exchange.common.exception.BaseException;

/**
 * Gas 异常
 */
public class GasException extends BaseException {
    public GasException(String message) {
        super(1006, message);
    }
}
