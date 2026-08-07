package com.web3.exchange.chain.service;

/**
 * HD 钱包（BIP32/BIP44）派生服务——为每用户生成独立的链上充币地址。
 * <p>托管钱包（CEX 自持私钥）：从主助记词（chain.hd-wallet.mnemonic）按
 * BIP44 路径 {@code m/44'/coinType'/0'/0/index} 派生子地址，仅存地址入库，
 * 私钥不落库（主助记词在配置/KMS，用于冷钱包恢复）。</p>
 */
public interface HdWalletService {

    /**
     * 按 BIP44 派生某链某索引的子地址（如 ETH 链 coinType=60，路径 m/44'/60'/0'/0/index）。
     *
     * @param chainCode 链编码（ETH/BSC/POLYGON 等 EVM → coinType 60；BTC → 0；TRON → 195）
     * @param index     派生索引（每链自增，保证地址唯一）
     * @return 地址（EVM 为 0x + 40 hex 小写；BTC/TRON 待对应库实现）
     */
    String deriveAddress(String chainCode, int index);
}
