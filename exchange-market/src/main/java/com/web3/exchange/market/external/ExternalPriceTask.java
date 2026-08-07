package com.web3.exchange.market.external;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 外部真实行情定时拉取任务（CoinGecko）——Binance WS 的兜底。
 * <p>当 Binance WebSocket 不可用时（网络/DNS 受限），用 CoinGecko 轮询保证有真实行情。
 * 由配置 server-settings.external-price.coingecko-enabled 控制（默认 false，WS 优先）。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ExternalPriceTask {

    private final CoinGeckoPriceSource priceSource;

    @Value("${server-settings.external-price.coingecko-enabled:false}")
    private boolean coingeckoEnabled;

    @Scheduled(fixedDelay = 30_000)
    public void run() {
        if (!coingeckoEnabled) {
            return; // Binance WS 优先，CoinGecko 仅作兜底
        }
        try {
            int n = priceSource.fetchAndInject();
            if (n > 0) {
                log.info("[extprice] 已注入 {} 个交易对 CoinGecko 行情", n);
            }
        } catch (Exception e) {
            log.error("[extprice] 行情拉取任务异常: {}", e.getMessage());
        }
    }
}
