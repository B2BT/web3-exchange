package com.web3.exchange.market.external;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.web3.exchange.market.market.MarketAggregator;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Binance WebSocket 真实行情源（毫秒级实时，免墙域名 data-stream.binance.vision）。
 * <p>常驻连接订阅 8 主流币对的 {@code @ticker}（24h快照）与 {@code @kline_1m/5m/15m/1h/4h/1d} 流，
 * 实时更新 MarketAggregator（覆盖 CoinGecko 轮询，延迟从秒级降到毫秒级）。</p>
 * <p><b>断线重连</b>：连接关闭后定时(5s)自动重连，可观测。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class BinanceWsPriceSource extends TextWebSocketHandler {

    private static final String WS_URL = "wss://data-stream.binance.vision/stream?streams=";
    /** 交易对（Binance 用 BTCUSDT 形式）→ 系统 symbol（BTC/USDT） */
    private static final String[] STREAMS = {
            "btcusdt", "ethusdt", "bnbusdt", "xrpusdt",
            "solusdt", "adausdt", "dogeusdt", "usdtusdt"
    };
    private static final String[] KLINE_INTERVALS = {"1m", "5m", "15m", "1h", "4h", "1d"};

    private final MarketAggregator aggregator;
    private final ObjectMapper objectMapper = new ObjectMapper();

    private final AtomicBoolean connected = new AtomicBoolean(false);
    private final StandardWebSocketClient client = new StandardWebSocketClient();
    private final ScheduledExecutorService reconnectExecutor = Executors.newSingleThreadScheduledExecutor();

    @Value("${server-settings.external-price.quote-decimals:8}")
    private int quoteDecimals;

    private WebSocketSession session;

    @PostConstruct
    public void start() {
        // 启动时连接；失败由重连机制兜底
        try {
            connect();
        } catch (Exception e) {
            log.warn("[binance-ws] 初次连接失败，进入重连: {}", e.getMessage());
            scheduleReconnect();
        }
    }

    private void connect() throws Exception {
        if (connected.get()) return;
        StringBuilder url = new StringBuilder(WS_URL);
        boolean first = true;
        for (String base : STREAMS) {
            if (!first) url.append("/"); // Binance 组合流用 / 分隔
            url.append(base).append("@ticker");
            for (String iv : KLINE_INTERVALS) {
                url.append("/").append(base).append("@kline_").append(iv);
            }
            first = false;
        }
        session = client.execute(this, url.toString()).get(20, TimeUnit.SECONDS);
        connected.set(true);
        log.info("[binance-ws] 已连接 Binance 实时行情流");
    }

    @Override
    protected void handleTextMessage(WebSocketSession s, TextMessage message) {
        try {
            JsonNode root = objectMapper.readTree(message.getPayload());
            JsonNode data = root.path("data");
            if (data.isMissingNode() || data.isNull()) return;
            String e = data.path("e").asText();
            if ("24hrTicker".equals(e)) {
                handleTicker(data);
            } else if ("kline".equals(e)) {
                handleKline(data.path("k"));
            }
        } catch (Exception ex) {
            log.debug("[binance-ws] 解析消息失败: {}", ex.getMessage());
        }
    }

    private void handleTicker(JsonNode d) {
        String symbol = toSystemSymbol(d.path("s").asText());
        if (symbol == null) return;
        TickerTmp t = new TickerTmp();
        t.lastPrice = toMin(d.path("c").asText());
        t.high = toMin(d.path("h").asText());
        t.low = toMin(d.path("l").asText());
        t.change = parseChange(d.path("P").asText());
        t.quoteVolume = toMin(d.path("q").asText());
        // volume：币数量，保留 8 位小数转 Long（decimals=8，避免溢出且显示合理）
        t.volume = toMin(d.path("v").asText());
        // 用真实 24h ticker 覆盖
        var ticker = new com.web3.exchange.market.market.model.Ticker();
        ticker.setSymbol(symbol);
        ticker.setLastPrice(t.lastPrice);
        ticker.setHigh24h(t.high);
        ticker.setLow24h(t.low);
        ticker.setChange24h(t.change);
        // quoteVolume = USDT 金额(×1e8 价格精度)；volume = 币数量(整数, decimals=0)
        ticker.setQuoteVolume24h(t.quoteVolume);
        ticker.setVolume24h(t.volume);
        aggregator.updateExternalTicker(ticker);
        // 同时注入一笔外部成交，驱动最新价/短线 K线
        aggregator.applyExternalTrade(symbol, t.lastPrice, 100000000L);
    }

    private void handleKline(JsonNode k) {
        String symbol = toSystemSymbol(k.path("s").asText());
        if (symbol == null) return;
        String interval = k.path("i").asText();
        boolean isClosed = k.path("x").asBoolean();
        long openTime = k.path("t").asLong();
        long open = toMin(k.path("o").asText());
        long high = toMin(k.path("h").asText());
        long low = toMin(k.path("l").asText());
        long close = toMin(k.path("c").asText());
        long volume = toMin(k.path("v").asText());
        long quoteVolume = toMin(k.path("q").asText());
        aggregator.applyExternalKline(symbol, interval, openTime, open, high, low, close, volume, quoteVolume);
    }

    @Override
    public void afterConnectionClosed(WebSocketSession s, CloseStatus status) {
        connected.set(false);
        log.warn("[binance-ws] 连接关闭: {}，安排重连", status);
        scheduleReconnect();
    }

    private void scheduleReconnect() {
        reconnectExecutor.schedule(() -> {
            try {
                connect();
            } catch (Exception e) {
                log.warn("[binance-ws] 重连失败，稍后重试: {}", e.getMessage());
                scheduleReconnect();
            }
        }, 5, TimeUnit.SECONDS);
    }

    @PreDestroy
    public void shutdown() {
        connected.set(false);
        reconnectExecutor.shutdownNow();
        if (session != null) {
            try { session.close(); } catch (Exception ignored) {}
        }
    }

    /** Binance 小写 symbol（btcusdt）→ 系统 symbol（BTC/USDT）；不支持返回 null。 */
    private String toSystemSymbol(String raw) {
        if (raw == null) return null;
        String s = raw.toLowerCase();
        for (String base : STREAMS) {
            if (s.equals(base)) {
                if ("usdtusdt".equals(base)) return "USDT/USDT";
                return base.substring(0, base.length() - 4).toUpperCase() + "/USDT";
            }
        }
        return null;
    }

    private long toMin(String s) {
        if (s == null || s.isBlank()) return 0;
        try {
            double v = Double.parseDouble(s);
            return Math.round(v * Math.pow(10, quoteDecimals));
        } catch (Exception e) {
            return 0;
        }
    }

    /** 涨跌幅（Binance 给百分比，如 1.5=1.5%）→ 基点(10000=100%) */
    private long parseChange(String s) {
        if (s == null || s.isBlank()) return 0;
        try {
            return Math.round(Double.parseDouble(s) * 100);
        } catch (Exception e) {
            return 0;
        }
    }

    /** 临时值容器（避免大量局部变量）。 */
    private static class TickerTmp {
        long lastPrice, high, low, change, quoteVolume, volume;
    }
}
