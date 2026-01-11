package com.web3.exchange.auth.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * JWT配置类
 */
@Data
@Component
@ConfigurationProperties(prefix = "jwt")
public class JwtConfig {

    // 密钥
    private String secret = "your-jwt-secret-key-change-in-production";

    // Access Token配置
    private AccessToken accessToken = new AccessToken();

    // Refresh Token配置
    private RefreshToken refreshToken = new RefreshToken();

    // 签发者
    private String issuer = "web3-auth-service";

    // 令牌前缀
    private String prefix = "Bearer ";

    // 令牌头
    private String header = "Authorization";

    // Refresh Token头
    private String refreshHeader = "X-Refresh-Token";

    /**
     * Access Token配置
     */
    @Data
    public static class AccessToken {
        // 过期时间（秒）
        private long expiration = 7200; // 2小时
        // 刷新阈值（秒），在过期前多久可以刷新
        private long refreshThreshold = 300; // 5分钟
    }

    /**
     * Refresh Token配置
     */
    @Data
    public static class RefreshToken {
        // 过期时间（秒）
        private long expiration = 2592000; // 30天
        // 最大使用次数
        private int maxUsage = 10;
        // 是否单次使用
        private boolean singleUse = false;
    }

    /**
     * 获取Access Token过期时间（毫秒）
     */
    public long getAccessTokenExpirationMillis() {
        return Duration.ofSeconds(accessToken.getExpiration()).toMillis();
    }

    /**
     * 获取Refresh Token过期时间（毫秒）
     */
    public long getRefreshTokenExpirationMillis() {
        return Duration.ofSeconds(refreshToken.getExpiration()).toMillis();
    }

    /**
     * 获取Access Token刷新阈值（毫秒）
     */
    public long getAccessTokenRefreshThresholdMillis() {
        return Duration.ofSeconds(accessToken.getRefreshThreshold()).toMillis();
    }
}

