package com.web3.exchange.market.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;
import org.springframework.web.socket.server.standard.ServletServerContainerFactoryBean;

/**
 * 行情 WebSocket 配置（docs/ws-realtime.md）。
 * <p>
 * 注册端点 {@code /ws}（直连 8106）与 {@code /api/market/ws}（经网关 8080 代理）。
 * 端口/鉴权：公开行情，首帧不鉴权。
 * </p>
 */
@Configuration
@EnableWebSocket
@EnableScheduling
public class MarketWebSocketConfig implements WebSocketConfigurer {

    private final MarketWebSocketHandler handler;

    public MarketWebSocketConfig(MarketWebSocketHandler handler) {
        this.handler = handler;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(handler, "/ws", "/api/market/ws")
                .setAllowedOrigins("*");
    }

    /**
     * 容器级 session 空闲超时 60s：session 在该时长内无任何消息（含 ping/数据帧）到达则主动关闭。
     */
    @Bean
    public ServletServerContainerFactoryBean webSocketContainer() {
        ServletServerContainerFactoryBean container = new ServletServerContainerFactoryBean();
        container.setMaxSessionIdleTimeout(60_000L);
        return container;
    }
}
