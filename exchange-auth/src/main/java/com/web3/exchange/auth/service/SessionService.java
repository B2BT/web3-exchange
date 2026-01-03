package com.web3.exchange.auth.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import java.util.Set;
import java.util.concurrent.TimeUnit;

@Service
@RequiredArgsConstructor
public class SessionService {

    private final RedisTemplate<String, String> redisTemplate;

    // 创建会话
    public void createSession(String sessionId, String username, int timeout) {
        String key = "session:" + sessionId;
        redisTemplate.opsForValue().set(key, username, timeout, TimeUnit.MINUTES);

        // 存储用户的所有会话
        String userSessionsKey = "user_sessions:" + username;
        redisTemplate.opsForSet().add(userSessionsKey, sessionId);
        redisTemplate.expire(userSessionsKey, 24, TimeUnit.HOURS);
    }

    // 验证会话
    public boolean validateSession(String sessionId) {
        String key = "session:" + sessionId;
        return Boolean.TRUE.equals(redisTemplate.hasKey(key));
    }

    // 获取会话用户
    public String getSessionUser(String sessionId) {
        String key = "session:" + sessionId;
        return redisTemplate.opsForValue().get(key);
    }

    // 删除会话
    public void deleteSession(String sessionId) {
        String key = "session:" + sessionId;
        String username = redisTemplate.opsForValue().get(key);
        redisTemplate.delete(key);

        if (username != null) {
            String userSessionsKey = "user_sessions:" + username;
            redisTemplate.opsForSet().remove(userSessionsKey, sessionId);
        }
    }

    // 删除用户的所有会话
    public void deleteUserSessions(String username) {
        String userSessionsKey = "user_sessions:" + username;
        Set<String> sessions = redisTemplate.opsForSet().members(userSessionsKey);

        if (sessions != null) {
            sessions.forEach(sessionId -> {
                redisTemplate.delete("session:" + sessionId);
            });
        }

        redisTemplate.delete(userSessionsKey);
    }
}