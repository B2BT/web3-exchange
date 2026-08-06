package com.web3.exchange.market.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * K线视图（对外 REST）。金额/价格/数量一律 Long 最小单位（展示层换算），不返回浮点。
 */
@Data
@Schema(description = "K线视图")
public class KlineVO {
    @Schema(description = "交易对")
    private String symbol;
    @Schema(description = "周期:1m/5m/15m/1h/4h/1d")
    private String interval;
    @Schema(description = "窗口开始时间(epoch millis, UTC)")
    private Long openTime;
    @Schema(description = "开盘价(计价币最小单位)")
    private Long open;
    @Schema(description = "最高价")
    private Long high;
    @Schema(description = "最低价")
    private Long low;
    @Schema(description = "收盘价")
    private Long close;
    @Schema(description = "成交量(基础币最小单位)")
    private Long volume;
    @Schema(description = "成交额(计价币最小单位)")
    private Long quoteVolume;
}
