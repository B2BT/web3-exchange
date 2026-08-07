package com.web3.exchange.futures.engine;

import com.web3.exchange.futures.dto.FuturesFill;
import com.web3.exchange.futures.entity.FuturesOrder;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 单机内存合约撮合引擎。
 * <p>
 * 复用现货撮合思想（价格优先 + 时间优先），但面向合约双向方向：开多/平空=买方(BUY)，
 * 开空/平多=卖方(SELL)。容器 {@link ConcurrentHashMap#symbol -> 买卖订单簿}，同一交易对
 * synchronized 串行化保证严格有序。
 * </p>
 * <p>
 * 撮合规则：BUY 吃卖盘（最低价），SELL 吃买盘（最高价）；成交价 = 对方挂单价（maker）。
 * 本引擎只产出成交(FuturesFill)并移除成交挂单，<b>不直接改持仓/账户</b>——由服务层核算。
 * </p>
 */
@Component
public class FuturesMatchingEngine {

    /** 交易对 → 订单簿 */
    private final ConcurrentHashMap<String, OrderBook> books = new ConcurrentHashMap<>();

    /** 撤单结果 */
    public static class FillResult {
        public final List<FuturesFill> fills = new ArrayList<>();
        /** 被完全成交/部分成交而需更新DB的挂单 */
        public final List<FuturesOrder> touchedMakers = new ArrayList<>();
        /** 是否完全成交（无剩余） */
        public boolean fullyFilled = false;
    }

    /** 挂单入簿。返回撮合结果（含 taker 成交 + 被消费的 maker）。 */
    public synchronized FillResult place(FuturesOrder order) {
        OrderBook book = books.computeIfAbsent(order.getSymbol(), k -> new OrderBook());
        return book.match(order);
    }

    /** 撤单：从簿移除指定 orderNo 的挂单。返回是否移除成功。 */
    public synchronized boolean cancel(String symbol, String orderNo) {
        OrderBook book = books.get(symbol);
        return book != null && book.remove(orderNo);
    }

    /** 查询当前买一/卖一（合约深度，公开用）。 */
    public synchronized long[] best(String symbol) {
        OrderBook book = books.get(symbol);
        if (book == null) return new long[]{0, 0};
        return book.best();
    }

    /* ============ 内部订单簿 ============ */

    private static class OrderBook {
        /** 卖盘（按价格升序） */
        private final java.util.TreeMap<Long, List<FuturesOrder>> asks = new java.util.TreeMap<>();
        /** 买盘（按价格降序） */
        private final java.util.TreeMap<Long, List<FuturesOrder>> bids = new java.util.TreeMap<>();
        /** orderNo → 当前在簿挂单（快速撤单） */
        private final ConcurrentHashMap<String, FuturesOrder> index = new ConcurrentHashMap<>();

        private boolean isBuy(FuturesOrder o) {
            return o.getSide() == 1 || o.getSide() == 4; // 开多/平空=买方
        }

        FillResult match(FuturesOrder taker) {
            FillResult res = new FillResult();
            long remaining = taker.getQuantity();
            boolean buy = isBuy(taker);

            while (remaining > 0) {
                // 定位可撮合档位（跳过与 taker 同用户的整档，防止自成交）
                long price = 0;
                FuturesOrder maker = null;
                if (buy) {
                    while (!asks.isEmpty()) {
                        var e = asks.firstEntry();
                        if (taker.getOrderType() == 1 && e.getKey() > taker.getPrice()) break;
                        FuturesOrder m = findNonSelf(e.getValue(), taker.getUserId());
                        if (m != null) { price = e.getKey(); maker = m; break; }
                        asks.remove(e.getKey()); // 整档都是同用户，跳过该价档
                    }
                } else {
                    while (!bids.isEmpty()) {
                        var e = bids.firstEntry();
                        if (taker.getOrderType() == 1 && e.getKey() < taker.getPrice()) break;
                        FuturesOrder m = findNonSelf(e.getValue(), taker.getUserId());
                        if (m != null) { price = e.getKey(); maker = m; break; }
                        bids.remove(e.getKey());
                    }
                }
                if (maker == null) break;

                List<FuturesOrder> makers = (buy ? asks : bids).get(price);
                long matchQty = Math.min(remaining, maker.getRemaining());

                // 记录成交（taker 与 maker 各一笔，各自 userId 与 side）
                FuturesFill f = new FuturesFill();
                f.setUserId(taker.getUserId());
                f.setPrice(price);
                f.setQuantity(matchQty);
                f.setSide(taker.getSide());
                res.fills.add(f);

                FuturesFill mf = new FuturesFill();
                mf.setUserId(maker.getUserId());
                mf.setPrice(price);
                mf.setQuantity(matchQty);
                mf.setSide(maker.getSide());
                res.fills.add(mf);

                remaining -= matchQty;

                // 更新 maker
                maker.setRemaining(maker.getRemaining() - matchQty);
                maker.setFilled(maker.getFilled() + matchQty);
                res.touchedMakers.add(maker);
                if (maker.getRemaining() == 0) {
                    final String makerOrderNo = maker.getOrderNo();
                    makers.removeIf(x -> x.getOrderNo().equals(makerOrderNo));
                    index.remove(makerOrderNo);
                    if (makers.isEmpty()) {
                        if (buy) asks.remove(price);
                        else bids.remove(price);
                    }
                }
            }

            // taker 剩余
            taker.setRemaining(remaining);
            taker.setFilled(taker.getQuantity() - remaining);
            res.fullyFilled = remaining == 0;
            // 若还有剩余且是限价单，挂入簿
            if (remaining > 0 && taker.getOrderType() == 1) {
                List<FuturesOrder> list = (buy ? bids : asks).computeIfAbsent(taker.getPrice(), k -> new ArrayList<>());
                list.add(taker);
                index.put(taker.getOrderNo(), taker);
            }
            return res;
        }

        boolean remove(String orderNo) {
            FuturesOrder o = index.remove(orderNo);
            if (o == null) return false;
            boolean buy = o.getSide() == 1 || o.getSide() == 4;
            List<FuturesOrder> list = (buy ? bids : asks).get(o.getPrice());
            if (list != null) {
                list.removeIf(x -> x.getOrderNo().equals(orderNo));
                if (list.isEmpty()) {
                    if (buy) bids.remove(o.getPrice());
                    else asks.remove(o.getPrice());
                }
            }
            return true;
        }

        /** 从某价档列表找一个非 taker 用户的订单（防自成交）；找不到返回 null。 */
        private FuturesOrder findNonSelf(List<FuturesOrder> list, Long takerUserId) {
            for (FuturesOrder o : list) {
                if (!o.getUserId().equals(takerUserId)) {
                    return o;
                }
            }
            return null;
        }

        long[] best() {
            long ask = asks.isEmpty() ? 0 : asks.firstKey();
            long bid = bids.isEmpty() ? 0 : bids.lastKey();
            return new long[]{bid, ask};
        }
    }
}
