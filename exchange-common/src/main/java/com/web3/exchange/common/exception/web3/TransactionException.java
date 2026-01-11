package com.web3.exchange.common.exception.web3;

import com.web3.exchange.common.exception.BaseException;

/**
 * 链上交易异常
 */
public class TransactionException extends BaseException {
    private String txHash;
    private String chain;

    public TransactionException(String message, String txHash, String chain) {
        super(1002, message);
        this.txHash = txHash;
        this.chain = chain;
    }
}
