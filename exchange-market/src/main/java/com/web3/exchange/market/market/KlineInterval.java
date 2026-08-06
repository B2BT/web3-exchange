package com.web3.exchange.market.market;

import java.util.EnumSet;
import java.util.Set;

/**
 * K线时间周期枚举（毫秒）。
 * <p>窗口对齐到 UTC 整点边界：{@code openTime = (tradeTimeMs / millis) * millis}。</p>
 */
public enum KlineInterval {
    M1("1m", 60_000L),
    M5("5m", 300_000L),
    M15("15m", 900_000L),
    H1("1h", 3_600_000L),
    H4("4h", 14_400_000L),
    D1("1d", 86_400_000L);

    private final String name;
    private final long millis;

    KlineInterval(String name, long millis) {
        this.name = name;
        this.millis = millis;
    }

    public String intervalName() {
        return name;
    }

    public long millis() {
        return millis;
    }

    /** 启用聚合的全部周期。 */
    public static Set<KlineInterval> enabled() {
        return EnumSet.allOf(KlineInterval.class);
    }

    /** 按周期名（1m/5m/15m/1h/4h/1d）查找，找不到返回 null。 */
    public static KlineInterval fromName(String name) {
        if (name == null) {
            return null;
        }
        String n = name.trim().toLowerCase();
        for (KlineInterval iv : values()) {
            if (iv.name.equals(n)) {
                return iv;
            }
        }
        return null;
    }
}
