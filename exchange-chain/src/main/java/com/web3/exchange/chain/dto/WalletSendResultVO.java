package com.web3.exchange.chain.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

/**
 * 自托管钱包转账结果。
 */
@Data
public class WalletSendResultVO {
    /** 钱包ID（雪花，String 防 JS 精度丢失） */
    @JsonSerialize(using = ToStringSerializer.class)
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
