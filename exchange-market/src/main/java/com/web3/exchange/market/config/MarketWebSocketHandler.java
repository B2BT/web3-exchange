package com.web3.exchange.market.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.web3.exchange.market.market.KlineInterval;
import com.web3.exchange.market.market.MarketAggregator;
import com.web3.exchange.market.market.model.Kline;
import com.web3.exchange.market.market.model.Ticker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 行情 WebSocket 处理器（实时推送 ticker / kline）。
 * <p>
 * 协议见 docs/ws-realtime.md：客户端发 {@code {"op":"subscribe","channel":"ticker|kline","symbol":"BTC/USDT"[,"period":"1m"]}}
 * / {@code unsubscribe} / {@code ping}；服务端回订阅确认 / pong / error，并约 1s 推送一次最新行情。
 * </p>
 * <p>维护 {@code session -> 订阅key集合}（如 {@code ticker:BTC/USDT}、{@code kline:BTC/USDT:1m}），重复订阅幂等。
 * 序列化使用 Spring 注入的 {@link ObjectMapper}，Long 金额/价格保留为 JSON number。</p>
 */
@Component
public class MarketWebSocketHandler extends TextWebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(MarketWebSocketHandler.class);

    /** session -> 订阅 key 集合（ticker:{symbol} / kline:{symbol}:{period}） */
    private final Map<WebSocketSession, Set<String>> subscriptions = new ConcurrentHashMap<>();

    private final ObjectMapper objectMapper;
    private final MarketAggregator aggregator;

    public MarketWebSocketHandler(ObjectMapper objectMapper, MarketAggregator aggregator) {
        this.objectMapper = objectMapper;
        this.aggregator = aggregator;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        // 空闲超时(60s)由 ServletServerContainerFactoryBean 统一配置；此处仅登记 session
        subscriptions.put(session, ConcurrentHashMap.newKeySet());
        log.debug("[market-ws] 连接建立 sessionId={}", session.getId());
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        try {
            JsonNode node = objectMapper.readTree(message.getPayload());
            String op = node.path("op").asText("");
            switch (op) {
                case "subscribe" -> doSubscribe(session, node);
                case "unsubscribe" -> doUnsubscribe(session, node);
                case "ping" -> send(session, buildMessage(Map.of("channel", "pong")));
                default -> sendError(session, "未知 op: " + op);
            }
        } catch (Exception e) {
            log.warn("[market-ws] 消息解析失败: {}", e.getMessage());
            sendError(session, "JSON 解析失败");
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        subscriptions.remove(session);
        log.debug("[market-ws] 连接关闭 sessionId={} status={}", session.getId(), status);
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        log.warn("[market-ws] 传输异常 sessionId={} err={}", session.getId(), exception.getMessage());
        subscriptions.remove(session);
    }

    private void doSubscribe(WebSocketSession session, JsonNode node) {
        String key = resolveKey(node, session);
        if (key == null) {
            return; // 错误已发送
        }
        subscriptions.computeIfAbsent(session, s -> ConcurrentHashMap.newKeySet()).add(key); // 幂等
        log.debug("[market-ws] 订阅 {} sessionId={}", key, session.getId());
        send(session, buildMessage(Map.of("channel", "subscribed", "channelName", key)));
    }

    private void doUnsubscribe(WebSocketSession session, JsonNode node) {
        String key = resolveKey(node, session);
        if (key == null) {
            return;
        }
        Set<String> set = subscriptions.get(session);
        if (set != null) {
            set.remove(key);
        }
        log.debug("[market-ws] 取消订阅 {} sessionId={}", key, session.getId());
    }

    /** 解析并校验订阅 key；参数非法时发送 error 并返回 null。 */
    private String resolveKey(JsonNode node, WebSocketSession session) {
        String channel = node.path("channel").asText("");
        String symbol = node.path("symbol").asText("").trim();
        if (symbol.isEmpty()) {
            sendError(session, "缺少 symbol");
            return null;
        }
        if ("ticker".equals(channel)) {
            return "ticker:" + symbol;
        }
        if ("kline".equals(channel)) {
            String period = node.path("period").asText("").trim();
            KlineInterval iv = KlineInterval.fromName(period);
            if (iv == null) {
                sendError(session, "不支持的 kline period: " + period);
                return null;
            }
            return "kline:" + symbol + ":" + iv.intervalName();
        }
        sendError(session, "不支持的 channel: " + channel);
        return null;
    }

    /**
     * 构建 ticker 推送 JSON；该 symbol 暂无行情时返回 null（调用方跳过本次推送）。
     */
    public String buildTickerMessage(String key) {
        String symbol = key.substring("ticker:".length());
        Ticker t = aggregator.getTicker(symbol);
        if (t == null) {
            return null;
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("lastPrice", t.getLastPrice());
        data.put("change24h", t.getChange24h());
        data.put("high24h", t.getHigh24h());
        data.put("low24h", t.getLow24h());
        data.put("volume24h", t.getVolume24h());
        data.put("quoteVolume24h", t.getQuoteVolume24h());
        Map<String, Object> msg = new LinkedHashMap<>();
        msg.put("channel", "ticker");
        msg.put("symbol", symbol);
        msg.put("data", data);
        return buildMessage(msg);
    }

    /**
     * 构建 kline 推送 JSON（最新一根）；该 symbol×period 暂无 K线时返回 null。
     */
    public String buildKlineMessage(String key) {
        String[] parts = key.split(":", 3); // kline, symbol, period
        String symbol = parts[1];
        String period = parts[2];
        List<Kline> list = aggregator.getKlines(symbol, period, 1);
        if (list.isEmpty()) {
            return null;
        }
        Kline k = list.get(list.size() - 1);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("openTime", k.getOpenTime());
        data.put("open", k.getOpen());
        data.put("high", k.getHigh());
        data.put("low", k.getLow());
        data.put("close", k.getClose());
        data.put("volume", k.getVolume());
        data.put("quoteVolume", k.getQuoteVolume());
        Map<String, Object> msg = new LinkedHashMap<>();
        msg.put("channel", "kline");
        msg.put("symbol", symbol);
        msg.put("period", period);
        msg.put("data", data);
        return buildMessage(msg);
    }

    /** 序列化对象为 JSON 字符串（Long 金额/价格保留 number）。 */
    public String buildMessage(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (Exception e) {
            log.warn("[market-ws] 序列化失败: {}", e.getMessage());
            return null;
        }
    }

    /** 向 session 发送文本帧；失败则清理订阅。 */
    public void send(WebSocketSession session, String payload) {
        if (payload == null) {
            return;
        }
        try {
            if (session.isOpen()) {
                session.sendMessage(new TextMessage(payload));
            } else {
                subscriptions.remove(session);
            }
        } catch (Exception e) {
            log.warn("[market-ws] 发送失败 sessionId={} err={}", session.getId(), e.getMessage());
            subscriptions.remove(session);
        }
    }

    private void sendError(WebSocketSession session, String message) {
        send(session, buildMessage(Map.of("channel", "error", "message", message)));
    }

    /** 当前订阅表（仅供定时推送遍历；weakly-consistent 并发安全）。 */
    public Map<WebSocketSession, Set<String>> subscriptions() {
        return subscriptions;
    }

    /** 移除某 session 的订阅（失效连接清理用）。 */
    public void removeSession(WebSocketSession session) {
        subscriptions.remove(session);
    }
}
