package com.web3.exchange.risk.dto;

import lombok.Data;

/**
 * 反钓鱼码请求。
 */
@Data
public class PhishingRequest {
    /** 用户ID */
    private Long userId;
    /** 反钓鱼短语 */
    private String phrase;
}
