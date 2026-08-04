package com.web3.exchange.gateway.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.web3.exchange.common.model.Result;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 全局 JWT 鉴权过滤器（响应式 WebFlux 实现）
 * <p>
 * 作用：
 * 1. 白名单路径直接放行（登录、注册、验证码、健康检查）；
 * 2. 从 Authorization: Bearer xxx 中提取并校验 JWT；
 * 3. 校验失败返回 401 统一 JSON（基于 exchange-common 的 Result）；
 * 4. 校验成功后将用户信息写入下游请求头 X-User-Id / X-User-Name / X-Authorities。
 */
@Slf4j
@Component
public class AuthFilter implements GlobalFilter, Ordered {

    /**
     * 免鉴权路径前缀
     */
    private static final Set<String> SKIP_PATHS = Set.of(
            "/api/auth/login",
            "/api/auth/register",
            "/api/auth/captcha",
            "/api/auth/reset-password",
            "/actuator/health"
    );

    @Value("${jwt.secret}")
    private String jwtSecret;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String path = request.getPath().value();

        // 1. 白名单放行
        if (isSkipPath(path)) {
            return chain.filter(exchange);
        }

        // 2. 提取 Bearer Token
        String token = extractToken(request);
        if (!StringUtils.hasText(token)) {
            return unauthorized(exchange, "缺少Token或Token格式不正确");
        }

        // 3. 解析并校验 JWT
        Claims claims = parseToken(token);
        if (claims == null) {
            return unauthorized(exchange, "Token无效或已过期");
        }

        // 4. 解析用户信息
        Long userId = claims.get("userId", Long.class);
        String username = claims.getSubject();

        // 5. 将用户信息写入下游请求头
        ServerHttpRequest modifiedRequest = request.mutate()
                .header("X-User-Id", String.valueOf(userId))
                .header("X-User-Name", username == null ? "" : username)
                .header("X-Authorities", authoritiesToHeader(claims))
                .build();

        return chain.filter(exchange.mutate().request(modifiedRequest).build());
    }

    @Override
    public int getOrder() {
        // 高优先级：尽可能早执行
        return -100;
    }

    private boolean isSkipPath(String path) {
        return SKIP_PATHS.stream().anyMatch(path::startsWith);
    }

    private String extractToken(ServerHttpRequest request) {
        String bearer = request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (StringUtils.hasText(bearer) && bearer.startsWith("Bearer ")) {
            return bearer.substring(7).trim();
        }
        return null;
    }

    /**
     * 使用与 exchange-auth 相同的密钥校验 JWT，成功返回 Claims，失败返回 null。
     */
    private Claims parseToken(String token) {
        try {
            SecretKey key = Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));
            return Jwts.parserBuilder()
                    .setSigningKey(key)
                    .build()
                    .parseClaimsJws(token)
                    .getBody();
        } catch (Exception e) {
            log.warn("JWT校验失败: {}", e.getMessage());
            return null;
        }
    }

    private String authoritiesToHeader(Claims claims) {
        Object roles = claims.get("roles");
        if (roles instanceof List<?> list) {
            return list.stream()
                    .map(String::valueOf)
                    .collect(Collectors.joining(","));
        }
        return "";
    }

    /**
     * 返回 401 统一 JSON 响应
     */
    private Mono<Void> unauthorized(ServerWebExchange exchange, String message) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(HttpStatus.UNAUTHORIZED);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);

        try {
            String body = objectMapper.writeValueAsString(Result.unauthorized(message));
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            DataBuffer buffer = response.bufferFactory().wrap(bytes);
            return response.writeWith(Mono.just(buffer));
        } catch (Exception e) {
            log.error("序列化 401 响应失败", e);
            response.setStatusCode(HttpStatus.UNAUTHORIZED);
            return response.setComplete();
        }
    }
}
