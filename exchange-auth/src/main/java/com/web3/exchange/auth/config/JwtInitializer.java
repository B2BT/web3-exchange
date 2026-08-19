package com.web3.exchange.auth.config;

import com.web3.exchange.auth.security.jwt.JwtTokenProvider;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;

/**
 * JWT初始化配置
 */
@Configuration
@RequiredArgsConstructor
public class JwtInitializer {

    private final JwtTokenProvider jwtTokenProvider;
    private final JwtConfig jwtConfig;

    @PostConstruct
    public void init() {
        // 初始化JWT密钥
        jwtTokenProvider.init();

        // 验证密钥
        validateJwtConfig();
    }

    private void validateJwtConfig() {
        String secret = jwtConfig.getSecret();

        if (secret == null || secret.length() < 32) {
            throw new IllegalStateException("JWT密钥长度至少32位，请通过环境变量 JWT_SECRET 设置 jwt.secret");
        }

        // 生产防呆：使用开发/默认密钥时禁止以 prod profile 启动
        String profile = System.getenv("SPRING_PROFILES_ACTIVE");
        boolean prod = profile != null && profile.contains("prod");
        boolean devDefault = "your-jwt-secret-key-change-in-production".equals(secret)
                || secret.contains("please-use-at-least-64-chars");
        if (prod && devDefault) {
            throw new IllegalStateException("生产环境禁用默认/开发 JWT 密钥！请通过安全通道(环境变量/KMS/Vault)注入 JWT_SECRET");
        }
        if (devDefault) {
            System.err.println("警告：正在使用默认/开发 JWT 密钥，生产环境请通过 JWT_SECRET 注入！");
        }
    }
}