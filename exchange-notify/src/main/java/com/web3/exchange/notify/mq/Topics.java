package com.web3.exchange.notify.mq;

/**
 * RocketMQ 主题常量——统一维护，避免散落的魔法字符串。
 * <p>
 * 命名规范见 {@code docs/mq-topics.md}：主题名采用「{领域}-{事件}」全大写短横线格式。
 * 通知域消费 {@link #ASSET_CHANGE}（资金变动）与 {@link #ORDER_TRADE}（成交通知）两条主题。
 * </p>
 */
public final class Topics {
    private Topics() {
    }

    /** 资金变动事件（asset 发，notify 订阅，消息体 LedgerVO） */
    public static final String ASSET_CHANGE = "ASSET-CHANGE";

    /** 撮合成交事件（order 发，notify 订阅，消息体 TradeSettleDTO） */
    public static final String ORDER_TRADE = "ORDER-TRADE";
}
