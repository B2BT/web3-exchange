package com.web3.exchange.asset.mq;

/**
 * RocketMQ 主题常量——统一维护，避免散落的魔法字符串。
 * <p>
 * 命名规范见 {@code docs/mq-topics.md}：主题名采用「{领域}-{事件}」全大写短横线格式。
 * 本批次（B）先落地 {@link #ASSET_CHANGE} 的生产者与幂等消费骨架；
 * {@link #ORDER_TRADE}、{@link #DEPOSIT_CONFIRMED} 由 order/chain 后续批次接入。
 * </p>
 */
public final class Topics {
    private Topics() {
    }

    /** 资金变动事件（asset 发，order/notify 订阅）——本批次已接入生产者 */
    public static final String ASSET_CHANGE = "ASSET-CHANGE";

    /** 撮合成交事件（order 发，asset/notify 订阅）——后续批次 */
    public static final String ORDER_TRADE = "ORDER-TRADE";

    /** 充值确认事件（chain 发，asset 订阅）——后续批次 */
    public static final String DEPOSIT_CONFIRMED = "DEPOSIT-CONFIRMED";
}
