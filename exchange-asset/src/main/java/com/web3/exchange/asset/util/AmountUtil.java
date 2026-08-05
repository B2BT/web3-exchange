package com.web3.exchange.asset.util;

import java.math.BigDecimal;

/**
 * 金额精度换算工具。
 * 资产域金额统一以「最小单位」整数(long)存储/传输，对外展示/入参按币种 decimals 换算。
 * 业务层禁止使用 double/float 参与金额运算。
 */
public final class AmountUtil {

    private AmountUtil() {
    }

    /**
     * 业务精度(BigDecimal) → 最小单位(long)。
     *
     * @param major    业务精度金额（如 "0.001"）
     * @param decimals 币种精度位数
     * @return 最小单位整数；非整数会向下取整（scale 截断）
     */
    public static long toMinor(BigDecimal major, int decimals) {
        if (major == null) {
            return 0L;
        }
        return major.multiply(BigDecimal.TEN.pow(decimals)).longValue();
    }

    /**
     * 最小单位(long) → 业务精度(BigDecimal)。
     *
     * @param minor    最小单位整数
     * @param decimals 币种精度位数
     * @return 业务精度金额
     */
    public static BigDecimal toMajor(long minor, int decimals) {
        return BigDecimal.valueOf(minor).movePointLeft(decimals);
    }
}
