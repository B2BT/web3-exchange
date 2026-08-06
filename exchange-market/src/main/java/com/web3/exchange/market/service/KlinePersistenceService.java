package com.web3.exchange.market.service;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.web3.exchange.market.entity.KlineRow;
import com.web3.exchange.market.mapper.KlineRowMapper;
import com.web3.exchange.market.market.KlineInterval;
import com.web3.exchange.market.market.MarketAggregator;
import com.web3.exchange.market.market.model.Kline;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * K线持久化服务——内存+DB 双轨的「已关闭窗口」落库与重启重建。
 * <p>
 * <b>落库</b>：@Scheduled 周期扫描内存聚合的 <b>已关闭窗口</b>（openTime + period ≤ now），
 * 经 {@code uk_symbol_period_window} 唯一索引 + ON DUPLICATE KEY UPDATE 幂等 upsert，
 * 同窗口重复只更新同一行；已落库的窗口用 {@code persistedKeys} 去重，避免重复写。
 * </p>
 * <p>
 * <b>重建</b>：{@link #rebuild()} 在应用就绪（ApplicationReadyEvent）时从 t_kline 读各周期最近 N 条
 * 回填内存 store，实现「重启不丢历史 K线」；回填窗口标记为已持久化，后续不被重复落库。
 * </p>
 */
@Service
public class KlinePersistenceService {

    private static final Logger log = LoggerFactory.getLogger(KlinePersistenceService.class);

    private final KlineRowMapper klineRowMapper;
    private final MarketAggregator aggregator;

    /** 已落库窗口 key：symbol:period:openTime（重启后由回填初始化，避免重复写）。 */
    private final Set<String> persistedKeys = ConcurrentHashMap.newKeySet();

    /** 每周期重建/回填的最近窗口数上限。 */
    private static final int REBUILD_LIMIT = 2000;

    public KlinePersistenceService(KlineRowMapper klineRowMapper, MarketAggregator aggregator) {
        this.klineRowMapper = klineRowMapper;
        this.aggregator = aggregator;
    }

    /** 应用就绪后从 DB 重建内存 K线（重启不丢历史）。 */
    @EventListener(ApplicationReadyEvent.class)
    public void rebuild() {
        List<KlineRow> rows = new ArrayList<>();
        for (KlineInterval iv : KlineInterval.enabled()) {
            rows.addAll(klineRowMapper.selectRecentByPeriod(iv.intervalName(), REBUILD_LIMIT));
        }
        int n = 0;
        for (KlineRow r : rows) {
            aggregator.restoreFromDb(r);
            persistedKeys.add(r.getSymbol() + ":" + r.getPeriod() + ":" + r.getWindowStart());
            n++;
        }
        log.info("[market] K线从 DB 重建完成，回填 {} 条已关闭窗口", n);
    }

    /**
     * 周期落库：扫描内存聚合的已关闭窗口，幂等 upsert 到 t_kline。
     */
    @Scheduled(fixedRateString = "${market.persist.interval-ms:5000}")
    public void persistClosedWindows() {
        long now = System.currentTimeMillis();
        List<KlineRow> toWrite = new ArrayList<>();
        for (Map.Entry<String, ConcurrentHashMap<String, ConcurrentHashMap<Long, Kline>>> bySymbol
                : aggregator.rawStore().entrySet()) {
            String symbol = bySymbol.getKey();
            for (Map.Entry<String, ConcurrentHashMap<Long, Kline>> byInterval : bySymbol.getValue().entrySet()) {
                KlineInterval iv = KlineInterval.fromName(byInterval.getKey());
                if (iv == null) {
                    continue;
                }
                long periodMs = iv.millis();
                for (Kline k : byInterval.getValue().values()) {
                    boolean closed = k.getOpenTime() + periodMs <= now;
                    if (!closed) {
                        continue;
                    }
                    String key = symbol + ":" + byInterval.getKey() + ":" + k.getOpenTime();
                    if (persistedKeys.add(key)) {
                        toWrite.add(toRow(symbol, byInterval.getKey(), k));
                    }
                }
            }
        }
        if (!toWrite.isEmpty()) {
            int n = klineRowMapper.upsertBatch(toWrite);
            log.debug("[market] K线落库 {} 条已关闭窗口", n);
        }
    }

    private KlineRow toRow(String symbol, String period, Kline k) {
        KlineRow row = new KlineRow();
        row.setId(IdWorker.getId());
        row.setSymbol(symbol);
        row.setPeriod(period);
        row.setWindowStart(k.getOpenTime());
        row.setOpen(k.getOpen());
        row.setHigh(k.getHigh());
        row.setLow(k.getLow());
        row.setClose(k.getClose());
        row.setVolume(k.getVolume());
        row.setQuoteVolume(k.getQuoteVolume());
        return row;
    }
}
