package com.web3.exchange.chain.service;

import com.web3.exchange.chain.dto.WalletBalanceVO;
import com.web3.exchange.chain.dto.WalletCreateRequest;
import com.web3.exchange.chain.dto.WalletImportRequest;
import com.web3.exchange.chain.dto.WalletVO;
import com.web3.exchange.chain.entity.UserWallet;

import java.util.List;

/**
 * 用户自托管钱包服务（创建/导入/列表/地址/余额）。
 */
public interface UserWalletService {

    /**
     * 创建自托管钱包：服务端生成 BIP39 助记词 + BIP44 派生首地址（索引 0），
     * 助记词与私钥 AES-GCM 加密入库；明文助记词仅本次返回一次。
     */
    WalletVO create(WalletCreateRequest req);

    /**
     * 导入自托管钱包：mnemonic（HD）或 privateKey（PRIVATE），校验后派生地址并加密入库。
     */
    WalletVO importWallet(WalletImportRequest req);

    /** 用户钱包列表（不含明文助记词/私钥）。 */
    List<WalletVO> listByUser(Long userId);

    /** 钱包详情（含解密后的地址，不含明文助记词/私钥）。 */
    UserWallet getById(Long userId, Long walletId);

    /** 查询钱包链上余额（原生币 + 该链已启用代币）。 */
    List<WalletBalanceVO> balance(Long userId, Long walletId);
}
