package com.web3.exchange.risk.dto;

import lombok.Data;

/**
 * 提现前置校验请求（chain → risk）。
 */
@Data
public class WithdrawRiskRequest {
    /** 用户ID */
    private Long userId;
    /** 提现ID */
    private Long withdrawId;
    /** 提现币种 */
    private String symbol;
    /** 提现金额(最小单位) */
    private Long amount;
    /** 反钓鱼码(提现时需回显并校验) */
    private String phrase;
    /** 提现收款地址（用于 AML 制裁名单核验） */
    private String toAddress;
}
