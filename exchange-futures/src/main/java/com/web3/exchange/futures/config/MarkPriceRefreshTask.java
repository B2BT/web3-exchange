package com.web3.exchange.futures.config;

import com.web3.exchange.futures.service.impl.MarkPriceServiceImpl;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 标记价定时刷新任务（M1）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MarkPriceRefreshTask {

    private final MarkPriceServiceImpl markPriceService;

    @Value("${server-settings.mark.refresh-interval-ms:5000}")
    private long intervalMs;

    @Scheduled(fixedDelayString = "${server-settings.mark.refresh-interval-ms:5000}")
    public void refresh() {
        try {
            int n = markPriceService.refreshAll();
            if (n > 0 && System.currentTimeMillis() % 300_000 < intervalMs) {
                log.debug("标记价刷新完成: {} 个交易对", n);
            }
        } catch (Exception e) {
            log.error("标记价刷新异常: {}", e.getMessage());
        }
    }
}
