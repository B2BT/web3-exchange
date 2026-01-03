package com.web3.exchange.gateway.filter;

import com.example.gateway.service.AuthService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Mono;
import java.util.List;
import java.util.Objects;

@Slf4j
@Component
@RequiredArgsConstructor
public class AuthFilter extends AbstractGatewayFilterFactory<AuthFilter.Config> {

    private final AuthService authService;

    public AuthFilter() {
        super(Config.class);
    }

    @Override
    public GatewayFilter apply(Config config) {
        return (exchange, chain) -> {
            ServerHttpRequest request = exchange.getRequest();
            String path = request.getPath().value();

            // 跳过认证的路径
            if (isSkipAuth(path)) {
                return chain.filter(exchange);
            }

            // 获取Token
            String token = getTokenFromRequest(request);
            if (!StringUtils.hasText(token)) {
                return unauthorized(exchange, "缺少Token");
            }

            // 验证Token
            if (!authService.validateToken(token)) {
                return unauthorized(exchange, "Token无效或已过期");
            }

            // 获取用户信息
            UserInfo userInfo = authService.getUserInfoFromToken(token);
            if (userInfo == null) {
                return unauthorized(exchange, "用户信息获取失败");
            }

            // 权限验证
            if (config.getRequiredRoles() != null && !config.getRequiredRoles().isEmpty()) {
                if (!hasRequiredRoles(userInfo, config.getRequiredRoles())) {
                    return forbidden(exchange, "权限不足");
                }
            }

            // 添加用户信息到Header
            ServerHttpRequest modifiedRequest = request.mutate()
                    .header("X-User-Id", String.valueOf(userInfo.getId()))
                    .header("X-User-Name", userInfo.getUsername())
                    .header("X-Authorities", String.join(",", userInfo.getAuthorities()))
                    .build();

            return chain.filter(exchange.mutate().request(modifiedRequest).build());
        };
    }

    private String getTokenFromRequest(ServerHttpRequest request) {
        String bearerToken = request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (StringUtils.hasText(bearerToken) && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7);
        }
        return null;
    }

    private boolean isSkipAuth(String path) {
        return path.startsWith("/api/auth/login") ||
                path.startsWith("/api/auth/register") ||
                path.startsWith("/api/auth/captcha") ||
                path.startsWith("/actuator/health");
    }

    private boolean hasRequiredRoles(UserInfo userInfo, List<String> requiredRoles) {
        return userInfo.getAuthorities().stream()
                .anyMatch(requiredRoles::contains);
    }

    private Mono<Void> unauthorized(ServerWebExchange exchange, String message) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(HttpStatus.UNAUTHORIZED);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);

        String body = String.format("{\"code\": 401, \"message\": \"%s\"}", message);
        DataBuffer buffer = response.bufferFactory()
                .wrap(body.getBytes(StandardCharsets.UTF_8));

        return response.writeWith(Mono.just(buffer));
    }

    private Mono<Void> forbidden(ServerWebExchange exchange, String message) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(HttpStatus.FORBIDDEN);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);

        String body = String.format("{\"code\": 403, \"message\": \"%s\"}", message);
        DataBuffer buffer = response.bufferFactory()
                .wrap(body.getBytes(StandardCharsets.UTF_8));

        return response.writeWith(Mono.just(buffer));
    }

    @Data
    public static class Config {
        private List<String> requiredRoles;
    }
}