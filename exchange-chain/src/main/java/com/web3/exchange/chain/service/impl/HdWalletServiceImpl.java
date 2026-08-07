package com.web3.exchange.chain.service.impl;

import com.web3.exchange.chain.config.ChainProperties;
import com.web3.exchange.chain.service.HdWalletService;
import com.web3.exchange.common.exception.web3.WalletException;
import org.springframework.stereotype.Service;
import org.web3j.crypto.Bip32ECKeyPair;
import org.web3j.crypto.Keys;
import org.web3j.crypto.MnemonicUtils;

/**
 * HD 钱包派生实现（BIP32/BIP44）。
 * <p>主助记词从 chain.hd-wallet.mnemonic 注入（仅 Mock/测试网；生产严禁明文）。
 * EVM 链（ETH/BSC/POLYGON）coinType=60，路径 m/44'/60'/0'/0/index；BTC=0、TRON=195 预留。
 * 子地址派生只算公钥地址，私钥不落库。</p>
 */
@Service
public class HdWalletServiceImpl implements HdWalletService {

    private final Bip32ECKeyPair master;

    public HdWalletServiceImpl(ChainProperties chainProperties) {
        ChainProperties.HdWallet hd = chainProperties.getHdWallet();
        String mnemonic = hd == null ? null : hd.getMnemonic();
        if (mnemonic == null || mnemonic.isBlank()) {
            throw new WalletException("HD 主助记词未配置（chain.hd-wallet.mnemonic），仅 Mock/测试网可用");
        }
        if (!MnemonicUtils.validateMnemonic(mnemonic.trim())) {
            throw new WalletException("HD 主助记词非法（BIP39 校验失败）");
        }
        String passphrase = hd.getPassphrase() == null ? "" : hd.getPassphrase();
        byte[] seed = MnemonicUtils.generateSeed(mnemonic.trim(), passphrase);
        this.master = Bip32ECKeyPair.generateKeyPair(seed);
    }

    @Override
    public String deriveAddress(String chainCode, int index) {
        int coinType = coinTypeOf(chainCode);
        int[] path = {
                44 + Bip32ECKeyPair.HARDENED_BIT,        // purpose 44'
                coinType + Bip32ECKeyPair.HARDENED_BIT,  // coinType'
                0 + Bip32ECKeyPair.HARDENED_BIT,         // account 0'
                0,                                       // change 0（外部地址）
                index                                    // address index
        };
        Bip32ECKeyPair child = Bip32ECKeyPair.deriveKeyPair(master, path);
        return Keys.getAddress(child); // 返回 0x + 40 hex 小写（EVM）
    }

    /** BIP44 标准 coinType：BTC=0 / ETH/BSC/POLYGON=60 / TRON=195。 */
    private int coinTypeOf(String chainCode) {
        if (chainCode == null) {
            throw new WalletException("chainCode 不能为空");
        }
        String c = chainCode.toUpperCase();
        return switch (c) {
            case "BTC" -> 0;
            case "TRON", "T" -> 195;
            default -> 60; // EVM 系（ETH/BSC/POLYGON/Arbitrum 等）统一 60
        };
    }
}
