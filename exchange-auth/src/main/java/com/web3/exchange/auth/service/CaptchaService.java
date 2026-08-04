package com.web3.exchange.auth.service;

import com.web3.exchange.auth.dto.response.CaptchaResponse;

/**
 * 图形验证码服务
 * <p>生成图形验证码（数学算式），存 Redis 供登录/注册校验。</p>
 */
public interface CaptchaService {

    /**
     * 生成验证码：返回 captchaId（key）+ 算式/内容，答案存入 Redis。
     */
    CaptchaResponse generate();

    /**
     * 校验验证码（一次性：校验后删除）。
     *
     * @param captchaId 验证码 ID（key）
     * @param input     用户输入
     * @return 是否校验通过
     */
    boolean verify(String captchaId, String input);
}
