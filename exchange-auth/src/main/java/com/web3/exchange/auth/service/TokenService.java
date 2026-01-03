package com.web3.exchange.auth.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import java.util.Set;
import java.util.concurrent.TimeUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class TokenService {

    private final RedisTemplate<String, String> redisTemplate;

    // Token 存储前缀
    private static final String ACCESS_TOKEN_PREFIX = "access_token:";
    private static final String REFRESH_TOKEN_PREFIX = "refresh_token:";
    private static final String TOKEN_BLACKLIST_PREFIX = "blacklist:";
    private static final String USER_TOKEN_PREFIX = "user_token:";

    // 存储 Token
    public void storeToken(String username, String accessToken, String refreshToken) {
        String accessKey = ACCESS_TOKEN_PREFIX + accessToken;
        String refreshKey = REFRESH_TOKEN_PREFIX + refreshToken;
        String userKey = USER_TOKEN_PREFIX + username;

        // 存储 Access Token (1小时)
        redisTemplate.opsForValue().set(accessKey, username, 1, TimeUnit.HOURS);

        // 存储 Refresh Token (7天)
        redisTemplate.opsForValue().set(refreshKey, username, 7, TimeUnit.DAYS);

        // 关联用户和 Token
        redisTemplate.opsForValue().set(userKey, accessToken, 1, TimeUnit.HOURS);

        // 存储用户的所有 Token
        String userTokensKey = "user_tokens:" + username;
        redisTemplate.opsForSet().add(userTokensKey, accessToken);
        redisTemplate.expire(userTokensKey, 1, TimeUnit.HOURS);
    }

    // 获取 Refresh Token
    public String getRefreshToken(String username) {
        String key = REFRESH_TOKEN_PREFIX + username;
        return redisTemplate.opsForValue().get(key);
    }

    // 更新 Access Token
    public void updateAccessToken(String username, String newAccessToken) {
        String oldAccessToken = redisTemplate.opsForValue().get(USER_TOKEN_PREFIX + username);
        if (oldAccessToken != null) {
            // 将旧的 Access Token 加入黑名单
            blacklistToken(oldAccessToken);
        }

        // 存储新的 Access Token
        String accessKey = ACCESS_TOKEN_PREFIX + newAccessToken;
        String userKey = USER_TOKEN_PREFIX + username;

        redisTemplate.opsForValue().set(accessKey, username, 1, TimeUnit.HOURS);
        redisTemplate.opsForValue().set(userKey, newAccessToken, 1, TimeUnit.HOURS);
    }

    // 将 Token 加入黑名单
    public void blacklistToken(String token) {
        String key = TOKEN_BLACKLIST_PREFIX + token;
        redisTemplate.opsForValue().set(key, "blacklisted", 1, TimeUnit.HOURS);
    }

    // 检查 Token 是否在黑名单中
    public boolean isTokenBlacklisted(String token) {
        String key = TOKEN_BLACKLIST_PREFIX + token;
        return redisTemplate.hasKey(key);
    }

    // 删除 Refresh Token
    public void deleteRefreshToken(String username) {
        String key = REFRESH_TOKEN_PREFIX + username;
        redisTemplate.delete(key);
    }

    // 删除用户的所有 Token
    public void deleteUserTokens(String username) {
        String userKey = USER_TOKEN_PREFIX + username;
        String accessToken = redisTemplate.opsForValue().get(userKey);

        if (accessToken != null) {
            redisTemplate.delete(ACCESS_TOKEN_PREFIX + accessToken);
        }

        redisTemplate.delete(userKey);
        redisTemplate.delete(REFRESH_TOKEN_PREFIX + username);

        // 删除用户的所有 Token 集合
        String userTokensKey = "user_tokens:" + username;
        Set<String> tokens = redisTemplate.opsForSet().members(userTokensKey);
        if (tokens != null) {
            tokens.forEach(this::blacklistToken);
        }
        redisTemplate.delete(userTokensKey);
    }
}