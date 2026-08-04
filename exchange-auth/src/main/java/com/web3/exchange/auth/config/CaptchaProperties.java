package com.web3.exchange.auth.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 图形验证码配置
 * <p>对应 application.yml 中的 captcha.* 配置。</p>
 */
@Data
@Component
@ConfigurationProperties(prefix = "captcha")
public class CaptchaProperties {

    /** 是否启用验证码（true：登录/注册需校验验证码） */
    private boolean enabled = true;

    /** 验证码类型：math-数学算式, string-随机字符串 */
    private String type = "math";

    /** 字符串验证码长度（math 类型忽略） */
    private int length = 4;

    /** 图片宽高（预留，未生成图片时忽略） */
    private int width = 130;
    private int height = 48;

    /** 验证码有效期（秒） */
    private int expireTime = 300;

    /** 防刷间隔（秒，预留） */
    private int antiBrushInterval = 60;
}
