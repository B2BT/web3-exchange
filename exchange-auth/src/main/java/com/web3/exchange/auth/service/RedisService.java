package com.web3.exchange.auth.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Set;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
@Slf4j
public class RedisService {

    private final RedisTemplate<String, Object> redisTemplate;

    /**
     * 设置值
     */
    public void set(String key, Object value, long timeout, TimeUnit unit) {
        try {
            redisTemplate.opsForValue().set(key, value, timeout, unit);
        } catch (Exception e) {
            log.error("Redis设置值失败 key: {}", key, e);
        }
    }

    /**
     * 设置值（秒）
     */
    public void set(String key, Object value, long timeoutSeconds) {
        set(key, value, timeoutSeconds, TimeUnit.SECONDS);
    }

    /**
     * 获取值
     */
    public Object get(String key) {
        try {
            return redisTemplate.opsForValue().get(key);
        } catch (Exception e) {
            log.error("Redis获取值失败 key: {}", key, e);
            return null;
        }
    }

    /**
     * 删除key
     */
    public boolean delete(String key) {
        try {
            return Boolean.TRUE.equals(redisTemplate.delete(key));
        } catch (Exception e) {
            log.error("Redis删除key失败 key: {}", key, e);
            return false;
        }
    }

    /**
     * 设置过期时间
     */
    public boolean expire(String key, long timeout, TimeUnit unit) {
        try {
            return Boolean.TRUE.equals(redisTemplate.expire(key, timeout, unit));
        } catch (Exception e) {
            log.error("Redis设置过期时间失败 key: {}", key, e);
            return false;
        }
    }

    /**
     * 检查key是否存在
     */
    public boolean exists(String key) {
        try {
            return Boolean.TRUE.equals(redisTemplate.hasKey(key));
        } catch (Exception e) {
            log.error("Redis检查key存在失败 key: {}", key, e);
            return false;
        }
    }

    /**
     * 添加到集合
     */
    public Long sAdd(String key, String... values) {
        try {
            return redisTemplate.opsForSet().add(key, (Object[]) values);
        } catch (Exception e) {
            log.error("Redis添加到集合失败 key: {}", key, e);
            return 0L;
        }
    }

    /**
     * 获取集合所有成员
     */
    public Set<String> sMembers(String key) {
        try {
            return (Set<String>) redisTemplate.opsForSet().members(key);
        } catch (Exception e) {
            log.error("Redis获取集合成员失败 key: {}", key, e);
            return null;
        }
    }

    /**
     * 从集合移除成员
     */
    public Long sRem(String key, Object... values) {
        try {
            return redisTemplate.opsForSet().remove(key, values);
        } catch (Exception e) {
            log.error("Redis从集合移除成员失败 key: {}", key, e);
            return 0L;
        }
    }

    /**
     * 设置过期时间（秒）
     */
    public boolean expire(String key, long timeoutSeconds) {
        return expire(key, timeoutSeconds, TimeUnit.SECONDS);
    }
}