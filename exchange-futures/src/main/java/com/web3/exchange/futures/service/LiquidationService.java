package com.web3.exchange.futures.service;

/**
 * 强平引擎服务（M5）。
 * <p>用标记价盯市：逐仓账户权益 = 初始保证金 + 未实现盈亏。当 权益 &lt; 维持保证金（名义价值 × MMR）时触发强平。</p>
 */
public interface LiquidationService {

    /** 扫描所有持仓，触发强平检测并处置。返回强平数量。 */
    int scanAndLiquidate();
}
