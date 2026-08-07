package com.web3.exchange.chain.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 链上域业务配置（chain.*）。
 * <p>scan.*：充值区块扫描参数；hot-wallet.*：热钱包（提现签名私钥 + 平台收款账户用户ID）。
 * 私钥仅用于 Mock/测试网，生产严禁明文。</p>
 */
@Data
@Component
@ConfigurationProperties(prefix = "chain")
public class ChainProperties {

    /** 充值区块扫描 */
    private Scan scan = new Scan();

    /** 热钱包 */
    private HotWallet hotWallet = new HotWallet();

    /** HD 钱包（BIP44 每用户充币地址派生） */
    private HdWallet hdWallet = new HdWallet();

    /** 自托管钱包（用户创建/导入钱包的私钥加密） */
    private SelfWallet selfWallet = new SelfWallet();

    @Data
    public static class Scan {
        /** 是否开启扫描 */
        private boolean enabled = true;
        /** 轮询间隔(ms) */
        private long intervalMs = 3000;
        /** 每批扫块数 */
        private long batchSize = 100;
        /** 无历史记录时起始块 */
        private long startBlock = 0;
        /** 重启回退安全窗口(块) */
        private long safetyWindow = 3;
    }

    @Data
    public static class HotWallet {
        /** 平台热钱包收款账户用户ID（asset 侧） */
        private Long platformUserId;
        /** 热钱包私钥（仅 Mock/测试网，十六进制无 0x 前缀） */
        private String privateKey;
    }

    @Data
    public static class HdWallet {
        /** BIP39 主助记词（仅 Mock/测试网，严禁明文入库/落日志；生产需 KMS/HSM） */
        private String mnemonic;
        /** 派生口令（通常为空） */
        private String passphrase = "";
    }

    @Data
    public static class SelfWallet {
        /** 自托管钱包私钥/助记词加密密钥（AES-GCM，PBKDF2 派生；仅 Mock/测试网，生产用 KMS/HSM） */
        private String encryptSecret;
    }
}
