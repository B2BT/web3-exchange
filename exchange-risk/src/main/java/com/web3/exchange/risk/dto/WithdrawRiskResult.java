package com.web3.exchange.risk.dto;

import lombok.Data;

/**
 * 提现前置校验结果。
 */
@Data
public class WithdrawRiskResult {
    /** 是否通过 */
    private boolean pass;
    /** 是否需二次验证码 */
    private boolean needVerify;
    /** 二次验证码(生产应发邮箱/短信；本 MVP 明文返回便于测试) */
    private String verifyCode;
    /** 拦截/提示原因 */
    private String reason;
}
