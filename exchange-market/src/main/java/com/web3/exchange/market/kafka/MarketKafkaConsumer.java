package com.web3.exchange.market.kafka;

import com.web3.exchange.market.kafka.dto.MarketEvent;
import com.web3.exchange.market.market.MarketAggregator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * 行情 Kafka 消费者。
 * <p>从 Kafka topic 消费 Binance WS 写入的真实行情，更新 MarketAggregator。
 * 这是"行情管道"的消费端——多消费者组可并行消费同一 topic，实现解耦与横向扩展。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MarketKafkaConsumer {

    private final MarketAggregator aggregator;

    @KafkaListener(topics = KafkaTopics.MARKET_TICKER,
            groupId = "market-kline-group",
            containerFactory = "marketKafkaListenerContainerFactory")
    public void onTicker(MarketEvent e) {
        if (e == null || e.getSymbol() == null) return;
        var ticker = new com.web3.exchange.market.market.model.Ticker();
        ticker.setSymbol(e.getSymbol());
        ticker.setLastPrice(e.getLastPrice());
        ticker.setHigh24h(e.getHigh24h());
        ticker.setLow24h(e.getLow24h());
        ticker.setChange24h(e.getChange24h());
        ticker.setVolume24h(e.getVolume24h());
        ticker.setQuoteVolume24h(e.getQuoteVolume24h());
        aggregator.updateExternalTicker(ticker);
        // 注入外部成交，驱动最新价/短线 K线
        if (e.getLastPrice() != null) {
            aggregator.applyExternalTrade(e.getSymbol(), e.getLastPrice(), 100000000L);
        }
    }

    @KafkaListener(topics = KafkaTopics.MARKET_KLINE,
            groupId = "market-kline-group",
            containerFactory = "marketKafkaListenerContainerFactory")
    public void onKline(MarketEvent e) {
        if (e == null || e.getSymbol() == null || e.getInterval() == null) return;
        aggregator.applyExternalKline(e.getSymbol(), e.getInterval(), e.getOpenTime() == null ? 0 : e.getOpenTime(),
                e.getOpen(), e.getHigh(), e.getLow(), e.getClose(),
                e.getVolume(), e.getQuoteVolume());
    }

    @KafkaListener(topics = KafkaTopics.MARKET_DEPTH,
            groupId = "market-depth-group",
            containerFactory = "marketKafkaListenerContainerFactory")
    public void onDepth(MarketEvent e) {
        if (e == null || e.getSymbol() == null) return;
        aggregator.updateExternalDepth(e.getSymbol(), e.getBids(), e.getAsks());
    }
}
