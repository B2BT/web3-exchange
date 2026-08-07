package com.web3.exchange.chain.dto;

import lombok.Data;

/**
 * 自托管钱包转账结果。
 */
@Data
public class WalletSendResultVO {
    /** 钱包ID */
    private Long walletId;
    /** 链编码 */
    private String chainCode;
    /** 币种符号 */
    private String symbol;
    /** 转出地址 */
    private String fromAddress;
    /** 目标地址 */
    private String toAddress;
    /** 转账金额（最小单位 Long） */
    private Long amount;
    /** 上链交易哈希 */
    private String txHash;
}
