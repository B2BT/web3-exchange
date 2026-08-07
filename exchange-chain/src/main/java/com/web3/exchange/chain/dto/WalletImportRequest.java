package com.web3.exchange.chain.dto;

import lombok.Data;

/**
 * 导入自托管钱包请求。
 * <p>二选一：mnemonic（BIP39 助记词，HD）或 privateKey（十六进制私钥，PRIVATE）。
 * 服务端校验后派生地址，私钥/助记词加密入库。</p>
 */
@Data
public class WalletImportRequest {
    /** 用户ID */
    private Long userId;
    /** 链编码:ETH/BSC/BTC/TRON */
    private String chainCode;
    /** BIP39 助记词（用空格分隔的英文单词） */
    private String mnemonic;
    /** 十六进制私钥（可带 0x） */
    private String privateKey;
    /** 钱包备注名（可选） */
    private String name;
}
