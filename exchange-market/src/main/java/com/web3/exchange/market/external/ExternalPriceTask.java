package com.web3.exchange.market.external;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 外部真实行情定时拉取任务（CoinGecko）。
 * <p>演示环境每 30 秒拉一次真实价格注入聚合器，使 ticker/K线显示真实行情。
 * CoinGecko 免费版 rate limit 较严，30s 间隔安全；生产可换 Binance/OKX 并加密间隔。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ExternalPriceTask {

    private final CoinGeckoPriceSource priceSource;

    @Scheduled(fixedDelay = 30_000)
    public void run() {
        try {
            int n = priceSource.fetchAndInject();
            if (n > 0) {
                log.info("[extprice] 已注入 {} 个交易对真实行情", n);
            }
        } catch (Exception e) {
            log.error("[extprice] 行情拉取任务异常: {}", e.getMessage());
        }
    }
}
