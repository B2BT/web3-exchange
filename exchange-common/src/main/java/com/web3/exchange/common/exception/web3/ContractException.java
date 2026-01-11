package com.web3.exchange.common.exception.web3;

import com.web3.exchange.common.exception.BaseException;

/**
 * 合约调用异常
 */
public class ContractException extends BaseException {
    private String contractAddress;
    private String method;

    public ContractException(String message, String contractAddress, String method) {
        super(1003, message);
        this.contractAddress = contractAddress;
        this.method = method;
    }
}
