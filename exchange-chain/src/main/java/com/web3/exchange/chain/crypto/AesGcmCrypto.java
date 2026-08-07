package com.web3.exchange.chain.crypto;

import com.web3.exchange.common.exception.web3.WalletException;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.PBEKeySpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * 钱包私钥/助记词加密工具（AES-GCM + PBKDF2 密钥派生）。
 * <p>私钥/助记词以 {@code PBKDF2WithHmacSHA256} 从配置 secret 派生 256 位 AES 密钥，
 * 用 AES-GCM（随机 12 字节 IV）加密后 Base64 存库，密文带版本前缀便于将来迁移。
 * GCM 提供认证加密（防篡改），IV 随密文一起存储。</p>
 * <p>安全说明：secret 仅来自配置/环境变量（Mock/测试网），生产须接入 KMS/HSM。</p>
 */
public final class AesGcmCrypto {

    private static final String ALGO = "AES/GCM/NoPadding";
    private static final int KEY_BITS = 256;
    private static final int IV_BYTES = 12;
    private static final int TAG_BITS = 128;
    private static final int ITERATIONS = 310_000;
    private static final byte[] SALT = "web3-wallet-aes-gcm-v1".getBytes(StandardCharsets.UTF_8);
    private static final String PREFIX = "v1:";
    private static final SecureRandom RANDOM = new SecureRandom();

    private AesGcmCrypto() {
    }

    /** 从配置 secret 派生 AES 密钥。 */
    private static SecretKey deriveKey(String secret) {
        try {
            PBEKeySpec spec = new PBEKeySpec(secret.toCharArray(), SALT, ITERATIONS, KEY_BITS);
            SecretKeyFactory f = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
            return new SecretKeySpec(f.generateSecret(spec).getEncoded(), "AES");
        } catch (Exception e) {
            throw new WalletException("密钥派生失败: " + e.getMessage());
        }
    }

    /** 加密明文 → "v1:Base64(iv + 密文)"。 */
    public static String encrypt(String plain, String secret) {
        if (plain == null || plain.isEmpty()) {
            return null;
        }
        try {
            byte[] iv = new byte[IV_BYTES];
            RANDOM.nextBytes(iv);
            Cipher cipher = Cipher.getInstance(ALGO);
            cipher.init(Cipher.ENCRYPT_MODE, deriveKey(secret), new GCMParameterSpec(TAG_BITS, iv));
            byte[] enc = cipher.doFinal(plain.getBytes(StandardCharsets.UTF_8));
            byte[] out = new byte[iv.length + enc.length];
            System.arraycopy(iv, 0, out, 0, iv.length);
            System.arraycopy(enc, 0, out, iv.length, enc.length);
            return PREFIX + Base64.getEncoder().encodeToString(out);
        } catch (Exception e) {
            throw new WalletException("加密失败: " + e.getMessage());
        }
    }

    /** 解密 "v1:Base64(iv + 密文)" → 明文。 */
    public static String decrypt(String cipherText, String secret) {
        if (cipherText == null || cipherText.isEmpty()) {
            return null;
        }
        try {
            String b64 = cipherText.startsWith(PREFIX) ? cipherText.substring(PREFIX.length()) : cipherText;
            byte[] full = Base64.getDecoder().decode(b64);
            byte[] iv = new byte[IV_BYTES];
            System.arraycopy(full, 0, iv, 0, IV_BYTES);
            Cipher cipher = Cipher.getInstance(ALGO);
            cipher.init(Cipher.DECRYPT_MODE, deriveKey(secret), new GCMParameterSpec(TAG_BITS, iv));
            byte[] dec = cipher.doFinal(full, IV_BYTES, full.length - IV_BYTES);
            return new String(dec, StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new WalletException("解密失败: " + e.getMessage());
        }
    }
}
