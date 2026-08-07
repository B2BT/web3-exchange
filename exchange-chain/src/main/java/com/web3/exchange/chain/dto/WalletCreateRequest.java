package com.web3.exchange.chain.dto;

import lombok.Data;

/**
 * 创建自托管钱包请求。
 * <p>服务端生成 HD 助记词并派生首地址（BIP44 索引 0），助记词加密入库、明文仅本次返回一次。</p>
 */
@Data
public class WalletCreateRequest {
    /** 用户ID */
    private Long userId;
    /** 链编码:ETH/BSC/BTC/TRON */
    private String chainCode;
    /** 钱包备注名（可选） */
    private String name;
}
