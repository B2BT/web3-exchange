package com.web3.exchange.common.monitor;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * 实现了一个基于 Redis 的异常监控告警系统，其核心目的是：防止异常信息淹没告警通道，并实现异常发生的“限流监控”。
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class ExceptionMonitor {

    private final RedisTemplate<String, Object> redisTemplate;
    // 你可以把它想象成一个“翻译官”，专门负责 Java 对象与 JSON 数据之间的双向转换。
    private final ObjectMapper objectMapper;

    // 配置
    @Value("${exception.monitor.enabled:true}")
    private boolean enabled;

    @Value("${exception.monitor.threshold:10}")
    private int threshold;

    @Value("${exception.monitor.window:60}")
    private int windowSeconds;

    public void monitor(Exception e, HttpServletRequest request) {
        if (!enabled) {
            return;
        }
        try {
            String exceptionType = e.getClass().getSimpleName();
            String key = "exception:monitor:" + exceptionType;

            // 定义 Lua 脚本，解决原子性问题
            String luaScript =
                    "local count = redis.call('incr', KEYS[1]) " +
                            "if count == 1 then " +
                            "  redis.call('expire', KEYS[1], ARGV[1]) " +
                            "end " +
                            "return count";

            // 执行脚本
            Long count = redisTemplate.execute(
                    new DefaultRedisScript<>(luaScript, Long.class),
                    Collections.singletonList(key),
                    String.valueOf(windowSeconds)
            );

            // 超过阈值触发告警
            if (count >= threshold) {
                triggerAlert(e, request, count);
            }

            // 记录异常详情
            if (log.isErrorEnabled()) {
                Map<String, Object> errorDetail = buildErrorDetail(e, request);
                log.error("异常监控: {}", objectMapper.writeValueAsString(errorDetail));

            }
        } catch (Exception ex) {
            log.error("异常监控失败", ex);
        }


    }
    /**
     * 构建异常详情
     */
    private Map<String, Object> buildErrorDetail(Exception e, HttpServletRequest request) {
        Map<String, Object> detail = new HashMap<>();
        detail.put("timestamp", System.currentTimeMillis());
        detail.put("exception", e.getClass().getName());
        detail.put("message", e.getMessage());
        detail.put("path", request.getRequestURI());
        detail.put("method", request.getMethod());
        detail.put("query", request.getQueryString());
        detail.put("ip", getClientIp(request));
        detail.put("userAgent", request.getHeader("User-Agent"));
        detail.put("referer", request.getHeader("Referer"));

        // 堆栈跟踪
        if (log.isDebugEnabled()) {
            StringWriter sw = new StringWriter();
            PrintWriter pw = new PrintWriter(sw);
            e.printStackTrace(pw);
            detail.put("stackTrace", sw.toString());
        }

        return detail;
    }

    /**
     * 触发告警
     *
     * @param e
     * @param request
     * @param count
     */

    private void triggerAlert(Exception e, HttpServletRequest request, Long count) {
        try {
            HashMap<String, Object> alert = new HashMap<>();
            alert.put("timestamp", System.currentTimeMillis());
            alert.put("exceptionType", e.getClass().getName());
            alert.put("message", e.getMessage());
            alert.put("count", count);
            alert.put("threshold", threshold);
            alert.put("window", windowSeconds);
            alert.put("path", request.getRequestURI());
            alert.put("method", request.getMethod());
            alert.put("ip", getClientIp(request));

            sendAlert(alert);

            log.warn("异常告警触发: {} 在 {} 秒内发生了 {} 次",
                    e.getClass().getSimpleName(), windowSeconds, count);
        } catch (Exception ex) {
            log.error("触发告警失败", ex);
        }

    }

    /**
     * 发送告警
     */
    private void sendAlert(Map<String, Object> alert) {
        // 实现告警发送逻辑 todo
        // 1. 发送到消息队列
        // 2. 发送邮件
        // 3. 发送到钉钉/企业微信
        // 4. 记录到数据库

        log.info("发送告警: {}", alert);
    }

    /**
     * 获取客户端IP
     */
    private String getClientIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.length() == 0 || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("Proxy-Client-IP");
        }
        if (ip == null || ip.length() == 0 || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("WL-Proxy-Client-IP");
        }
        if (ip == null || ip.length() == 0 || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }
        return ip;
    }
}
