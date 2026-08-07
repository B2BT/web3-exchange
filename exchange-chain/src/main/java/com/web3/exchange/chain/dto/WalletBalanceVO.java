package com.web3.exchange.chain.dto;

import lombok.Data;

/**
 * 自托管钱包链上余额（最小单位 Long）。
 */
@Data
public class WalletBalanceVO {
    /** 链编码 */
    private String chainCode;
    /** 币种符号 */
    private String symbol;
    /** 币种类型:COIN=原生/TOKEN=代币 */
    private String coinType;
    /** 链上余额（最小单位 Long） */
    private String balance;
    /** 精度（最小单位位数） */
    private Integer decimals;
}
