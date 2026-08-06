package com.web3.exchange.market.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketSession;

import java.util.Map;
import java.util.Set;

/**
 * 行情定时推送任务：约 1s 遍历一次活跃订阅，从 {@link com.web3.exchange.market.market.MarketAggregator}
 * 取 ticker / 最新 kline 写回对应 session。
 * <p>
 * 校验 {@code session.isOpen()}，异常/失效 session 由 handler 清理。
 * </p>
 */
@Component
public class MarketWsPushTask {

    private static final Logger log = LoggerFactory.getLogger(MarketWsPushTask.class);

    private final MarketWebSocketHandler handler;

    public MarketWsPushTask(MarketWebSocketHandler handler) {
        this.handler = handler;
    }

    @Scheduled(fixedRate = 1000)
    public void push() {
        for (Map.Entry<WebSocketSession, Set<String>> entry : handler.subscriptions().entrySet()) {
            WebSocketSession session = entry.getKey();
            if (!session.isOpen()) {
                handler.removeSession(session);
                continue;
            }
            Set<String> keys = entry.getValue();
            if (keys == null || keys.isEmpty()) {
                continue;
            }
            for (String key : keys) {
                try {
                    String payload;
                    if (key.startsWith("ticker:")) {
                        payload = handler.buildTickerMessage(key);
                    } else if (key.startsWith("kline:")) {
                        payload = handler.buildKlineMessage(key);
                    } else {
                        continue;
                    }
                    handler.send(session, payload);
                } catch (Exception e) {
                    log.warn("[market-ws] 推送失败 sessionId={} key={} err={}",
                            session.getId(), key, e.getMessage());
                    handler.removeSession(session);
                    break;
                }
            }
        }
    }
}
