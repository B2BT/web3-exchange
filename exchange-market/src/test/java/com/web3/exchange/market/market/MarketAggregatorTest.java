package com.web3.exchange.market.market;

import com.web3.exchange.common.order.dto.TradeSettleDTO;
import com.web3.exchange.market.market.model.Kline;
import com.web3.exchange.market.market.model.Ticker;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * MarketAggregator 单元测试——验证 OHLCV 聚合正确性：
 * 单笔开窗(open=high=low=close)、连续多笔 high/low/close 更新、跨窗口滚动开新窗、
 * 同 tradeNo 去重后不重复累计 volume、精度(大数 Long 累加不溢出)。
 */
class MarketAggregatorTest {

    private final MarketAggregator agg = new MarketAggregator();

    private TradeSettleDTO trade(String tradeNo, long price, long quantity, long quoteAmount) {
        TradeSettleDTO dto = new TradeSettleDTO();
        dto.setTradeNo(tradeNo);
        dto.setSymbol("BTC/USDT");
        dto.setBaseCoin("BTC");
        dto.setQuoteCoin("USDT");
        dto.setPrice(price);
        dto.setQuantity(quantity);
        dto.setQuoteAmount(quoteAmount);
        dto.setBuyUserId(1L);
        dto.setSellUserId(2L);
        return dto;
    }

    @Test
    void singleTrade_opensWindow_allEqual() {
        long t = 10 * 60_000L + 45_000L; // 10:00:45 UTC
        agg.onTradeAt(trade("T1", 100L, 1000L, 100000L), t);
        List<Kline> m1 = agg.getKlines("BTC/USDT", "1m", 10);
        assertEquals(1, m1.size());
        Kline k = m1.get(0);
        assertEquals(10 * 60_000L, k.getOpenTime());
        assertEquals(100L, k.getOpen());
        assertEquals(100L, k.getHigh());
        assertEquals(100L, k.getLow());
        assertEquals(100L, k.getClose());
        assertEquals(1000L, k.getVolume());
        assertEquals(100000L, k.getQuoteVolume());
    }

    @Test
    void multipleTrades_sameWindow_updateHLCV() {
        long t = 10 * 60_000L + 45_000L; // 10:00:45
        agg.onTradeAt(trade("T1", 100L, 1000L, 100000L), t);
        agg.onTradeAt(trade("T2", 120L, 500L, 60000L), t + 5_000L);   // high=120
        agg.onTradeAt(trade("T3", 90L, 300L, 27000L), t + 10_000L);   // low=90, close=90
        List<Kline> m1 = agg.getKlines("BTC/USDT", "1m", 10);
        Kline k = m1.get(0);
        assertEquals(100L, k.getOpen());   // open 首笔
        assertEquals(120L, k.getHigh());   // 极值
        assertEquals(90L, k.getLow());     // 极值
        assertEquals(90L, k.getClose());   // 最新一笔
        assertEquals(1800L, k.getVolume());          // 1000+500+300
        assertEquals(187000L, k.getQuoteVolume());   // 100000+60000+27000
    }

    @Test
    void crossWindow_rollsNewWindow() {
        long t1 = 10 * 60_000L + 45_000L; // 10:00:45
        long t2 = 11 * 60_000L + 10_000L; // 11:00:10（下一分钟窗口）
        agg.onTradeAt(trade("T1", 100L, 1000L, 100000L), t1);
        agg.onTradeAt(trade("T2", 120L, 500L, 60000L), t2);
        List<Kline> m1 = agg.getKlines("BTC/USDT", "1m", 10);
        assertEquals(2, m1.size(), "跨窗口应开新窗，旧窗保留");
        // 窗口正序
        assertEquals(10 * 60_000L, m1.get(0).getOpenTime());
        assertEquals(11 * 60_000L, m1.get(1).getOpenTime());
        assertEquals(100L, m1.get(0).getOpen());
        assertEquals(120L, m1.get(1).getOpen());
        // 1d 两个窗口应落在同一日窗口 → 单根 D1，volume 累计
        List<Kline> d1 = agg.getKlines("BTC/USDT", "1d", 10);
        assertEquals(1, d1.size());
        assertEquals(1500L, d1.get(0).getVolume());
    }

    @Test
    void duplicateTradeNo_notDoubleCountVolume() {
        long t = 10 * 60_000L + 45_000L;
        agg.onTradeAt(trade("T1", 100L, 1000L, 100000L), t);
        agg.onTradeAt(trade("T1", 100L, 1000L, 100000L), t + 1000L); // 重复投递/重放
        List<Kline> m1 = agg.getKlines("BTC/USDT", "1m", 10);
        Kline k = m1.get(0);
        assertEquals(1000L, k.getVolume(), "同 tradeNo 去重，volume 不重复累计");
        assertEquals(100000L, k.getQuoteVolume());
    }

    @Test
    void largeNumbers_noOverflow() {
        long t = 10 * 60_000L + 45_000L;
        long bigPrice = 9_000_000_000L;    // 9e9，远超 int 范围
        long bigQty = 123_456_789L;
        long bigQuote = bigPrice * bigQty; // 约 1.1e18，Long 安全
        agg.onTradeAt(trade("T1", bigPrice, bigQty, bigQuote), t);
        agg.onTradeAt(trade("T2", bigPrice, bigQty, bigQuote), t + 1_000L);
        List<Kline> m1 = agg.getKlines("BTC/USDT", "1m", 10);
        Kline k = m1.get(0);
        assertEquals(2L * bigQuote, k.getQuoteVolume());
        assertEquals(2L * bigQty, k.getVolume());
        assertTrue(k.getHigh() >= bigPrice);
    }

    @Test
    void ticker_derivedFromD1() {
        long t = System.currentTimeMillis();
        agg.onTradeAt(trade("T1", 100L, 1000L, 100000L), t);
        agg.onTradeAt(trade("T2", 120L, 500L, 60000L), t + 1_000L);
        Ticker ticker = agg.getTicker("BTC/USDT");
        assertNotNull(ticker);
        assertEquals(120L, ticker.getLastPrice());
        assertEquals(100L, ticker.getOpenPrice());
        assertEquals(120L, ticker.getHigh24h());
        assertEquals(100L, ticker.getLow24h());
        assertEquals(1500L, ticker.getVolume24h());
        assertEquals(160000L, ticker.getQuoteVolume24h());
        // change24h = (120-100)*10000/100 = 2000 bp = 20%
        assertEquals(2000L, ticker.getChange24h());
        // 不存在交易对返回 null
        assertNull(agg.getTicker("ETH/USDT"));
    }

    @Test
    void getKlines_limitDescendingReturn() {
        long base = 10 * 60_000L;
        for (int i = 0; i < 5; i++) {
            agg.onTradeAt(trade("T" + i, 100L, 1L, 100L), base + i * 60_000L);
        }
        List<Kline> limited = agg.getKlines("BTC/USDT", "1m", 3);
        assertEquals(3, limited.size());
        assertEquals(base + 2 * 60_000L, limited.get(0).getOpenTime(), "升序返回最近 N 根");
        assertEquals(base + 4 * 60_000L, limited.get(2).getOpenTime());
    }
}
