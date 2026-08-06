package com.web3.exchange.order.engine;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.web3.exchange.order.constant.OrderConstant;
import com.web3.exchange.order.entity.Order;
import com.web3.exchange.order.entity.Trade;
import com.web3.exchange.order.util.QuoteCalculator.PrecisionContext;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

/**
 * 单机内存撮合引擎。
 * <p>
 * 容器为 {@link ConcurrentHashMap#symbol -> OrderBook}，同一交易对用独立 {@link ReentrantLock} 串行化
 * （striped lock），保证同一交易对的撮合严格有序、天然满足价格优先 + 时间优先；不同交易对互不阻塞。
 * 撮合核心 {@link #doMatch} 为<b>纯逻辑</b>，不依赖 DB，可独立单测。
 * </p>
 * <p>
 * 撮合规则（docs/order-domain.md §5.3）：
 * 买盘取最高价、卖盘取最低价（价格优先，TreeMap 有序保证）；同价 FIFO（时间优先）；
 * <b>成交价 = 挂单（maker）的挂单价</b>；市价买单按 quote_amount 预算、市价卖单按 quantity 撮合。
 * </p>
 */
@Component
public class MatchingEngine {

    private final ConcurrentHashMap<String, OrderBook> books = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, ReentrantLock> locks = new ConcurrentHashMap<>();

    /** 某一交易对撮合后：生成的成交 + 被改动、需落库更新的挂单。 */
    public static class MatchResult {
        public final List<Trade> trades = new ArrayList<>();
        /** 被成交改动 remaining/status 的挂单（其 DB 行需同步更新） */
        public final Set<Order> changedMakers = new LinkedHashSet<>();

        public boolean hasTrades() {
            return !trades.isEmpty();
        }
    }

    /**
     * 撤单：从内存订单簿移除某订单（同一交易对锁串行化）。
     *
     * @return 是否在簿中移除成功
     */
    public boolean removeBookOrder(Order order) {
        ReentrantLock lock = locks.computeIfAbsent(order.getSymbol(), k -> new ReentrantLock());
        lock.lock();
        try {
            OrderBook book = books.get(order.getSymbol());
            if (book == null) {
                return false;
            }
            return book.removeById(order.getSide(), order.getPrice(), order.getId());
        } finally {
            lock.unlock();
        }
    }

    /**
     * 启动恢复：把持久化的活跃限价单直接入簿（不重复冻结/过户，靠 asset 幂等兜底）。
     */
    public void addRestingOrder(Order o) {
        ReentrantLock lock = locks.computeIfAbsent(o.getSymbol(), k -> new ReentrantLock());
        lock.lock();
        try {
            OrderBook book = books.computeIfAbsent(o.getSymbol(), k -> new OrderBook());
            book.add(o);
        } finally {
            lock.unlock();
        }
    }

    /** 某交易对最优买价（盘口空返回 null）。 */
    public Long bestBidPrice(String symbol) {
        return withBook(symbol, OrderBook::bestBid, o -> o == null ? null : o.getPrice());
    }

    /** 某交易对最优卖价（盘口空返回 null）。 */
    public Long bestAskPrice(String symbol) {
        return withBook(symbol, OrderBook::bestAsk, o -> o == null ? null : o.getPrice());
    }

    /** 某交易对盘口挂单总数。 */
    public int orderCount(String symbol) {
        ReentrantLock lock = locks.computeIfAbsent(symbol, k -> new ReentrantLock());
        lock.lock();
        try {
            OrderBook book = books.get(symbol);
            return book == null ? 0 : book.size();
        } finally {
            lock.unlock();
        }
    }

    private Long withBook(String symbol, java.util.function.Function<OrderBook, Order> pick,
                          java.util.function.Function<Order, Long> map) {
        ReentrantLock lock = locks.computeIfAbsent(symbol, k -> new ReentrantLock());
        lock.lock();
        try {
            OrderBook book = books.get(symbol);
            if (book == null) {
                return null;
            }
            return map.apply(pick.apply(book));
        } finally {
            lock.unlock();
        }
    }

    /**
     * 撮合新订单（入口，先按交易对加锁串行化）。等价于 {@link #match(Order, PrecisionContext)} 传全 0 精度上下文
     * （quoteAmount = price × qty），供单测/无精度场景使用。
     *
     * @param taker 新下单（含已落库的 id/orderNo/createTime）
     * @return 撮合结果（成交列表 + 需更新的挂单）
     */
    public MatchResult match(Order taker) {
        return match(taker, PrecisionContext.ZERO);
    }

    /**
     * 撮合新订单（入口，先按交易对加锁串行化）。
     *
     * @param taker 新下单（含已落库的 id/orderNo/createTime）
     * @param ctx   该交易对的精度上下文（price_precision/amount_precision/quoteDecimals），
     *              撮合与冻结必须用同一上下文以保证买卖双方 quoteAmount 算法一致
     * @return 撮合结果（成交列表 + 需更新的挂单）
     */
    public MatchResult match(Order taker, PrecisionContext ctx) {
        ReentrantLock lock = locks.computeIfAbsent(taker.getSymbol(), k -> new ReentrantLock());
        lock.lock();
        try {
            OrderBook book = books.computeIfAbsent(taker.getSymbol(), k -> new OrderBook());
            return doMatch(book, taker, ctx);
        } finally {
            lock.unlock();
        }
    }

    /**
     * 撮合核心（纯逻辑，不依赖 DB；调用方须已持有本交易对的锁）。
     */
    private MatchResult doMatch(OrderBook book, Order taker, PrecisionContext ctx) {
        MatchResult result = new MatchResult();
        boolean isBuy = taker.getSide() == OrderConstant.SIDE_BUY;

        if (isBuy) {
            if (taker.getOrderType() == OrderConstant.TYPE_LIMIT) {
                // 限价买单：持续吃 价格<=N.price 的最优卖单
                while (taker.getRemaining() > 0) {
                    Order maker = book.bestAsk();
                    if (maker == null || maker.getPrice() > taker.getPrice()) {
                        break; // 价格不可穿透
                    }
                    long qty = Math.min(taker.getRemaining(), maker.getRemaining());
                    fill(taker, maker, qty, result, ctx);
                    if (maker.getRemaining() == 0) {
                        book.remove(maker);
                    }
                }
            } else {
                // 市价买单：按 quote_amount 预算逐档吃掉
                while (taker.getQuoteAmount() > 0) {
                    Order maker = book.bestAsk();
                    if (maker == null) {
                        break;
                    }
                    // 预算能买的最大数量：1 个基础币最小单位的名义额 = ctx.quoteAmount(price, 1)
                    long unitCost = ctx.quoteAmount(maker.getPrice(), 1L);
                    if (unitCost <= 0) {
                        break; // 1 个最小单位的预算都不够，无法撮合
                    }
                    long maxQty = taker.getQuoteAmount() / unitCost;
                    if (maxQty <= 0) {
                        break; // 预算不足以买 1 个最小单位
                    }
                    long qty = Math.min(maxQty, maker.getRemaining());
                    long spent = ctx.quoteAmount(maker.getPrice(), qty);
                    // 四舍五入兜底：若 round 后 spent 越过预算，回退到不超过预算的最大数量
                    while (spent > taker.getQuoteAmount() && qty > 0) {
                        qty--;
                        spent = ctx.quoteAmount(maker.getPrice(), qty);
                    }
                    if (qty == 0) {
                        break;
                    }
                    fill(taker, maker, qty, result, ctx);
                    taker.setQuoteAmount(taker.getQuoteAmount() - spent); // 扣减市价买单预算
                    if (maker.getRemaining() == 0) {
                        book.remove(maker);
                    }
                }
            }
        } else {
            if (taker.getOrderType() == OrderConstant.TYPE_LIMIT) {
                // 限价卖单：持续吃 价格>=N.price 的最优买单
                while (taker.getRemaining() > 0) {
                    Order maker = book.bestBid();
                    if (maker == null || maker.getPrice() < taker.getPrice()) {
                        break; // 价格不可穿透
                    }
                    long qty = Math.min(taker.getRemaining(), maker.getRemaining());
                    fill(taker, maker, qty, result, ctx);
                    if (maker.getRemaining() == 0) {
                        book.remove(maker);
                    }
                }
            } else {
                // 市价卖单：按 quantity 逐档吃掉
                while (taker.getRemaining() > 0) {
                    Order maker = book.bestBid();
                    if (maker == null) {
                        break;
                    }
                    long qty = Math.min(taker.getRemaining(), maker.getRemaining());
                    fill(taker, maker, qty, result, ctx);
                    if (maker.getRemaining() == 0) {
                        book.remove(maker);
                    }
                }
            }
        }

        // 吃单最终状态：remaining==0 → FILLED；限价剩余>0 → 入簿（已成交>0 则 PARTIAL）；市价剩余不落簿
        if (taker.getRemaining() == 0) {
            taker.setStatus(OrderConstant.STATUS_FILLED);
            taker.setFilledTime(java.time.LocalDateTime.now());
        } else if (taker.getOrderType() == OrderConstant.TYPE_LIMIT) {
            taker.setStatus(taker.getFilledAmount() > 0
                    ? OrderConstant.STATUS_PARTIAL : OrderConstant.STATUS_NEW);
            book.add(taker); // 剩余挂单入簿
        } else {
            // 市价单未完全成交：成交多少算多少，剩余作废不落簿
            taker.setStatus(taker.getFilledAmount() > 0
                    ? OrderConstant.STATUS_PARTIAL : OrderConstant.STATUS_NEW);
        }

        return result;
    }

    /**
     * 单笔撮合：按挂单价成交，更新双方 remaining/filled/avg/trade_count，生成 Trade。
     * 成交名义额用 {@link PrecisionContext#quoteAmount} 精确换算（计价币最小单位，溢出安全）。
     */
    private void fill(Order taker, Order maker, long qty, MatchResult result, PrecisionContext ctx) {
        long price = maker.getPrice(); // 成交价 = 挂单（maker）挂单价
        long quoteAmount = ctx.quoteAmount(price, qty);

        // 挂单方（maker）
        maker.setRemaining(maker.getRemaining() - qty);
        maker.setFilledAmount(maker.getFilledAmount() + qty);
        maker.setFilledQuoteAmount(maker.getFilledQuoteAmount() + quoteAmount);
        maker.setAvgPrice(maker.getFilledAmount() == 0 ? 0 : maker.getFilledQuoteAmount() / maker.getFilledAmount());
        maker.setTradeCount(maker.getTradeCount() + 1);
        maker.setStatus(maker.getRemaining() == 0 ? OrderConstant.STATUS_FILLED : OrderConstant.STATUS_PARTIAL);
        if (maker.getRemaining() == 0) {
            maker.setFilledTime(java.time.LocalDateTime.now());
        }

        // 吃单方（taker）
        taker.setRemaining(taker.getRemaining() - qty);
        taker.setFilledAmount(taker.getFilledAmount() + qty);
        taker.setFilledQuoteAmount(taker.getFilledQuoteAmount() + quoteAmount);
        taker.setAvgPrice(taker.getFilledAmount() == 0 ? 0 : taker.getFilledQuoteAmount() / taker.getFilledAmount());
        taker.setTradeCount(taker.getTradeCount() + 1);

        // 生成成交
        Trade t = new Trade();
        t.setTradeNo(IdWorker.getIdStr());
        t.setSymbol(taker.getSymbol());
        t.setPrice(price);
        t.setQuantity(qty);
        t.setQuoteAmount(quoteAmount);
        t.setTakerOrderNo(taker.getOrderNo());
        t.setMakerOrderNo(maker.getOrderNo());
        t.setTakerOrderId(taker.getId());
        t.setMakerOrderId(maker.getId());
        t.setTakerUserId(taker.getUserId());
        t.setMakerUserId(maker.getUserId());
        t.setTakerSide(taker.getSide());
        t.setBuyUserId(taker.getSide() == OrderConstant.SIDE_BUY ? taker.getUserId() : maker.getUserId());
        t.setSellUserId(taker.getSide() == OrderConstant.SIDE_SELL ? taker.getUserId() : maker.getUserId());
        t.setTakerFee(0L);
        t.setMakerFee(0L);
        t.setSettleStatus(0);
        t.setSettleQuoteRequestId(t.getTradeNo() + ":Q");
        t.setSettleBaseRequestId(t.getTradeNo() + ":B");

        result.trades.add(t);
        result.changedMakers.add(maker);
    }
}
