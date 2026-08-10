package com.web3.exchange.market.market;

import com.web3.exchange.common.order.dto.TradeSettleDTO;
import com.web3.exchange.market.market.model.Kline;
import com.web3.exchange.market.market.model.Ticker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 内存 K线/ticker 聚合器——消费 ORDER-TRADE 成交流水，按 UTC 整点窗口切分聚合 OHLCV。
 * <p>
 * <b>数据结构</b>：{@code symbol -> interval -> openTime -> Kline}（外层 ConcurrentHashMap，
 * 内层亦并发容器），单 key 更新用 {@code ConcurrentHashMap.compute} 保证原子，同窗口 OHLC
 * 更新不丢、不乱，不同 symbol/周期互不阻塞。
 * </p>
 * <p>
 * <b>精度</b>：price/quantity/quoteAmount 一律 Long 最小单位累加/比较，不做除法。
 * </p>
 * <p>
 * <b>幂等</b>：按 tradeNo 内存去重（重启即重建，与「行情可重放、非强一致」特性一致）。
 * </p>
 */
@Component
public class MarketAggregator {

    private static final Logger log = LoggerFactory.getLogger(MarketAggregator.class);

    /** symbol -> interval -> openTime -> Kline */
    private final ConcurrentHashMap<String, ConcurrentHashMap<String, ConcurrentHashMap<Long, Kline>>> store =
            new ConcurrentHashMap<>();

    /** 已聚合成交号去重（tradeNo:symbol），保证同一成交只聚合一次。 */
    private final Set<String> seenTrades = ConcurrentHashMap.newKeySet();

    /** 每 (symbol, interval) 返回最多 K线根数上限（防内存无限增长，查询时过滤即可）。 */
    private static final int MAX_KLINE_PER_QUERY = 1000;

    /**
     * 聚合一笔成交：对每个启用周期做 OHLCV 更新。
     */
    public void onTrade(TradeSettleDTO dto) {
        long nowMs = System.currentTimeMillis();
        onTradeAt(dto, nowMs);
    }

    /**
     * 注入一笔外部行情成交（真实市场数据源，如 CoinGecko）。
     * <p>复用 onTradeAt 的 OHLCV 聚合：生成/更新 K线与 ticker，使真实价格进入行情展示。
     * 外部 source 用固定前缀单号，与内部撮合成交单号不冲突。</p>
     *
     * @param symbol   交易对（如 BTC/USDT）
     * @param price    价格（计价币最小单位 Long）
     * @param quantity 数量（基础币最小单位，可为 1）
     */
    public void applyExternalTrade(String symbol, long price, long quantity) {
        TradeSettleDTO dto = new TradeSettleDTO();
        dto.setTradeNo("EXT-" + System.currentTimeMillis() + "-" + symbol.hashCode());
        dto.setSymbol(symbol);
        dto.setPrice(price);
        dto.setQuantity(quantity);
        dto.setQuoteAmount(price * quantity);
        onTrade(dto);
    }

    /**
     * 在指定时刻聚合一笔成交（供测试注入精确时间戳做跨窗口/同窗口断言）。
     */
    void onTradeAt(TradeSettleDTO dto, long nowMs) {
        String tradeNo = dto.getTradeNo();
        String symbol = dto.getSymbol();
        if (tradeNo == null || symbol == null || dto.getPrice() == null
                || dto.getQuantity() == null || dto.getQuoteAmount() == null) {
            log.warn("[market] 成交字段不完整，跳过聚合。tradeNo={} symbol={}", tradeNo, symbol);
            return;
        }
        // 幂等去重：同一成交只聚合一次
        if (!seenTrades.add(tradeNo + ":" + symbol)) {
            log.debug("[market] 重复成交，跳过聚合。tradeNo={}", tradeNo);
            return;
        }
        for (KlineInterval iv : KlineInterval.enabled()) {
            long openTime = (nowMs / iv.millis()) * iv.millis();
            ConcurrentHashMap<Long, Kline> map = intervalMap(symbol, iv);
            map.compute(openTime, (k, cur) -> {
                if (cur == null) {
                    return new Kline(symbol, iv.intervalName(), k,
                            dto.getPrice(), dto.getQuantity(), dto.getQuoteAmount());
                }
                cur.setHigh(Math.max(cur.getHigh(), dto.getPrice()));
                cur.setLow(Math.min(cur.getLow(), dto.getPrice()));
                cur.setClose(dto.getPrice());                       // 最新成交价即收盘价
                cur.setVolume(cur.getVolume() + dto.getQuantity());
                cur.setQuoteVolume(cur.getQuoteVolume() + dto.getQuoteAmount());
                return cur;
            });
        }
        log.debug("[market] 已聚合成交 {} {} price={} qty={} quote={}", symbol, tradeNo,
                dto.getPrice(), dto.getQuantity(), dto.getQuoteAmount());
    }

    /**
     * 查询某交易对某周期的 K线（按窗口时间升序，最多 limit 根，默认取最近 N 根）。
     */
    public List<Kline> getKlines(String symbol, String interval, int limit) {
        KlineInterval iv = KlineInterval.fromName(interval);
        if (iv == null) {
            return List.of();
        }
        ConcurrentHashMap<String, ConcurrentHashMap<Long, Kline>> byInterval = store.get(symbol);
        if (byInterval == null) {
            return List.of();
        }
        ConcurrentHashMap<Long, Kline> map = byInterval.get(iv.intervalName());
        if (map == null || map.isEmpty()) {
            return List.of();
        }
        int cap = Math.min(Math.max(limit, 1), MAX_KLINE_PER_QUERY);
        List<Kline> list = new ArrayList<>(map.values());
        list.sort(Comparator.comparingLong(Kline::getOpenTime));
        if (list.size() > cap) {
            return new ArrayList<>(list.subList(list.size() - cap, list.size()));
        }
        return list;
    }

    /** symbol -> 权威 24h ticker 快照（外部真实行情覆盖，优先级高于 K线推导） */
    private final ConcurrentHashMap<String, Ticker> externalTickers = new ConcurrentHashMap<>();

    /** symbol -> 真实订单簿深度（Binance @depth20，每档 {price, qty} 最小单位） */
    private final ConcurrentHashMap<String, DepthSnapshot> externalDepths = new ConcurrentHashMap<>();

    /** 订单簿深度快照（Level 数组，价格/数量均为 Long 最小单位）。 */
    @lombok.Data
    public static class DepthSnapshot {
        private final java.util.List<Level> bids = new java.util.ArrayList<>();
        private final java.util.List<Level> asks = new java.util.ArrayList<>();
        private long updateId;

        @lombok.Data
        public static class Level {
            private final long price;
            private final long quantity;
        }
    }

    /** 注入真实订单簿深度（Binance @depth20）。覆盖同 symbol 快照。 */
    public void updateExternalDepth(String symbol, java.util.List<long[]> bids, java.util.List<long[]> asks) {
        DepthSnapshot snap = new DepthSnapshot();
        for (long[] b : bids) snap.getBids().add(new DepthSnapshot.Level(b[0], b[1]));
        for (long[] a : asks) snap.getAsks().add(new DepthSnapshot.Level(a[0], a[1]));
        externalDepths.put(symbol, snap);
    }

    /** 读取某交易对真实深度（无则 null）。 */
    public DepthSnapshot getExternalDepth(String symbol) {
        return externalDepths.get(symbol);
    }

    /**
     * 注入一笔外部完整 K线（来自真实行情源，如 Binance kline 流）。
     * <p>直接写入 store（幂等：同 (symbol, period, openTime) 覆盖），比从成交聚合更精确。</p>
     */
    public void applyExternalKline(String symbol, String interval, long openTime,
                                   long open, long high, long low, long close,
                                   long volume, long quoteVolume) {
        KlineInterval iv = KlineInterval.fromName(interval);
        if (iv == null) return;
        Kline k = new Kline(symbol, iv.intervalName(), openTime, close, volume, quoteVolume);
        k.setOpen(open);
        k.setHigh(high);
        k.setLow(low);
        intervalMap(symbol, iv).put(openTime, k);
    }

    /**
     * 注入权威 24h ticker 快照（来自真实行情源，如 CoinGecko 的 high/low/volume/change）。
     * <p>若存在则 getTicker 直接返回该快照（K线推导仅作兜底），保证 24h 指标准确。</p>
     */
    public void updateExternalTicker(Ticker t) {
        if (t == null || t.getSymbol() == null) return;
        externalTickers.put(t.getSymbol(), t);
    }

    /**
     * 单交易对 ticker——优先返回外部真实行情快照；否则由 D1 K线派生（内部模拟成交兜底）。
     */
    public Ticker getTicker(String symbol) {
        Ticker ext = externalTickers.get(symbol);
        if (ext != null) {
            return ext;
        }
        List<Kline> d1 = getKlines(symbol, "1d", 100);
        if (d1.isEmpty()) {
            return null;
        }
        Kline latest = d1.get(d1.size() - 1);
        long last = latest.getClose();
        long open = latest.getOpen();
        long high = latest.getHigh();
        long low = latest.getLow();
        long volume = latest.getVolume();
        long quoteVolume = latest.getQuoteVolume();
        long count = 1;
        // 合并前 1 个 D1 窗口（近似覆盖 24h）
        if (d1.size() >= 2) {
            Kline prev = d1.get(d1.size() - 2);
            high = Math.max(high, prev.getHigh());
            low = Math.min(low, prev.getLow());
            volume += prev.getVolume();
            quoteVolume += prev.getQuoteVolume();
            count++;
        }
        long change = open == 0L ? 0L : (last - open) * 10000L / open;
        Ticker t = new Ticker();
        t.setSymbol(symbol);
        t.setLastPrice(last);
        t.setOpenPrice(open);
        t.setHigh24h(high);
        t.setLow24h(low);
        t.setVolume24h(volume);
        t.setQuoteVolume24h(quoteVolume);
        t.setChange24h(change);
        t.setCount24h(count);
        return t;
    }

    /** 全市场 ticker 列表。 */
    public List<Ticker> getTickers() {
        List<Ticker> result = new ArrayList<>();
        for (String symbol : store.keySet()) {
            Ticker t = getTicker(symbol);
            if (t != null) {
                result.add(t);
            }
        }
        result.sort(Comparator.comparing(Ticker::getSymbol));
        return result;
    }

    /** 是否已消费到某成交（测试/诊断用）。 */
    public boolean hasTrade(String tradeNo) {
        return seenTrades.stream().anyMatch(s -> s.startsWith(tradeNo + ":"));
    }

    /**
     * 从 DB 回填一笔已持久化的 K线到内存 store（重启重建用）。
     * 幂等：同 (symbol, period, openTime) 已存在则不覆盖（以内存实时聚合为准）。
     */
    public void restoreFromDb(com.web3.exchange.market.entity.KlineRow row) {
        KlineInterval iv = KlineInterval.fromName(row.getPeriod());
        if (iv == null) {
            return;
        }
        Kline k = new Kline(row.getSymbol(), row.getPeriod(), row.getWindowStart(),
                row.getOpen(), row.getVolume(), row.getQuoteVolume());
        k.setHigh(row.getHigh());
        k.setLow(row.getLow());
        k.setClose(row.getClose());
        ConcurrentHashMap<Long, Kline> map = intervalMap(row.getSymbol(), iv);
        map.putIfAbsent(row.getWindowStart(), k);
    }

    /** 获取某 symbol×interval 的窗口 map（惰性建内层容器）。 */
    private ConcurrentHashMap<Long, Kline> intervalMap(String symbol, KlineInterval iv) {
        ConcurrentHashMap<String, ConcurrentHashMap<Long, Kline>> byInterval =
                store.computeIfAbsent(symbol, k -> new ConcurrentHashMap<>());
        return byInterval.computeIfAbsent(iv.intervalName(), k -> new ConcurrentHashMap<>());
    }

    /** 暴露内部 store 供持久化扫描/测试（返回内部引用仅供只读遍历）。 */
    public Map<String, ConcurrentHashMap<String, ConcurrentHashMap<Long, Kline>>> rawStore() {
        return store;
    }
}
