package com.web3.exchange.risk.dto;

import lombok.Data;

/**
 * 下单风控校验结果。
 */
@Data
public class OrderRiskResult {
    /** 是否通过 */
    private boolean pass;
    /** 拦截原因(通过时为空) */
    private String reason;
}
