package com.web3.exchange.auth.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * @RequiredArgsConstructor:在 Spring 框架中，这是实现构造器注入（Constructor Injection）最简洁的方式。
 * 优势：相比于 @Autowired 字段注入，构造器注入更推荐，因为它能保证依赖项不可变（final），且方便进行单元测试 Spring 官方文档。
 */
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

    /**
     * 存入Token，使用双Token验证机制与用户会话管理机制，支持自动注销和强制下线，删除Redis里的Key后立即失效
     * @param username
     * @param accessToken 用于日常接口访问，即使泄漏，黑客可利用的时间窗口短
     * @param refreshToken 当AccessToken过期后，可以使用RefreshToken自动换取新的Access Token，
     *                     7天代表一周内活跃一次就可以保证登录状态
     */
    public void storeToken(String username, String accessToken, String refreshToken) {
        String accessKey = ACCESS_TOKEN_PREFIX + accessToken;
        String refreshKey = REFRESH_TOKEN_PREFIX + refreshToken;
        String userKey = USER_TOKEN_PREFIX + username;

        // 存储 Access Token （1小时）
        redisTemplate.opsForValue().set(accessKey, username,1, TimeUnit.HOURS);

        // 存储 Refresh Token （7天）
        redisTemplate.opsForValue().set(refreshKey, username,7, TimeUnit.DAYS);

        // 关联用户和Token，便于通过用户快速获取当前登录状态
        redisTemplate.opsForValue().set(userKey, username,1, TimeUnit.HOURS);

        // 存储用户的所有token，使用opsForSet存储集合，用于多端登录管理
        String userTokenKey = "user_tokens:"+ username;
        redisTemplate.opsForSet().add(userTokenKey, accessToken);
        redisTemplate.expire(userTokenKey,1, TimeUnit.HOURS);
    }

    // 获取Refresh Token
    public String getRefreshToken(String username){
        String key = REFRESH_TOKEN_PREFIX +username;
        return redisTemplate.opsForValue().get(key);
    }

    // 更新Access Token
    public void updateAccessToken(String username, String newAccessToken){
        String oldAccessToken = redisTemplate.opsForValue().get(USER_TOKEN_PREFIX + username);
        if(oldAccessToken != null){
            // 将旧的AccessToken加入黑名单
            balcklistToken(oldAccessToken);
        }
        // 存储新的Access Token，accessKey给过滤器和拦截器使用，验证Token是否合法
        String accessKey = ACCESS_TOKEN_PREFIX + newAccessToken;
        // 给管理员和业务逻辑使用，用来管理用户登录状态
        String userKey = USER_TOKEN_PREFIX + username;
        redisTemplate.opsForValue().set(accessKey,username,1,TimeUnit.HOURS);
        redisTemplate.opsForValue().set(userKey,newAccessToken,1,TimeUnit.HOURS);
    }
    // 将Token加入黑名单
    public void balcklistToken(String token) {
       String key = TOKEN_BLACKLIST_PREFIX + token;
       redisTemplate.opsForValue().set(key,"blacklisted",1,TimeUnit.HOURS);
    }

    // 检查Token是否在黑名单中
    public boolean isTokenBlacklisted(String token){
        String key = TOKEN_BLACKLIST_PREFIX + token;
        return redisTemplate.hasKey(key);
    }

    // 删除 Refresh Token
    public void delelteRefreshToken(String username){
        String key = REFRESH_TOKEN_PREFIX + username;

    }

    // 删除用户所有Token
    public void deleteUserTokens(String username){
        String userKey = USER_TOKEN_PREFIX + username;
        String accessToken = redisTemplate.opsForValue().get(userKey);

        if(accessToken != null){
            redisTemplate.delete(ACCESS_TOKEN_PREFIX + accessToken);
        }
        redisTemplate.delete(userKey);
        redisTemplate.delete(REFRESH_TOKEN_PREFIX + userKey);

        // 删除用户所有的Token集合
        String userTokenKey = "user_tokens:" + username;
        Set<String> tokens = redisTemplate.opsForSet().members(userTokenKey);
        if (tokens != null) {
            tokens.forEach(this::balcklistToken);
        }
        redisTemplate.delete(userTokenKey);
    }

}
