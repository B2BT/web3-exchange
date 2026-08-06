package com.web3.exchange.order.util;

import com.web3.exchange.order.util.QuoteCalculator.PrecisionContext;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * {@link QuoteCalculator} 精度换算单测：溢出安全 + 精确性 + 四舍五入 + 跨币种精度。
 * <p>
 * 换算公式（统一计价币最小单位 long）：
 * notional = (price / 10^pricePrecision) × (qty / 10^amountPrecision) × 10^quoteDecimals
 */
class QuoteCalculatorTest {

    @Test
    void largeOrder_noLongOverflow_precise() {
        // price=10^12 (price_precision=8 → 10^12/10^8 = 10000 USDT)，
        // qty=10^8 (amount_precision=8 → 1 BTC)，
        // 名义额 = 10000 USDT，换算成计价币(USDT=6)最小单位 = 10000×10^6 = 10^10。
        // 旧实现 price×qty=10^20 > Long.MAX(9.2×10^18) 溢出；此处精确为 10^10，不溢出。
        long qa = QuoteCalculator.quoteAmount(1_000_000_000_000L, 100_000_000L, 8, 8, 6);
        assertEquals(10_000_000_000L, qa, "10000 USDT 的 USDT 最小单位，精确不溢出");
    }

    @Test
    void roundHalfUp() {
        // (1234.5678) × (1.0) × 100 = 123456.78 → HALF_UP → 123457
        long qa = QuoteCalculator.quoteAmount(12_345_678L, 100L, 4, 2, 2);
        assertEquals(123_457L, qa);
    }

    @Test
    void zeroContext_isPriceTimesQty() {
        // 全 0 精度上下文：quoteAmount = price × qty（与撮合单测口径一致）
        long qa = QuoteCalculator.quoteAmount(100L, 3L, 0, 0, 0);
        assertEquals(300L, qa);
    }

    @Test
    void crossQuoteDecimals() {
        // ETH/USDT：base ETH 与 quote USDT decimals 不同；amount_precision 决定数量刻度
        // (10^10/10^8=100 USDT) × (10^9/10^8=10 ETH) = 1000 USDT → USDT 最小单位 = 1000×10^6 = 10^9
        long qa = QuoteCalculator.quoteAmount(10_000_000_000L, 1_000_000_000L, 8, 8, 6);
        assertEquals(1_000_000_000L, qa);
    }

    @Test
    void subMinUnit_roundsToZero() {
        // 名义额 < 1 计价币最小单位 → 四舍五入为 0
        long qa = QuoteCalculator.quoteAmount(1L, 1L, 8, 8, 6);
        assertEquals(0L, qa);
    }

    @Test
    void finalOverflow_failsLoudly() {
        // 最终结果超出 long 范围：抛 ArithmeticException，而非静默回绕
        assertThrows(ArithmeticException.class,
                () -> QuoteCalculator.quoteAmount(Long.MAX_VALUE, 2L, 0, 0, 0));
    }

    @Test
    void precisionContext_consistent() {
        PrecisionContext ctx = new PrecisionContext(8, 8, 6);
        assertEquals(10_000_000_000L, ctx.quoteAmount(1_000_000_000_000L, 100_000_000L));
    }
}
