package com.web3.exchange.gateway.config;

import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.server.reactive.ServerHttpRequest;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * 限流 KeyResolver 配置：按客户端 IP 维度做请求限流。
 * <p>
 * 说明：
 * 1. Spring Cloud Gateway 的 RequestRateLimiter 会自动查找名为 {@code keyResolver} 的 KeyResolver Bean，
 *    因此这里 Bean 方法名固定为 keyResolver。
 * 2. IP 取值优先级：X-Forwarded-For 首段（网关/代理常见透传） > RemoteAddress。
 *    X-Forwarded-For 取逗号分隔后的第一个地址，规避多级代理伪造。
 * 3. 网关是 WebFlux 响应式栈，KeyResolver 是函数式接口，返回 Mono&lt;String&gt;。
 */
@Configuration
public class KeyResolverConfig {

    @Bean
    public KeyResolver keyResolver() {
        return exchange -> {
            ServerHttpRequest request = exchange.getRequest();
            List<String> forwarded = request.getHeaders().get("X-Forwarded-For");
            String ip;
            if (forwarded != null && !forwarded.isEmpty()
                    && forwarded.get(0) != null && !forwarded.get(0).isBlank()) {
                // 取首个 IP，多级代理时可能有 "ip1, ip2, ip3"
                ip = forwarded.get(0).split(",")[0].trim();
            } else if (request.getRemoteAddress() != null
                    && request.getRemoteAddress().getAddress() != null) {
                ip = request.getRemoteAddress().getAddress().getHostAddress();
            } else {
                ip = "unknown";
            }
            return Mono.just(ip);
        };
    }
}
