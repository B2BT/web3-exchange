package com.web3.exchange.market.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * Ticker 视图（对外 REST）。全部 Long 最小单位。
 */
@Data
@Schema(description = "Ticker视图")
public class TickerVO {
    @Schema(description = "交易对")
    private String symbol;
    @Schema(description = "最新成交价(计价币最小单位)")
    private Long lastPrice;
    @Schema(description = "24h涨跌幅(基点bp,10000=100%)")
    private Long change24h;
    @Schema(description = "24h最高价")
    private Long high24h;
    @Schema(description = "24h最低价")
    private Long low24h;
    @Schema(description = "24h成交量(基础币最小单位)")
    private Long volume24h;
    @Schema(description = "24h成交额(计价币最小单位)")
    private Long quoteVolume24h;
}
