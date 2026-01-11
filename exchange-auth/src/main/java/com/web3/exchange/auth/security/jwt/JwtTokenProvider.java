package com.web3.exchange.auth.security.jwt;

import com.web3.exchange.auth.config.JwtConfig;
import com.web3.exchange.auth.dto.response.TokenPair;
import com.web3.exchange.auth.security.domain.UserPrincipal;
import com.web3.exchange.auth.service.RedisService;
import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bouncycastle.jcajce.BCFKSLoadStoreParameter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Component;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;
/**
 * JWT令牌提供者（双令牌实现）
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class JwtTokenProvider {

    private final JwtConfig jwtConfig;
    private final RedisService redisService;

    // JJWT 0.11.5 使用 Keys 生成密钥
    private SecretKey secretKey;

    /**
     * 初始化密钥
     */
    public void init() {
        this.secretKey = Keys.hmacShaKeyFor(
                jwtConfig.getSecret().getBytes(StandardCharsets.UTF_8)
        );
    }

    /**
     * 生成双令牌（Access Token + Refresh Token）
     */
    public TokenPair generateTokenPair(Authentication authentication) {
        UserPrincipal userPrincipal = (UserPrincipal) authentication.getPrincipal();

        return TokenPair.builder()
                .accessToken(generateAccessToken(userPrincipal))
                .refreshToken(generateRefreshToken(userPrincipal))
                .tokenType("Bearer")
                .accessTokenExpiresIn(jwtConfig.getAccessToken().getExpiration())
                .refreshTokenExpiresIn(jwtConfig.getRefreshToken().getExpiration())
                .build();
    }

    /**
     * 生成Access Token
     */
    public String generateAccessToken(UserPrincipal userPrincipal) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("userId", userPrincipal.getUserId());
        claims.put("username", userPrincipal.getUsername());
        claims.put("roles", userPrincipal.getRoles());
        claims.put("permissions", userPrincipal.getPermissions());
        claims.put("tokenType", "access");

        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + jwtConfig.getAccessTokenExpirationMillis());

        return Jwts.builder()
                .setClaims(claims)
                .setSubject(userPrincipal.getUsername())
                .setIssuer(jwtConfig.getIssuer())
                .setIssuedAt(now)
                .setExpiration(expiryDate)
                .signWith(secretKey, SignatureAlgorithm.HS512)
                .compact();
    }

    /**
     * 生成Refresh Token
     */
    public String generateRefreshToken(UserPrincipal userPrincipal) {
        Map<String, Object> claims = new HashMap<>();
        claims.put("userId", userPrincipal.getUserId());
        claims.put("username", userPrincipal.getUsername());
        claims.put("tokenType", "refresh");
        claims.put("jti", UUID.randomUUID().toString()); // JWT ID

        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + jwtConfig.getRefreshTokenExpirationMillis());

        String refreshToken = Jwts.builder()
                .setClaims(claims)
                .setSubject(userPrincipal.getUsername())
                .setIssuer(jwtConfig.getIssuer())
                .setIssuedAt(now)
                .setExpiration(expiryDate)
                .signWith(secretKey, SignatureAlgorithm.HS512)
                .compact();

        // 保存Refresh Token到Redis
        saveRefreshToken(refreshToken, userPrincipal.getUserId());

        return refreshToken;
    }

    /**
     * 验证Access Token
     */
    public boolean validateAccessToken(String token) {
        try {
            // 1. 检查令牌格式
            if (token == null || !token.startsWith(jwtConfig.getPrefix())) {
                return false;
            }

            // 移除前缀
            String jwt = token.substring(jwtConfig.getPrefix().length()).trim();

            // 2. 解析验证
            Jwts.parserBuilder()
                    .setSigningKey(secretKey)
                    .build()
                    .parseClaimsJws(jwt);

            // 3. 检查令牌类型
            Claims claims = getAllClaimsFromToken(jwt);
            if (!"access".equals(claims.get("tokenType"))) {
                log.warn("令牌类型错误，期望access，实际: {}", claims.get("tokenType"));
                return false;
            }

            // 4. 检查黑名单
            if (isTokenBlacklisted(jwt)) {
                log.warn("Access Token在黑名单中");
                return false;
            }

            return true;

        } catch (ExpiredJwtException e) {
            log.warn("Access Token已过期: {}", e.getMessage());
        } catch (UnsupportedJwtException e) {
            log.warn("不支持的JWT格式: {}", e.getMessage());
        } catch (MalformedJwtException e) {
            log.warn("JWT格式错误: {}", e.getMessage());
        } catch (SignatureException e) {
            log.warn("JWT签名无效: {}", e.getMessage());
        } catch (IllegalArgumentException e) {
            log.warn("JWT claims为空: {}", e.getMessage());
        } catch (Exception e) {
            log.error("验证Access Token异常", e);
        }

        return false;
    }

    /**
     * 验证Refresh Token
     */
    public boolean validateRefreshToken(String token) {
        try {
            // 1. 解析验证
            Jwts.parserBuilder()
                    .setSigningKey(secretKey)
                    .build()
                    .parseClaimsJws(token);

            // 2. 检查令牌类型
            Claims claims = getAllClaimsFromToken(token);
            if (!"refresh".equals(claims.get("tokenType"))) {
                log.warn("令牌类型错误，期望refresh，实际: {}", claims.get("tokenType"));
                return false;
            }

            // 3. 检查是否已被使用
            if (isRefreshTokenUsed(token)) {
                log.warn("Refresh Token已被使用");
                return false;
            }

            // 4. 检查是否在黑名单
            if (isTokenBlacklisted(token)) {
                log.warn("Refresh Token在黑名单中");
                return false;
            }

            return true;

        } catch (ExpiredJwtException e) {
            log.warn("Refresh Token已过期: {}", e.getMessage());
        } catch (Exception e) {
            log.error("验证Refresh Token异常", e);
        }

        return false;
    }

    /**
     * 从Access Token获取用户名
     */
    public String getUsernameFromAccessToken(String token) {
        String jwt = token.substring(jwtConfig.getPrefix().length()).trim();
        Claims claims = getAllClaimsFromToken(jwt);
        return claims.getSubject();
    }

    /**
     * 从Access Token获取用户ID
     */
    public Long getUserIdFromAccessToken(String token) {
        String jwt = token.substring(jwtConfig.getPrefix().length()).trim();
        Claims claims = getAllClaimsFromToken(jwt);
        return claims.get("userId", Long.class);
    }

    /**
     * 从Refresh Token获取用户信息
     */
    public RefreshTokenInfo getRefreshTokenInfo(String token) {
        Claims claims = getAllClaimsFromToken(token);

        return RefreshTokenInfo.builder()
                .userId(claims.get("userId", Long.class))
                .username(claims.getSubject())
                .jti(claims.get("jti", String.class))
                .issuedAt(claims.getIssuedAt())
                .expiration(claims.getExpiration())
                .build();
    }

    /**
     * 刷新Access Token
     */
    public String refreshAccessToken(String refreshToken) {
        try {
            // 验证Refresh Token
            if (!validateRefreshToken(refreshToken)) {
                throw new JwtException("Refresh Token无效");
            }

            // 获取用户信息
            RefreshTokenInfo refreshTokenInfo = getRefreshTokenInfo(refreshToken);

            // 标记Refresh Token已使用（如果是单次使用）
            if (jwtConfig.getRefreshToken().isSingleUse()) {
                markRefreshTokenUsed(refreshToken);
            }

            // 生成新的Access Token
            Map<String, Object> claims = new HashMap<>();
            claims.put("userId", refreshTokenInfo.getUserId());
            claims.put("username", refreshTokenInfo.getUsername());
            claims.put("tokenType", "access");

            Date now = new Date();
            Date expiryDate = new Date(now.getTime() + jwtConfig.getAccessTokenExpirationMillis());

            return Jwts.builder()
                    .setClaims(claims)
                    .setSubject(refreshTokenInfo.getUsername())
                    .setIssuer(jwtConfig.getIssuer())
                    .setIssuedAt(now)
                    .setExpiration(expiryDate)
                    .signWith(secretKey, SignatureAlgorithm.HS512)
                    .compact();

        } catch (Exception e) {
            log.error("刷新Access Token失败", e);
            throw new JwtException("刷新令牌失败: " + e.getMessage());
        }
    }

    /**
     * 刷新双令牌
     */
    public TokenPair refreshTokenPair(String refreshToken) {
        try {
            // 验证Refresh Token
            if (!validateRefreshToken(refreshToken)) {
                throw new JwtException("Refresh Token无效");
            }

            // 获取用户信息
            RefreshTokenInfo refreshTokenInfo = getRefreshTokenInfo(refreshToken);

            // 标记Refresh Token已使用
            markRefreshTokenUsed(refreshToken);

            // 创建认证对象
            UserPrincipal userPrincipal = UserPrincipal.builder()
                    .userId(refreshTokenInfo.getUserId())
                    .username(refreshTokenInfo.getUsername())
                    .build();

            // 生成新的双令牌
            TokenPair tokenPair = TokenPair.builder()
                    .accessToken(generateAccessToken(userPrincipal))
                    .refreshToken(generateRefreshToken(userPrincipal))
                    .tokenType("Bearer")
                    .accessTokenExpiresIn(jwtConfig.getAccessToken().getExpiration())
                    .refreshTokenExpiresIn(jwtConfig.getRefreshToken().getExpiration())
                    .build();

            return tokenPair;

        } catch (Exception e) {
            log.error("刷新双令牌失败", e);
            throw new JwtException("刷新令牌失败: " + e.getMessage());
        }
    }

    /**
     * 获取令牌中的所有声明
     */
    private Claims getAllClaimsFromToken(String token) {
        return Jwts.parserBuilder()
                .setSigningKey(secretKey)
                .build()
                .parseClaimsJws(token)
                .getBody();
    }

    /**
     * 保存Refresh Token到Redis
     */
    private void saveRefreshToken(String refreshToken, Long userId) {
        try {
            Claims claims = getAllClaimsFromToken(refreshToken);
            String jti = claims.get("jti", String.class);

            // 存储结构: refresh_token:{jti} -> {userId}:{issuedAt}:{expiration}
            String value = String.format("%s:%d:%d",
                    userId,
                    claims.getIssuedAt().getTime(),
                    claims.getExpiration().getTime());

            long ttl = (claims.getExpiration().getTime() - System.currentTimeMillis()) / 1000;

            redisService.set("refresh_token:" + jti, value, ttl);

            // 同时记录用户与Refresh Token的关系
            redisService.sAdd("user_refresh_tokens:" + userId, jti);
            redisService.expire("user_refresh_tokens:" + userId, ttl);

        } catch (Exception e) {
            log.error("保存Refresh Token失败", e);
        }
    }

    /**
     * 标记Refresh Token已使用
     */
    private void markRefreshTokenUsed(String refreshToken) {
        try {
            Claims claims = getAllClaimsFromToken(refreshToken);
            String jti = claims.get("jti", String.class);

            // 标记为已使用
            redisService.set("refresh_token_used:" + jti, "1",
                    jwtConfig.getRefreshTokenExpirationMillis() / 1000);

        } catch (Exception e) {
            log.error("标记Refresh Token已使用失败", e);
        }
    }

    /**
     * 检查Refresh Token是否已使用
     */
    private boolean isRefreshTokenUsed(String refreshToken) {
        try {
            Claims claims = getAllClaimsFromToken(refreshToken);
            String jti = claims.get("jti", String.class);

            return redisService.exists("refresh_token_used:" + jti);
        } catch (Exception e) {
            log.error("检查Refresh Token使用状态失败", e);
            return true; // 如果检查失败，认为已使用
        }
    }

    /**
     * 检查令牌是否在黑名单
     */
    private boolean isTokenBlacklisted(String token) {
        try {
            // 计算token的hash作为key
            String tokenHash = Integer.toHexString(token.hashCode());
            return redisService.exists("token_blacklist:" + tokenHash);
        } catch (Exception e) {
            log.error("检查令牌黑名单失败", e);
            return false;
        }
    }

    /**
     * 将令牌加入黑名单
     */
    public void blacklistToken(String token, long ttlSeconds) {
        try {
            String tokenHash = Integer.toHexString(token.hashCode());
            redisService.set("token_blacklist:" + tokenHash, "1", ttlSeconds);
        } catch (Exception e) {
            log.error("添加令牌到黑名单失败", e);
        }
    }

    /**
     * 撤销用户的所有Refresh Token
     */
    public void revokeAllRefreshTokens(Long userId) {
        try {
            String key = "user_refresh_tokens:" + userId;
            Set<String> jtis = redisService.sMembers(key);

            if (jtis != null) {
                for (String jti : jtis) {
                    // 删除Refresh Token
                    redisService.delete("refresh_token:" + jti);
                    // 标记为已使用
                    redisService.set("refresh_token_used:" + jti, "1", 3600);
                }
            }

            // 删除用户关联集合
            redisService.delete(key);

        } catch (Exception e) {
            log.error("撤销用户Refresh Token失败", e);
        }
    }

    /**
     * 检查Access Token是否即将过期
     */
    public boolean isAccessTokenExpiringSoon(String token) {
        try {
            String jwt = token.substring(jwtConfig.getPrefix().length()).trim();
            Claims claims = getAllClaimsFromToken(jwt);

            Date expiration = claims.getExpiration();
            long timeUntilExpiry = expiration.getTime() - System.currentTimeMillis();

            return timeUntilExpiry <= jwtConfig.getAccessTokenRefreshThresholdMillis();
        } catch (Exception e) {
            log.error("检查令牌过期时间失败", e);
            return false;
        }
    }

    /**
     * Refresh Token信息
     */
    @Data
    @Builder
    public static class RefreshTokenInfo {
        private Long userId;
        private String username;
        private String jti;
        private Date issuedAt;
        private Date expiration;
    }
}