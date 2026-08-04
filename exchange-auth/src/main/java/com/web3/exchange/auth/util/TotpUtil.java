package com.web3.exchange.auth.util;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.ByteArrayOutputStream;

/**
 * RFC 6238 TOTP（基于时间的一次性密码）实现，与 Google Authenticator 兼容。
 * <p>
 * 算法：HMAC-SHA1，时间步长 30 秒，6 位数字，校验时允许 ±1 个时间窗口容差。
 * base32 解码为手写实现，不引入额外依赖。
 * </p>
 */
public final class TotpUtil {

    private static final String BASE32_ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567";
    private static final String HMAC_ALGORITHM = "HmacSHA1";
    private static final long TIME_STEP_SECONDS = 30;
    private static final int CODE_DIGITS = 6;

    private TotpUtil() {
    }

    /**
     * 校验用户输入的 TOTP 码（使用当前时间，允许 ±1 窗口容差）。
     *
     * @param base32Secret base32 编码的密钥
     * @param code         用户输入的 6 位码
     * @return 是否有效
     */
    public static boolean verify(String base32Secret, String code) {
        if (base32Secret == null || base32Secret.isBlank()
                || code == null || code.isBlank()) {
            return false;
        }
        byte[] secret;
        try {
            secret = base32Decode(base32Secret);
        } catch (Exception e) {
            return false;
        }
        if (secret.length == 0) {
            return false;
        }

        long timeBucket = System.currentTimeMillis() / 1000L / TIME_STEP_SECONDS;
        String input = code.trim();
        for (int offset = -1; offset <= 1; offset++) {
            if (constantTimeEquals(generateForBucket(secret, timeBucket + offset), input)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 生成指定时刻的 TOTP 码（用于测试）。
     *
     * @param base32Secret base32 编码的密钥
     * @param timeMillis   毫秒时间戳
     * @return 6 位数字码
     */
    public static String generate(String base32Secret, long timeMillis) {
        long timeBucket = timeMillis / 1000L / TIME_STEP_SECONDS;
        return generateForBucket(base32Decode(base32Secret), timeBucket);
    }

    private static String generateForBucket(byte[] secret, long timeBucket) {
        // 计数器：8 字节大端序
        byte[] counter = new byte[8];
        long value = timeBucket;
        for (int i = 7; i >= 0; i--) {
            counter[i] = (byte) (value & 0xff);
            value >>>= 8;
        }

        byte[] hash = hmacSha1(secret, counter);
        int offset = hash[hash.length - 1] & 0x0f;
        int binary = ((hash[offset] & 0x7f) << 24)
                | ((hash[offset + 1] & 0xff) << 16)
                | ((hash[offset + 2] & 0xff) << 8)
                | (hash[offset + 3] & 0xff);
        int otp = binary % (int) Math.pow(10, CODE_DIGITS);
        return String.format("%0" + CODE_DIGITS + "d", otp);
    }

    private static byte[] hmacSha1(byte[] key, byte[] data) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALGORITHM);
            mac.init(new SecretKeySpec(key, HMAC_ALGORITHM));
            return mac.doFinal(data);
        } catch (Exception e) {
            throw new IllegalStateException("HMAC-SHA1 计算失败", e);
        }
    }

    /**
     * base32 解码（无填充，忽略非法字符与空格）。
     */
    private static byte[] base32Decode(String base32) {
        String cleaned = base32.replace("=", "").replace(" ", "").toUpperCase();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        int buffer = 0;
        int bitsLeft = 0;
        for (int i = 0; i < cleaned.length(); i++) {
            int idx = BASE32_ALPHABET.indexOf(cleaned.charAt(i));
            if (idx < 0) {
                continue;
            }
            buffer = (buffer << 5) | idx;
            bitsLeft += 5;
            if (bitsLeft >= 8) {
                out.write((buffer >> (bitsLeft - 8)) & 0xff);
                bitsLeft -= 8;
            }
        }
        return out.toByteArray();
    }

    private static boolean constantTimeEquals(String a, String b) {
        if (a.length() != b.length()) {
            return false;
        }
        int result = 0;
        for (int i = 0; i < a.length(); i++) {
            result |= a.charAt(i) ^ b.charAt(i);
        }
        return result == 0;
    }
}
