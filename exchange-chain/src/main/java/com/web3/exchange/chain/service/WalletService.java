package com.web3.exchange.chain.service;

import org.web3j.crypto.Credentials;
import org.web3j.crypto.RawTransaction;

/**
 * 热钱包私钥管理与签名自检。
 * <p>本期私钥从配置注入（仅 Mock/测试网，严禁明文入库/落日志）；生产需 KMS/HSM 或加密 keystore。</p>
 */
public interface WalletService {

    /** 获取热钱包凭据（缓存 + 私钥解密）。 */
    Credentials getCredentials();

    /**
     * 签名自检：用热钱包私钥对同一 rawTx+chainId 重新签名，比较 r/s。
     * ECDSA 签名确定性，重签结果一致即证明该签名确由热钱包私钥产生。
     *
     * @return 签名是否匹配热钱包私钥
     */
    boolean verifySigner(byte[] signedRaw, RawTransaction rawTx, long chainId);
}
