package com.web3.exchange.order.engine;

import com.web3.exchange.order.constant.OrderConstant;
import com.web3.exchange.order.entity.Order;

import java.util.Comparator;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.TreeMap;

/**
 * 内存订单簿（单个交易对的盘口）。
 * <p>
 * 价格优先 + 时间优先的数据结构：用两个 {@link TreeMap} 维护盘口——
 * 买盘 {@link #bids} 按价格<b>降序</b>（价格高者先成交），卖盘 {@link #asks} 按价格<b>升序</b>（价格低者先成交）；
 * 每个价格档用 FIFO {@link PriorityQueue}（按 createTime 升序、id 升序）保证<b>时间优先</b>（同价先到先得）。
 * </p>
 * <p>本类为<b>纯数据结构</b>，不依赖 DB，可独立单测。</p>
 */
public class OrderBook {

    /**
     * 同价档 FIFO 比较器：先到（createTime 小）者先成交；同 createTime 按 id 升序兜底（雪花 id 单调递增）。
     */
    private static final Comparator<Order> FIFO = Comparator
            .comparing(Order::getCreateTime, Comparator.nullsLast(Comparator.naturalOrder()))
            .thenComparing(Order::getId, Comparator.nullsLast(Comparator.naturalOrder()));

    /** 买盘：价格降序 → 最高买价在 firstEntry */
    private final TreeMap<Long, PriorityQueue<Order>> bids = new TreeMap<>(Comparator.reverseOrder());
    /** 卖盘：价格升序 → 最低卖价在 firstEntry */
    private final TreeMap<Long, PriorityQueue<Order>> asks = new TreeMap<>();

    /** 入簿（仅活跃限价单）。 */
    public void add(Order o) {
        if (o.getSide() == OrderConstant.SIDE_BUY) {
            bids.computeIfAbsent(o.getPrice(), k -> new PriorityQueue<>(FIFO)).add(o);
        } else {
            asks.computeIfAbsent(o.getPrice(), k -> new PriorityQueue<>(FIFO)).add(o);
        }
    }

    /** 最优卖单（最低价，同价最早）；盘口为空返回 null。 */
    public Order bestAsk() {
        return peek(asks);
    }

    /** 最优买单（最高价，同价最早）；盘口为空返回 null。 */
    public Order bestBid() {
        return peek(bids);
    }

    /** 卖盘：价格 <= 上限的所有活跃挂单快照（价格升序，FOK/评估用）。 */
    public java.util.List<Order> asksUpTo(long priceLimit) {
        java.util.List<Order> out = new java.util.ArrayList<>();
        for (Map.Entry<Long, PriorityQueue<Order>> e : asks.entrySet()) {
            if (e.getKey() > priceLimit) {
                break;
            }
            out.addAll(e.getValue());
        }
        return out;
    }

    /** 买盘：价格 >= 下限的所有活跃挂单快照（价格降序，FOK/评估用）。 */
    public java.util.List<Order> bidsFrom(long priceFloor) {
        java.util.List<Order> out = new java.util.ArrayList<>();
        for (Map.Entry<Long, PriorityQueue<Order>> e : bids.entrySet()) {
            if (e.getKey() < priceFloor) {
                break;
            }
            out.addAll(e.getValue());
        }
        return out;
    }

    /** 卖盘全部活跃挂单快照（价格升序，市价 FOK/评估用）。 */
    public java.util.List<Order> allAsks() {
        java.util.List<Order> out = new java.util.ArrayList<>();
        for (PriorityQueue<Order> q : asks.values()) {
            out.addAll(q);
        }
        return out;
    }

    /** 买盘全部活跃挂单快照（价格降序，市价 FOK/评估用）。 */
    public java.util.List<Order> allBids() {
        java.util.List<Order> out = new java.util.ArrayList<>();
        for (PriorityQueue<Order> q : bids.values()) {
            out.addAll(q);
        }
        return out;
    }

    /** 从簿移除（挂单成交归零或撤单）。 */
    public void remove(Order o) {
        if (o.getSide() == OrderConstant.SIDE_BUY) {
            removeFrom(bids, o);
        } else {
            removeFrom(asks, o);
        }
    }

    /** 移除某一方向的价格档。 */
    public void removePriceLevel(int side, Long price) {
        if (side == OrderConstant.SIDE_BUY) {
            bids.remove(price);
        } else {
            asks.remove(price);
        }
    }

    /**
     * 按订单ID从对应方向的价格档队列中移除（撤单用）。返回是否移除成功。
     */
    public boolean removeById(int side, Long price, Long orderId) {
        TreeMap<Long, PriorityQueue<Order>> target = side == OrderConstant.SIDE_BUY ? bids : asks;
        PriorityQueue<Order> q = target.get(price);
        if (q == null) {
            return false;
        }
        boolean removed = q.removeIf(o -> orderId.equals(o.getId()));
        if (q.isEmpty()) {
            target.remove(price);
        }
        return removed;
    }

    /**
     * 深度快照：按价格档聚合每档内所有活跃挂单的 remaining 之和。
     * <p>bids 按价格<b>降序</b>（最高买价在前），asks 按价格<b>升序</b>（最低卖价在前），
     * 各取前 {@code limit} 档。每档为 {@code long[]{price, quantity}}，金额/数量均为最小单位。</p>
     */
    public static class DepthSnapshot {
        /** 买盘：{price, quantity}，价格降序 */
        public final java.util.List<long[]> bids = new java.util.ArrayList<>();
        /** 卖盘：{price, quantity}，价格升序 */
        public final java.util.List<long[]> asks = new java.util.ArrayList<>();
    }

    /**
     * 聚合盘口深度。遍历 bids/asks 每个价格档，把该档 PriorityQueue 里所有 Order.remaining 求和；
     * bids 降序取前 limit 档、asks 升序取前 limit 档。
     *
     * @param limit 每方向最多返回的档数（≥0）
     * @return 深度快照（含 bids/asks 两个有序列表）
     */
    public DepthSnapshot depth(int limit) {
        DepthSnapshot snap = new DepthSnapshot();
        if (limit <= 0) {
            return snap;
        }
        aggregate(bids, snap.bids, limit);
        aggregate(asks, snap.asks, limit);
        return snap;
    }

    /** 单方向聚合：TreeMap 已有序（bids 降序、asks 升序），逐档累加 remaining，取前 limit 档。 */
    private void aggregate(TreeMap<Long, PriorityQueue<Order>> side, java.util.List<long[]> out, int limit) {
        for (java.util.Map.Entry<Long, PriorityQueue<Order>> e : side.entrySet()) {
            if (out.size() >= limit) {
                break;
            }
            long quantity = 0;
            for (Order o : e.getValue()) {
                quantity += o.getRemaining();
            }
            if (quantity > 0) {
                out.add(new long[]{e.getKey(), quantity});
            }
        }
    }

    /** 某方向盘口是否为空。 */
    public boolean isEmpty(int side) {
        return (side == OrderConstant.SIDE_BUY ? bids : asks).isEmpty();
    }

    /** 盘口挂单总数（买盘+卖盘）。 */
    public int size() {
        int n = 0;
        for (PriorityQueue<Order> q : bids.values()) {
            n += q.size();
        }
        for (PriorityQueue<Order> q : asks.values()) {
            n += q.size();
        }
        return n;
    }

    private Order peek(TreeMap<Long, PriorityQueue<Order>> side) {
        if (side.isEmpty()) {
            return null;
        }
        Map.Entry<Long, PriorityQueue<Order>> e = side.firstEntry();
        PriorityQueue<Order> q = e.getValue();
        while (q.isEmpty()) {
            side.pollFirstEntry();
            if (side.isEmpty()) {
                return null;
            }
            e = side.firstEntry();
            q = e.getValue();
        }
        return q.peek();
    }

    private void removeFrom(TreeMap<Long, PriorityQueue<Order>> side, Order o) {
        PriorityQueue<Order> q = side.get(o.getPrice());
        if (q != null) {
            q.remove(o);
            if (q.isEmpty()) {
                side.remove(o.getPrice());
            }
        }
    }
}
