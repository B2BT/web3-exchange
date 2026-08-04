package com.web3.exchange.gateway.filter;

import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * 请求日志全局过滤器：记录每个请求的 method、path、响应状态码与耗时。
 * <p>
 * Order = -200，优先级高于 AuthFilter(-100)，确保包裹整条过滤链；
 * 使用 doFinally 在请求完成后统一输出结果（含被下游/鉴权 short-circuit 的请求）。
 */
@Slf4j
@Component
public class RequestLogFilter implements GlobalFilter, Ordered {

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String method = request.getMethod() == null ? "" : request.getMethod().name();
        String path = request.getPath().value();
        String rawQuery = request.getURI().getRawQuery();
        String target = rawQuery == null ? path : path + "?" + rawQuery;
        long start = System.currentTimeMillis();

        return chain.filter(exchange).doFinally(signal -> {
            long cost = System.currentTimeMillis() - start;
            int status = exchange.getResponse().getStatusCode() == null
                    ? 0
                    : exchange.getResponse().getStatusCode().value();
            log.info("[GATEWAY] {} {} -> status={} cost={}ms signal={}",
                    method, target, status, cost, signal);
        });
    }

    @Override
    public int getOrder() {
        return -200;
    }
}
