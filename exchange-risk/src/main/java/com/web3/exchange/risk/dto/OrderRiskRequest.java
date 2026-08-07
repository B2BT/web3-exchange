package com.web3.exchange.risk.dto;

import lombok.Data;

/**
 * 下单风控前置校验请求（order → risk）。
 */
@Data
public class OrderRiskRequest {
    /** 用户ID */
    private Long userId;
    /** 交易对 */
    private String symbol;
    /** 方向:1=BUY 2=SELL */
    private Integer side;
    /** 类型:1=限价 2=市价 */
    private Integer orderType;
    /** 限价(计价币最小单位) */
    private Long price;
    /** 数量(基础币最小单位) */
    private Long quantity;
    /** 市价买单预算额 */
    private Long quoteAmount;
    /** 盘口最优卖价(滑点计算用) */
    private Long bestAsk;
    /** 盘口最优买价 */
    private Long bestBid;
}
