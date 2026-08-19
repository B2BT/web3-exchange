package com.web3.exchange.futures.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 永续合约成交明细（逐笔成交历史，重启不丢失）。
 */
@Data
@TableName("t_futures_fill")
public class FuturesFillEntity implements Serializable {
    private static final long serialVersionUID = 1L;

    @TableId(type = IdType.AUTO)
    private Long id;
    /** 来源合约订单号（taker 实际落库单号） */
    private String orderNo;
    /** 成交用户（taker 或 maker） */
    private Long userId;
    /** 对手方用户 */
    private Long counterUserId;
    /** 交易对 */
    private String symbol;
    /** 成交方向：1开多 2开空 3平多 4平空 */
    private Integer side;
    /** 成交价（最小单位） */
    private Long price;
    /** 成交数量（最小单位） */
    private Long quantity;
    /** 名义金额（最小单位 = qty×price/1e8） */
    private Long notional;
    /** 手续费（最小单位） */
    private Long fee;
    /** 0=taker 1=maker */
    private Integer tradeRole;
    /** 成交时间 */
    private LocalDateTime createTime;
}
