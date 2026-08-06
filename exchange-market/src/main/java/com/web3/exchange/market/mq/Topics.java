package com.web3.exchange.market.mq;

/**
 * RocketMQ 主题常量——统一维护，避免散落的魔法字符串。
 * <p>命名规范见 {@code docs/mq-topics.md}：主题名采用「{领域}-{事件}」全大写短横线格式。</p>
 */
public final class Topics {
    private Topics() {
    }

    /** 撮合成交事件（order 发，asset/notify/market 各自消费组订阅） */
    public static final String ORDER_TRADE = "ORDER-TRADE";

    /** market 独立消费组（同一主题可被多个消费组分别消费，互不影响） */
    public static final String MARKET_ORDER_TRADE_GROUP = "market-order-trade-group";
}
