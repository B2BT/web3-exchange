package com.web3.exchange.chain.dto;

import lombok.Data;

/**
 * 自托管钱包链上转账请求。
 * <p>用钱包内加密存储的私钥离线签名并广播；amount 为币种最小单位 Long。</p>
 */
@Data
public class WalletSendRequest {
    /** 用户ID */
    private Long userId;
    /** 币种符号（如 ETH 原生 / USDT ERC-20） */
    private String symbol;
    /** 目标地址 */
    private String toAddress;
    /** 转账金额（最小单位 Long） */
    private Long amount;
}
