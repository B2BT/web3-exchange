package com.web3.exchange.market.market.model;

import lombok.Data;

/**
 * K线一行（OHLCV，纯内存不可变语义，更新时整体替换）。
 * <p>金额/价格/数量一律 <b>Long 最小单位</b>：price/quoteVolume 为计价币最小单位，
 * volume 为基础币最小单位。禁止 double/float。</p>
 */
@Data
public class Kline {
    private final String symbol;
    private final String interval;      // "1m"/"5m"/"15m"/"1h"/"4h"/"1d"
    private final Long openTime;        // 窗口开始时间(epoch millis, UTC 整点)
    private Long open, high, low, close;      // 计价币最小单位
    private Long volume;                      // 基础币最小单位
    private Long quoteVolume;                 // 计价币最小单位

    public Kline(String symbol, String interval, Long openTime,
                 Long price, Long quantity, Long quoteAmount) {
        this.symbol = symbol;
        this.interval = interval;
        this.openTime = openTime;
        // 首笔成交即开/高/低/收
        this.open = price;
        this.high = price;
        this.low = price;
        this.close = price;
        this.volume = quantity;
        this.quoteVolume = quoteAmount;
    }
}
