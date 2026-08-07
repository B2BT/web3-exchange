package com.web3.exchange.staking.dto;

import lombok.Data;

/**
 * 质押/赎回请求。
 */
@Data
public class StakingRequest {
    /** 用户ID */
    private Long userId;
    /** 产品编码 */
    private String productCode;
    /** 金额(最小单位) */
    private Long amount;
}
