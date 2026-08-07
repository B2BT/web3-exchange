package com.web3.exchange.chain.crypto;

import com.web3.exchange.common.exception.web3.WalletException;
import org.web3j.crypto.Bip32ECKeyPair;
import org.web3j.crypto.Credentials;
import org.web3j.crypto.Keys;
import org.web3j.crypto.MnemonicUtils;

import java.security.SecureRandom;

/**
 * BIP39/BIP32/BIP44 派生工具（自托管钱包用）。
 * <p>与 HdWalletService 的区别：这里从<b>用户自己的助记词/私钥</b>派生（而非配置主助记词），
 * 用于创建/导入自托管钱包并推导其首地址（BIP44 索引 0）。EVM coinType=60、BTC=0、TRON=195。</p>
 */
public final class Bip44Utils {

    private static final SecureRandom RANDOM = new SecureRandom();

    private Bip44Utils() {
    }

    /** 生成 12 词 BIP39 助记词（128 位熵）。 */
    public static String generateMnemonic() {
        byte[] entropy = new byte[16]; // 128-bit → 12 词
        RANDOM.nextBytes(entropy);
        return MnemonicUtils.generateMnemonic(entropy);
    }

    /** 校验助记词是否为合法 BIP39。 */
    public static boolean validateMnemonic(String mnemonic) {
        return mnemonic != null && MnemonicUtils.validateMnemonic(mnemonic.trim());
    }

    /** 从助记词派生 BIP44 首地址（索引 0）。 */
    public static String deriveAddressFromMnemonic(String mnemonic, String chainCode) {
        byte[] seed = MnemonicUtils.generateSeed(mnemonic.trim(), "");
        Bip32ECKeyPair child = derivePath(seed, chainCode);
        return "0x" + Keys.getAddress(child);
    }

    /** 从助记词派生 BIP44 私钥（索引 0，hex 无 0x）。 */
    public static String derivePrivateKeyFromMnemonic(String mnemonic, String chainCode) {
        byte[] seed = MnemonicUtils.generateSeed(mnemonic.trim(), "");
        Bip32ECKeyPair child = derivePath(seed, chainCode);
        return child.getPrivateKey().toString(16);
    }

    /** 从私钥直接得到地址。 */
    public static String deriveAddressFromPrivateKey(String privateKey) {
        try {
            String pk = privateKey.startsWith("0x") ? privateKey.substring(2) : privateKey;
            Credentials cred = Credentials.create(pk);
            return cred.getAddress();
        } catch (Exception e) {
            throw new WalletException("私钥非法，无法派生地址: " + e.getMessage());
        }
    }

    private static Bip32ECKeyPair derivePath(byte[] seed, String chainCode) {
        Bip32ECKeyPair master = Bip32ECKeyPair.generateKeyPair(seed);
        int coinType = coinTypeOf(chainCode);
        int[] path = {
                44 + Bip32ECKeyPair.HARDENED_BIT,
                coinType + Bip32ECKeyPair.HARDENED_BIT,
                0 + Bip32ECKeyPair.HARDENED_BIT,
                0,
                0
        };
        return Bip32ECKeyPair.deriveKeyPair(master, path);
    }

    /** BIP44 标准 coinType：BTC=0 / ETH/BSC/POLYGON=60 / TRON=195。 */
    public static int coinTypeOf(String chainCode) {
        if (chainCode == null) {
            throw new WalletException("chainCode 不能为空");
        }
        return switch (chainCode.toUpperCase()) {
            case "BTC" -> 0;
            case "TRON", "T" -> 195;
            default -> 60; // EVM 系统一 60
        };
    }
}
