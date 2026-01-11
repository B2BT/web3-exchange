package com.web3.exchange.common.exception.web3;

import com.web3.exchange.common.exception.BaseException;

/**
 * 代币异常
 */
public class TokenException extends BaseException {
    private String tokenAddress;

    public TokenException(String message, String tokenAddress) {
        super(1007, message);
        this.tokenAddress = tokenAddress;
    }
}
