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

    @PostConstruct
    public void init() {
        // 初始化JWT密钥
        jwtTokenProvider.init();

        // 验证密钥
        validateJwtConfig();
    }

    private void validateJwtConfig() {
        JwtConfig jwtConfig = new JwtConfig();
        String secret = jwtConfig.getSecret();

        if (secret == null || secret.length() < 32) {
            throw new IllegalStateException("JWT密钥长度至少32位，请在配置文件中设置jwt.secret");
        }

        if ("your-jwt-secret-key-change-in-production".equals(secret)) {
            System.err.println("警告：正在使用默认JWT密钥，生产环境请修改！");
        }
    }
}