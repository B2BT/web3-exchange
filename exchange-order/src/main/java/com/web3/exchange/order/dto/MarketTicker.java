package com.web3.exchange.order.dto;

import lombok.Data;

/**
 * 行情 Ticker 精简视图（order 域拉取 market 最新价用）。
 * <p>只映射 symbol + lastPrice（最新成交价，计价币最小单位），供条件单触发任务判激活。</p>
 */
@Data
public class MarketTicker {
    /** 交易对，如 BTC/USDT */
    private String symbol;
    /** 最新成交价（计价币最小单位） */
    private Long lastPrice;
}
