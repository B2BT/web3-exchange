package com.web3.exchange.futures.service;

/**
 * 标记价格服务。
 * <p>标记价 = 现货价 × (1 + 基差因子)，强平判定以标记价为准，防插针操纵。</p>
 */
public interface MarkPriceService {

    /** 获取某交易对最新标记价（最小单位 Long）；不存在返回 null。 */
    Long getMarkPrice(String symbol);
}
