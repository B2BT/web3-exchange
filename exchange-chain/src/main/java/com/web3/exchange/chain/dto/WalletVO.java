package com.web3.exchange.chain.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 自托管钱包视图。
 * <p>create 时一次性返回明文 mnemonic（仅当次，不入库明文返回），其余接口不返回。</p>
 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class WalletVO {
    /** 钱包ID（雪花，String 防 JS 精度丢失） */
    private Long id;
    /** 用户ID */
    private Long userId;
    /** 链编码 */
    private String chainCode;
    /** 钱包类型:HD/PRIVATE/READONLY */
    private String walletType;
    /** 钱包地址 */
    private String address;
    /** 地址类型:SELF/CUSTODIAL */
    private String addressType;
    /** 钱包备注名 */
    private String name;
    /** 创建时一次性返回的明文助记词（仅 HD create 返回一次） */
    private String mnemonic;
    /** 状态 */
    private Integer status;
    /** 创建时间 */
    private LocalDateTime createTime;
}
