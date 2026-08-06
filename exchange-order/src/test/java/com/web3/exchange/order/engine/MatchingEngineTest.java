package com.web3.exchange.order.engine;

import com.web3.exchange.order.constant.OrderConstant;
import com.web3.exchange.order.entity.Order;
import com.web3.exchange.order.entity.Trade;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 撮合引擎正确性单测（docs/order-domain.md §7）：价格优先 / 时间优先 / 量价守恒 / 限价不可穿透 / 市价行为。
 * 撮合引擎为纯类，不依赖 DB/Spring，直接 JUnit 断言。
 */
class MatchingEngineTest {

    private final MatchingEngine engine = new MatchingEngine();

    private Order order(long id, int side, int type, long price, long qty, long remaining, int seq) {
        Order o = new Order();
        o.setId(id);
        o.setOrderNo("O" + id);
        o.setUserId(1L);
        o.setSymbol("BTC/USDT");
        o.setBaseCoin("BTC");
        o.setQuoteCoin("USDT");
        o.setSide(side);
        o.setOrderType(type);
        o.setPrice(price);
        o.setQuantity(qty);
        o.setRemaining(remaining);
        o.setFilledAmount(0L);
        o.setFilledQuoteAmount(0L);
        o.setAvgPrice(0L);
        o.setTradeCount(0);
        o.setCreateTime(LocalDateTime.of(2026, 1, 1, 0, 0).plusSeconds(seq));
        return o;
    }

    @Test
    void limitBuy_pricePriority_bestAskFirst() {
        // 簿内卖盘 102/101/99，限价买 @100 → 只与 @99 成交（最优卖价；102/101 不可穿透）
        engine.match(order(1, OrderConstant.SIDE_SELL, OrderConstant.TYPE_LIMIT, 102, 5, 5, 1));
        engine.match(order(2, OrderConstant.SIDE_SELL, OrderConstant.TYPE_LIMIT, 101, 5, 5, 2));
        engine.match(order(3, OrderConstant.SIDE_SELL, OrderConstant.TYPE_LIMIT, 99, 5, 5, 3));

        Order buy = order(10, OrderConstant.SIDE_BUY, OrderConstant.TYPE_LIMIT, 100, 3, 3, 4);
        MatchingEngine.MatchResult r = engine.match(buy);

        assertEquals(1, r.trades.size());
        assertEquals(99L, r.trades.get(0).getPrice(), "必须先与最低卖价 @99 成交");
        assertEquals(3L, r.trades.get(0).getQuantity());
    }

    @Test
    void limitSell_pricePriority_bestBidFirst() {
        // 簿内买盘 8/9/11，限价卖 @10 → 先与 @11 成交（最优买价；8/9 不可穿透）
        engine.match(order(1, OrderConstant.SIDE_BUY, OrderConstant.TYPE_LIMIT, 8, 5, 5, 1));
        engine.match(order(2, OrderConstant.SIDE_BUY, OrderConstant.TYPE_LIMIT, 9, 5, 5, 2));
        engine.match(order(3, OrderConstant.SIDE_BUY, OrderConstant.TYPE_LIMIT, 11, 5, 5, 3));

        Order sell = order(10, OrderConstant.SIDE_SELL, OrderConstant.TYPE_LIMIT, 10, 4, 4, 4);
        MatchingEngine.MatchResult r = engine.match(sell);

        assertEquals(1, r.trades.size());
        assertEquals(11L, r.trades.get(0).getPrice(), "必须先与最高买价 @11 成交");
    }

    @Test
    void timePriority_samePriceFifo() {
        // 簿内两个卖单 @100（A 先 5、B 后 5），买 @100 x7 → 先吃满 A 的 5，再吃 B 的 2
        engine.match(order(1, OrderConstant.SIDE_SELL, OrderConstant.TYPE_LIMIT, 100, 5, 5, 1));
        engine.match(order(2, OrderConstant.SIDE_SELL, OrderConstant.TYPE_LIMIT, 100, 5, 5, 2));

        Order buy = order(10, OrderConstant.SIDE_BUY, OrderConstant.TYPE_LIMIT, 100, 7, 7, 3);
        MatchingEngine.MatchResult r = engine.match(buy);

        assertEquals(2, r.trades.size());
        assertEquals("O1", r.trades.get(0).getMakerOrderNo(), "先挂单者 A 优先成交");
        assertEquals(5L, r.trades.get(0).getQuantity());
        assertEquals("O2", r.trades.get(1).getMakerOrderNo());
        assertEquals(2L, r.trades.get(1).getQuantity());
    }

    @Test
    void conservation_quantityAndQuote() {
        // 量价守恒：qty==min(takerRemaining,makerRemaining)；quote==price*qty
        engine.match(order(1, OrderConstant.SIDE_SELL, OrderConstant.TYPE_LIMIT, 100, 5, 5, 1));
        Order buy = order(10, OrderConstant.SIDE_BUY, OrderConstant.TYPE_LIMIT, 100, 3, 3, 2);
        MatchingEngine.MatchResult r = engine.match(buy);
        assertEquals(1, r.trades.size());
        Trade t = r.trades.get(0);
        assertEquals(3L, t.getQuantity(), "qty==min(3,5)");
        assertEquals(100L * 3, t.getQuoteAmount(), "quote==price*qty");
        assertEquals(0L, buy.getRemaining(), "吃单剩余归零 → FILLED");
        assertEquals(OrderConstant.STATUS_FILLED, buy.getStatus());
    }

    @Test
    void limit_noPierce() {
        // 买 @100 绝不与 @101 卖成交；卖 @10 绝不与 @9 买成交
        engine.match(order(1, OrderConstant.SIDE_SELL, OrderConstant.TYPE_LIMIT, 101, 5, 5, 1));
        Order buy = order(10, OrderConstant.SIDE_BUY, OrderConstant.TYPE_LIMIT, 100, 2, 2, 2);
        MatchingEngine.MatchResult r = engine.match(buy);
        assertTrue(r.trades.isEmpty(), "限价买 @100 不能穿透 @101 卖单");
        assertEquals(OrderConstant.STATUS_NEW, buy.getStatus(), "未成交限价单入簿保持 NEW");

        // 独立引擎，避免上一子用例的挂单残留
        MatchingEngine engine2 = new MatchingEngine();
        engine2.match(order(2, OrderConstant.SIDE_BUY, OrderConstant.TYPE_LIMIT, 9, 5, 5, 3));
        Order sell = order(11, OrderConstant.SIDE_SELL, OrderConstant.TYPE_LIMIT, 10, 2, 2, 4);
        MatchingEngine.MatchResult r2 = engine2.match(sell);
        assertTrue(r2.trades.isEmpty(), "限价卖 @10 不能穿透 @9 买单");
    }

    @Test
    void marketBuy_budgetExhausted() {
        // 预算 1000，簿卖 @100 x6 → 成交 6（花 600），余 400 不再成交
        engine.match(order(1, OrderConstant.SIDE_SELL, OrderConstant.TYPE_LIMIT, 100, 6, 6, 1));
        Order buy = order(10, OrderConstant.SIDE_BUY, OrderConstant.TYPE_MARKET, 0, 0, 0, 2);
        buy.setQuoteAmount(1000L);
        MatchingEngine.MatchResult r = engine.match(buy);
        assertEquals(1, r.trades.size());
        assertEquals(6L, r.trades.get(0).getQuantity());
        assertEquals(600L, r.trades.get(0).getQuoteAmount());
        assertEquals(400L, buy.getQuoteAmount(), "剩余预算 400 不再成交");
    }

    @Test
    void marketSell_quantityExhausted() {
        // 数量 5，簿买 @10 x3 + @9 x4 → 成交 3(@10)+2(@9)=5 停止，不超卖
        engine.match(order(1, OrderConstant.SIDE_BUY, OrderConstant.TYPE_LIMIT, 10, 3, 3, 1));
        engine.match(order(2, OrderConstant.SIDE_BUY, OrderConstant.TYPE_LIMIT, 9, 4, 4, 2));
        Order sell = order(10, OrderConstant.SIDE_SELL, OrderConstant.TYPE_MARKET, 0, 5, 5, 3);
        MatchingEngine.MatchResult r = engine.match(sell);
        assertEquals(2, r.trades.size());
        assertEquals(10L, r.trades.get(0).getPrice());
        assertEquals(3L, r.trades.get(0).getQuantity());
        assertEquals(9L, r.trades.get(1).getPrice());
        assertEquals(2L, r.trades.get(1).getQuantity());
        assertEquals(0L, sell.getRemaining(), "不超卖");
    }

    @Test
    void partialFill_remainingRestInBook() {
        // 卖 @100 x5 与买 @100 x2 → 卖单部分成交 remaining=3 留在簿内，可被后续单继续撮合
        engine.match(order(1, OrderConstant.SIDE_SELL, OrderConstant.TYPE_LIMIT, 100, 5, 5, 1));
        Order buy1 = order(10, OrderConstant.SIDE_BUY, OrderConstant.TYPE_LIMIT, 100, 2, 2, 2);
        engine.match(buy1);
        assertEquals(OrderConstant.STATUS_FILLED, buy1.getStatus());

        // 后续再买 4 → 吃满剩余 3，卖单 FILLED
        Order buy2 = order(11, OrderConstant.SIDE_BUY, OrderConstant.TYPE_LIMIT, 100, 4, 4, 3);
        MatchingEngine.MatchResult r = engine.match(buy2);
        assertEquals(1, r.trades.size());
        assertEquals(3L, r.trades.get(0).getQuantity());
        assertEquals(OrderConstant.STATUS_PARTIAL, buy2.getStatus(), "再买4只成交3 → PARTIAL");
    }

    // ---------- 进阶订单类型撮合策略（docs/advanced-orders.md §二）：PostOnly / IOC / FOK ----------

    @Test
    void postOnly_crossPrice_rejected() {
        // 簿内卖 @100，PostOnly 限价买 @100 → 会立即成交 → 整单拒绝（不成交、不挂单）
        engine.match(order(1, OrderConstant.SIDE_SELL, OrderConstant.TYPE_LIMIT, 100, 5, 5, 1));
        Order buy = order(10, OrderConstant.SIDE_BUY, OrderConstant.TYPE_LIMIT, 100, 3, 3, 2);
        buy.setTimeInForce(OrderConstant.TIF_POST_ONLY);
        MatchingEngine.MatchResult r = engine.match(buy);
        assertTrue(r.trades.isEmpty(), "PostOnly 交叉价不成交");
        assertEquals(OrderConstant.STATUS_REJECTED, buy.getStatus());
        assertEquals("PostOnly订单可能立即成交", buy.getRemark());
        assertEquals(1, engine.orderCount("BTC/USDT"), "拒绝单不挂簿，盘口仅剩原卖单");
    }

    @Test
    void postOnly_noCross_restsInBook() {
        // 簿内卖 @101，PostOnly 限价买 @100 → 不交叉 → 作为 maker 入簿
        engine.match(order(1, OrderConstant.SIDE_SELL, OrderConstant.TYPE_LIMIT, 101, 5, 5, 1));
        Order buy = order(10, OrderConstant.SIDE_BUY, OrderConstant.TYPE_LIMIT, 100, 3, 3, 2);
        buy.setTimeInForce(OrderConstant.TIF_POST_ONLY);
        MatchingEngine.MatchResult r = engine.match(buy);
        assertTrue(r.trades.isEmpty(), "PostOnly 不交叉不成交");
        assertEquals(OrderConstant.STATUS_NEW, buy.getStatus());
        assertEquals(2, engine.orderCount("BTC/USDT"), "PostOnly 不交叉单挂簿");
    }

    @Test
    void ioc_partialFill_remainderVoided() {
        // 簿内卖 @100 x5，IOC 限价买 @100 x7 → 成交5，剩余2作废不挂单
        engine.match(order(1, OrderConstant.SIDE_SELL, OrderConstant.TYPE_LIMIT, 100, 5, 5, 1));
        Order buy = order(10, OrderConstant.SIDE_BUY, OrderConstant.TYPE_LIMIT, 100, 7, 7, 2);
        buy.setTimeInForce(OrderConstant.TIF_IOC);
        MatchingEngine.MatchResult r = engine.match(buy);
        assertEquals(1, r.trades.size());
        assertEquals(5L, r.trades.get(0).getQuantity());
        assertEquals(2L, buy.getRemaining(), "IOC 剩余保留但作废");
        assertEquals(OrderConstant.STATUS_CANCELLED, buy.getStatus(), "IOC 部分成交剩余作废→终态取消");
        assertEquals(0, engine.orderCount("BTC/USDT"), "IOC 剩余不挂簿(卖单已满成)");
    }

    @Test
    void ioc_noFill_cancelledWhole() {
        // 簿内卖 @101，IOC 限价买 @100 x3 → 无成交 → 整单取消（不挂簿）
        engine.match(order(1, OrderConstant.SIDE_SELL, OrderConstant.TYPE_LIMIT, 101, 5, 5, 1));
        Order buy = order(10, OrderConstant.SIDE_BUY, OrderConstant.TYPE_LIMIT, 100, 3, 3, 2);
        buy.setTimeInForce(OrderConstant.TIF_IOC);
        MatchingEngine.MatchResult r = engine.match(buy);
        assertTrue(r.trades.isEmpty());
        assertEquals(OrderConstant.STATUS_CANCELLED, buy.getStatus(), "IOC 未成交剩余作废→取消");
        assertEquals(1, engine.orderCount("BTC/USDT"), "IOC 不挂簿，仅剩原卖单");
    }

    @Test
    void fok_cannotFullyFill_cancelledWhole() {
        // 簿内卖 @100 x5，FOK 限价买 @100 x7 → 盘口不足 → 整单取消（不成交、不挂单）
        engine.match(order(1, OrderConstant.SIDE_SELL, OrderConstant.TYPE_LIMIT, 100, 5, 5, 1));
        Order buy = order(10, OrderConstant.SIDE_BUY, OrderConstant.TYPE_LIMIT, 100, 7, 7, 2);
        buy.setTimeInForce(OrderConstant.TIF_FOK);
        MatchingEngine.MatchResult r = engine.match(buy);
        assertTrue(r.trades.isEmpty(), "FOK 不足整单不成交");
        assertEquals(OrderConstant.STATUS_CANCELLED, buy.getStatus());
        assertEquals(1, engine.orderCount("BTC/USDT"), "FOK 取消不挂簿，仅剩原卖单");
    }

    @Test
    void fok_fullyFillable_fillsAll() {
        // 簿内卖 @100 x5，FOK 限价买 @100 x5 → 恰好全成交 → FILLED
        engine.match(order(1, OrderConstant.SIDE_SELL, OrderConstant.TYPE_LIMIT, 100, 5, 5, 1));
        Order buy = order(10, OrderConstant.SIDE_BUY, OrderConstant.TYPE_LIMIT, 100, 5, 5, 2);
        buy.setTimeInForce(OrderConstant.TIF_FOK);
        MatchingEngine.MatchResult r = engine.match(buy);
        assertEquals(1, r.trades.size());
        assertEquals(5L, r.trades.get(0).getQuantity());
        assertEquals(0L, buy.getRemaining());
        assertEquals(OrderConstant.STATUS_FILLED, buy.getStatus());
    }
}
