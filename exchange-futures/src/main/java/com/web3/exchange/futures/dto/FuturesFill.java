package com.web3.exchange.futures.dto;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 合约成交记录（内存撮合产出，供仓位/账户核算）。
 */
@Data
public class FuturesFill implements Serializable {
    /** 成交用户（taker 或 maker） */
    private Long userId;
    /** 成交价（最小单位） */
    private Long price;
    /** 成交数量（最小单位） */
    private Long quantity;
    /** 该笔成交方向（1开多 2开空 3平多 4平空） */
    private Integer side;
}
