package com.web3.exchange.market.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.web3.exchange.common.entity.base.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

/**
 * K线落库行（t_kline）——内存聚合的「已关闭窗口」最终态持久化。
 * <p>
 * 双轨设计：内存 {@code MarketAggregator} 负责实时聚合与查询（快）；本表保存每个
 * (symbol, period, windowStart) 已关闭窗口的最终 OHLCV，供重启后重建内存。幂等由
 * {@code uk_symbol_period_window(symbol,period,window_start)} 唯一索引 + ON DUPLICATE KEY UPDATE
 * 兜底：同窗口重复消费/重复落库只更新同一行，不产生重复行。
 * </p>
 * <p>
 * 精度约定（与内存 Kline 一致）：price/quoteVolume 为计价币最小单位，volume 为基础币最小单位，
 * 一律 Long；windowStart 为 epoch millis（UTC 整点对齐），免时区换算。
 * </p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Accessors(chain = true)
@TableName("t_kline")
public class KlineRow extends BaseEntity {
    /** 交易对:BTC/USDT 等(与内存 store key 一致) */
    private String symbol;
    /** K线周期:1m/5m/15m/1h/4h/1d */
    private String period;
    /** 窗口开始时间(epoch millis, UTC 整点对齐) */
    private Long windowStart;
    /** 开盘价(计价币最小单位) */
    private Long open;
    /** 最高价(计价币最小单位) */
    private Long high;
    /** 最低价(计价币最小单位) */
    private Long low;
    /** 收盘价(计价币最小单位) */
    private Long close;
    /** 成交量(基础币最小单位) */
    private Long volume;
    /** 成交额(计价币最小单位) */
    private Long quoteVolume;
}
