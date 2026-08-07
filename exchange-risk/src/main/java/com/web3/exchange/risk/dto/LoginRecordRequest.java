package com.web3.exchange.risk.dto;

import lombok.Data;

/**
 * 登录记录请求（auth → risk）。
 */
@Data
public class LoginRecordRequest {
    /** 用户ID */
    private Long userId;
    /** 用户名 */
    private String username;
    /** 登录IP */
    private String ip;
    /** UA */
    private String userAgent;
    /** 结果:0=成功 1=失败 */
    private Integer result;
}
