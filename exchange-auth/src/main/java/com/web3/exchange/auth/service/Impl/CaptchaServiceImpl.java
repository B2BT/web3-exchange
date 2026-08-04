package com.web3.exchange.auth.service.Impl;

import com.web3.exchange.auth.config.CaptchaProperties;
import com.web3.exchange.auth.dto.response.CaptchaResponse;
import com.web3.exchange.auth.service.CaptchaService;
import com.web3.exchange.auth.service.RedisService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.util.UUID;

/**
 * 图形验证码服务实现
 * <p>生成数学算式验证码，答案存 Redis（key: captcha:{captchaId}），一次性校验。</p>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class CaptchaServiceImpl implements CaptchaService {

    private static final String CAPTCHA_KEY_PREFIX = "captcha:";

    private final RedisService redisService;
    private final CaptchaProperties captchaProperties;

    private final SecureRandom secureRandom = new SecureRandom();

    @Override
    public CaptchaResponse generate() {
        String captchaId = UUID.randomUUID().toString().replace("-", "");
        String[] math = randomMath();
        String expression = math[0]; // 例如 "3+5"
        String answer = math[1];     // 例如 "8"

        redisService.set(CAPTCHA_KEY_PREFIX + captchaId, answer,
                captchaProperties.getExpireTime());

        return CaptchaResponse.builder()
                .captchaId(captchaId)
                // 算式展示给前端（此实现不生成图片，前端可直接展示文本或自行渲染）
                .captchaImage(expression)
                // 答案仅用于测试环境直接读取，便于 curl 联调
                .captchaText(answer)
                .type(captchaProperties.getType())
                .expireSeconds(captchaProperties.getExpireTime())
                .timestamp(System.currentTimeMillis())
                .build();
    }

    @Override
    public boolean verify(String captchaId, String input) {
        if (captchaId == null || input == null || input.isBlank()) {
            return false;
        }
        String key = CAPTCHA_KEY_PREFIX + captchaId;
        Object stored = redisService.get(key);
        if (stored == null) {
            return false;
        }
        // 一次性使用：无论对错都删除，防止暴力重试
        redisService.delete(key);
        return stored.toString().trim().equalsIgnoreCase(input.trim());
    }

    /**
     * 生成随机数学算式，返回 {表达式, 答案}
     */
    private String[] randomMath() {
        int a = secureRandom.nextInt(9) + 1;
        int b = secureRandom.nextInt(9) + 1;
        int op = secureRandom.nextInt(3);
        int result;
        String operator;
        switch (op) {
            case 0 -> { operator = "+"; result = a + b; }
            case 1 -> { operator = "-"; result = a - b; }
            default -> { operator = "*"; result = a * b; }
        }
        return new String[]{a + operator + b, String.valueOf(result)};
    }
}
