package com.web3.exchange.margin.dto;

import lombok.Data;

/**
 * 借币/还币请求。
 */
@Data
public class MarginBorrowRequest {
    /** 用户ID */
    private Long userId;
    /** 币种符号 */
    private String symbol;
    /** 金额(最小单位) */
    private Long amount;
}
