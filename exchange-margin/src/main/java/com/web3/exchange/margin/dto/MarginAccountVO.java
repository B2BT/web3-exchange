package com.web3.exchange.margin.dto;

import lombok.Data;

/**
 * 杠杆账户详情（金额最小单位 Long）。
 */
@Data
public class MarginAccountVO {
    /** 账户ID（雪花，String 防 JS 精度丢失） */
    private Long id;
    /** 用户ID */
    private Long userId;
    /** 币种符号 */
    private String symbol;
    /** 抵押 */
    private Long collateral;
    /** 借入本金 */
    private Long borrowed;
    /** 未还利息 */
    private Long interestAccrued;
    /** 风险率(百分数, borrowed=0 时为 null/∞) */
    private Long riskRate;
    /** 状态 */
    private Integer status;
}
