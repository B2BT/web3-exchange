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

    /**
     * 单交易对 ticker——由 D1 K线派生（当日 D1 窗口 + 前 1 个 D1 窗口，近似 24h，docs 方案一）。
     */
    public Ticker getTicker(String symbol) {
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

    /** 获取某 symbol×interval 的窗口 map（惰性建内层容器）。 */
    private ConcurrentHashMap<Long, Kline> intervalMap(String symbol, KlineInterval iv) {
        ConcurrentHashMap<String, ConcurrentHashMap<Long, Kline>> byInterval =
                store.computeIfAbsent(symbol, k -> new ConcurrentHashMap<>());
        return byInterval.computeIfAbsent(iv.intervalName(), k -> new ConcurrentHashMap<>());
    }

    /** 暴露内部 store 供诊断（返回只读视图意义不大，此处直接返回内部引用仅供测试）。 */
    Map<String, ConcurrentHashMap<String, ConcurrentHashMap<Long, Kline>>> rawStore() {
        return store;
    }
}
