package com.web3.exchange.order.util;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * 成交额/冻结额精度换算工具（order 域）。
 * <p>
 * 解决「price × qty 长整型溢出」与「跨币种精度错位」两个根因问题：
 * price 为 <b>price_precision 刻度</b>（t_symbol.price_precision 位小数）的价格，quantity 为
 * <b>amount_precision 刻度</b>（t_symbol.amount_precision 位小数）的数量，二者直接相乘会得到
 * 高达 price_precision+amount_precision 位小数的乘积——既可能溢出 long，也未换算成计价币最小单位。
 * </p>
 * <p>
 * 本工具统一换算为 <b>计价币（quote coin）最小单位 long</b>，全程 BigDecimal（任意精度、溢出安全）：
 * <pre>
 *   notional = (price / 10^pricePrecision) × (quantity / 10^amountPrecision) × 10^quoteDecimals
 * </pre>
 * 最终四舍五入（HALF_UP）到 0 位小数。业务层禁止用 double/float 参与金额运算。
 * </p>
 */
public final class QuoteCalculator {

    private QuoteCalculator() {
    }

    /**
     * 计算名义成交额/冻结额（统一为计价币最小单位 long）。
     *
     * @param price           挂单价（price_precision 刻度，最小单位 long）
     * @param qty             数量（amount_precision 刻度，最小单位 long）
     * @param pricePrecision  价格精度（小数位数，t_symbol.price_precision）
     * @param amountPrecision 数量精度（小数位数，t_symbol.amount_precision）
     * @param quoteDecimals   计价币精度（小数位数，t_coin.decimals，如 USDT=6）
     * @return 名义额（计价币最小单位，long）；结果超出 long 范围时抛 {@link ArithmeticException} 而非静默回绕
     */
    public static long quoteAmount(long price, long qty, int pricePrecision, int amountPrecision, int quoteDecimals) {
        BigDecimal p = BigDecimal.valueOf(price).movePointLeft(pricePrecision);
        BigDecimal q = BigDecimal.valueOf(qty).movePointLeft(amountPrecision);
        return p.multiply(q)
                .movePointRight(quoteDecimals)
                .setScale(0, RoundingMode.HALF_UP)
                .longValueExact();
    }

    /**
     * 精度上下文：封装某交易对/某币种对的 price_precision、amount_precision、quoteDecimals，
     * 供撮合引擎与下单冻结在同一精度约定下复用（保证买卖双方累计 quoteAmount 用同一算法）。
     */
    public static final class PrecisionContext {
        private final int pricePrecision;
        private final int amountPrecision;
        private final int quoteDecimals;

        public PrecisionContext(int pricePrecision, int amountPrecision, int quoteDecimals) {
            this.pricePrecision = pricePrecision;
            this.amountPrecision = amountPrecision;
            this.quoteDecimals = quoteDecimals;
        }

        /**
         * 在本上下文精度下计算名义额（计价币最小单位）。
         */
        public long quoteAmount(long price, long qty) {
            return QuoteCalculator.quoteAmount(price, qty, pricePrecision, amountPrecision, quoteDecimals);
        }

        /** 全 0 精度上下文：quoteAmount = price × qty（撮合单测/无精度场景兜底）。 */
        public static final PrecisionContext ZERO = new PrecisionContext(0, 0, 0);
    }
}
