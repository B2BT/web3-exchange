package com.web3.exchange.risk.dto;

import lombok.Data;

/**
 * 提现二次验证请求（chain → risk）。
 */
@Data
public class WithdrawVerifyRequest {
    /** 用户ID */
    private Long userId;
    /** 提现ID */
    private Long withdrawId;
    /** 用户输入的验证码 */
    private String verifyCode;
}
