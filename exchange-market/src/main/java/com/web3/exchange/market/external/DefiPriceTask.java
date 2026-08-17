package com.web3.exchange.market.external;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * DeFi 链上价格定时拉取任务——Uniswap V2 储备算价，中心化行情源的链上兜底。
 * <p>由 server-settings.external-price.defi-enabled 控制（默认 false），
 * 周期 60s 拉取（链上读价比中心化 API 慢）。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DefiPriceTask {

    private final DefiPriceSource defiPriceSource;

    @Scheduled(fixedDelay = 60_000)
    public void run() {
        try {
            int n = defiPriceSource.fetchAndInject();
            if (n > 0) {
                log.info("[defiprice] 已注入 {} 个交易对 DeFi 链上价格", n);
            }
        } catch (Exception e) {
            log.error("[defiprice] DeFi 价格任务异常: {}", e.getMessage());
        }
    }
}
