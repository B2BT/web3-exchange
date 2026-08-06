package com.web3.exchange.market.market.model;

import lombok.Data;

/**
 * 交易对实时快照（ticker），全部 Long 最小单位。由 K线聚合结果派生。
 */
@Data
public class Ticker {
    private String symbol;
    private Long lastPrice;        // 最新成交价(计价币最小单位)
    private Long openPrice;        // 24h 前首笔成交价(用于涨跌幅)
    private Long high24h, low24h;  // 24h 最高/最低
    private Long volume24h;        // 24h 成交量(基础币最小单位)
    private Long quoteVolume24h;   // 24h 成交额(计价币最小单位)
    private Long change24h;        // 涨跌幅(基点 bp，整数：10000=100%)
    private Long count24h;         // 24h 成交笔数(可选)
}
