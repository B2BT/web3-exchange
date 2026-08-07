package com.web3.exchange.risk.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 风控业务配置（risk.*）。
 */
@Data
@Component
@ConfigurationProperties(prefix = "risk")
public class RiskProperties {
    /** 二次验证码有效期(分钟) */
    private int verifyTtlMinutes = 10;
    /** 验证码长度 */
    private int verifyCodeLength = 6;
}
