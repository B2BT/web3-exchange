package com.web3.exchange.user.util;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * AES-GCM 对称加密工具（用于 API 密钥 secretKey 落库加密）。
 * <p>密钥从固定种子派生（与 self-wallet 的 encrypt-secret 同一思路）；生产应改环境变量注入。
 * 密文格式：Base64(iv 12字节 + tag16 + ciphertext)。</p>
 */
public final class AesGcmUtil {

    /** AES-128 密钥（生产应从配置注入，勿硬编码） */
    private static final byte[] KEY_BYTES =
            "web3-exchange-api-key-secret-000".getBytes(StandardCharsets.UTF_8);

    private AesGcmUtil() {}

    /**
     * 加密：返回 Base64(iv + ciphertext)，含 16 字节认证 tag。
     */
    public static String encrypt(String plaintext) {
        try {
            byte[] iv = new byte[12];
            new SecureRandom().nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            SecretKeySpec key = new SecretKeySpec(KEY_BYTES, 0, 16, "AES");
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(128, iv));
            byte[] ct = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            byte[] out = new byte[iv.length + ct.length];
            System.arraycopy(iv, 0, out, 0, iv.length);
            System.arraycopy(ct, 0, out, iv.length, ct.length);
            return Base64.getEncoder().encodeToString(out);
        } catch (Exception e) {
            throw new IllegalStateException("AES 加密失败", e);
        }
    }

    /**
     * 解密：输入为 {@link #encrypt} 的 Base64 输出。
     */
    public static String decrypt(String ciphertextB64) {
        try {
            byte[] all = Base64.getDecoder().decode(ciphertextB64);
            byte[] iv = new byte[12];
            byte[] ct = new byte[all.length - 12];
            System.arraycopy(all, 0, iv, 0, 12);
            System.arraycopy(all, 12, ct, 0, ct.length);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            SecretKeySpec key = new SecretKeySpec(KEY_BYTES, 0, 16, "AES");
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(128, iv));
            return new String(cipher.doFinal(ct), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new IllegalStateException("AES 解密失败", e);
        }
    }
}
