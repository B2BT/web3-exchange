package com.web3.exchange.filter;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.web3.exchange.auth.dto.response.UserInfoResponse;
import com.web3.exchange.auth.service.AuthService;
import com.web3.exchange.common.model.Result;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.HttpHeaders;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class AuthFilter extends AbstractGatewayFilterFactory<AuthFilter.Config> {


    private final ObjectMapper objectMapper;
    private final AuthService authService;

    @Override
    public GatewayFilter apply(Config config) {
        return ((exchange, chain) -> {
            ServerHttpRequest request = exchange.getRequest();
            String path = request.getPath().value();

            // 跳过白名单路径（如/login、/public/**等）
            if (isSkipAuth(path)) {
                return chain.filter(exchange);
            }

            // 获取Token
            String token = getTokenFromRequest(request);
            if (!StringUtils.hasText(token)) {
                return unauthorized(exchange, "缺少token");
            }
            // 验证token
            if (!authService.validateToken(token)) {
                return unauthorized(exchange, "Token无效或过期");
            }
            // 获取用户信息
            UserInfoResponse userInfo = authService.getUserFromToken(token);
            if (userInfo == null) {
                return unauthorized(exchange, "用户信息获取失败");
            }

            // 权限验证
            if (config.getRequiredRoles() != null && !config.getRequiredRoles().isEmpty()) {
                if (!hasRequiredRoles(userInfo,config.getRequiredRoles())){
                    return forbidden(exchange,"权限不足");
                }
            }
            // 添加用户信息到Header，请求改写。后端微服务不建议再次解析Token，
            // 网关直接把解析好的用户ID和用户名塞进HTTP Header里面，
            // 下游服务只需要执行request.getHeader("X-User-Id")就能拿到当前登录者的身份
            // mutate：转换
            ServerHttpRequest modifiedRequest = request.mutate()
                    .header("X-User-Id", String.valueOf(userInfo.getId()))
                    .header("X-User-Name", userInfo.getUsername())
                    .header("X-Authorities", String.join(",", userInfo.getAuthorities()))
                    .build();
            return chain.filter(exchange.mutate().request(modifiedRequest).build());
        });
    }

    private boolean hasRequiredRoles(UserInfoResponse userInfo, List<String> requiredRoles) {
        return userInfo.getAuthorities().stream()
                .anyMatch(requiredRoles::contains);
    }

    private Mono<Void> forbidden(ServerWebExchange exchange, String message) {
        ServerHttpResponse response = exchange.getResponse();
        // 401状态码，未授权
        response.setStatusCode(HttpStatus.FORBIDDEN);
        // 没有这一行，前端可能把返回结果当作纯文本处理
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        // 构建JSON字符串
        Result<Void> result = Result.forbidden(message);

        // 将字符串转换为字节流返回，通过 Mono.fromCallable 延迟执行序列化，提升响应式性能
        return response.writeWith(Mono.fromCallable(() -> {
            try {
                byte[] bytes = objectMapper.writeValueAsBytes(result);
                return response.bufferFactory().wrap(bytes);
            } catch (JsonProcessingException e) {
                return response.bufferFactory().wrap("{\"code\":500}".getBytes());
            }
        }));
    }

    /**
     * 返回未授权
     * 由于 Spring Cloud Gateway 是基于异步非阻塞的 WebFlux 框架，
     * 它不能像 Controller 那样简单地返回一个 Result 对象，
     * 而是必须操作 数据缓冲区（DataBuffer） 直接向响应流中写入字节。
     *
     * @param exchange
     * @param message
     * @return
     */
    private Mono<Void> unauthorized(ServerWebExchange exchange, String message) {
        ServerHttpResponse response = exchange.getResponse();
        // 401状态码，未授权
        response.setStatusCode(HttpStatus.UNAUTHORIZED);
        // 没有这一行，前端可能把返回结果当作纯文本处理
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);

        // 构建JSON字符串
        Result<Void> result = Result.unauthorized(message);

        // 将字符串转换为字节流返回，通过 Mono.fromCallable 延迟执行序列化，提升响应式性能
        return response.writeWith(Mono.fromCallable(() -> {
            try {
                byte[] bytes = objectMapper.writeValueAsBytes(result);
                return response.bufferFactory().wrap(bytes);
            } catch (JsonProcessingException e) {
                return response.bufferFactory().wrap("{\"code\":500}".getBytes());
            }
        }));
    }

    private String getTokenFromRequest(ServerHttpRequest request) {
        String bearerToken = request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if(StringUtils.hasText(bearerToken) && bearerToken.startsWith("Bearer ")){
            return bearerToken.substring(7);
        }
        return null;
    }

    /**
     * 跳过白名单路径（如/login、/public/**等）
     *
     * @param path
     * @return
     */
    private boolean isSkipAuth(String path) {
        return path.startsWith("/api/auth/login") ||
                path.startsWith("/api/auth/register") ||
                path.startsWith("/api/auth/captcha") ||
                path.startsWith("/api/auth/health");
    }

    @Data
    public static class Config {
        private List<String> requiredRoles;
    }
}
