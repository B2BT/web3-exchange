package com.web3.exchange.common.exception.web3;

import com.web3.exchange.common.exception.BaseException;

/**
 * 钱包异常
 */
public class WalletException extends BaseException {
    public WalletException(String message) {
        super(1001, message);
    }

    public WalletException(String message, Throwable cause) {
        super(1001, message, cause);
    }
}
