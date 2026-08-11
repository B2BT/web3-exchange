package com.web3.exchange.market.kafka;

/**
 * Kafka 行情管道 topic 定义。
 * <p>Binance WS 真实行情 → Kafka topic → 多消费者（K线聚合/深度/合约标记价/前端推送）。</p>
 */
public final class KafkaTopics {

    /** 行情 ticker 事件流（最新价/24h快照） */
    public static final String MARKET_TICKER = "binance-ticker";

    /** K线事件流 */
    public static final String MARKET_KLINE = "binance-kline";

    /** 订单簿深度事件流 */
    public static final String MARKET_DEPTH = "binance-depth";

    private KafkaTopics() {}
}
