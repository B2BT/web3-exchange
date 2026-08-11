package com.web3.exchange.market.kafka.dto;

import lombok.Data;

/**
 * Kafka 行情事件消息体（统一封装 ticker / kline / depth，由 type 区分）。
 * <p>所有金额/价格均为 Long 最小单位，保持与内存聚合器一致。</p>
 */
@Data
public class MarketEvent {
    /** 事件类型：ticker / kline / depth */
    private String type;
    /** 系统交易对（如 BTC/USDT） */
    private String symbol;

    // ---- ticker 字段 ----
    private Long lastPrice;
    private Long high24h;
    private Long low24h;
    private Long change24h;        // 基点 10000=100%
    private Long volume24h;
    private Long quoteVolume24h;

    // ---- kline 字段 ----
    private String interval;       // 1m/5m/15m/1h/4h/1d
    private Long openTime;         // 窗口开始 epoch millis
    private Long open;
    private Long high;
    private Long low;
    private Long close;
    private Long volume;
    private Long quoteVolume;

    // ---- depth 字段（bids/asks 为 [price, qty] 最小单位） ----
    private java.util.List<long[]> bids;
    private java.util.List<long[]> asks;

    /** 事件产生时间戳（epoch millis），用于重放/审计。 */
    private Long eventTime;
}
