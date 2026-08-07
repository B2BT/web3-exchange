package com.web3.exchange.futures.config;

import com.web3.exchange.futures.service.LiquidationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 强平引擎定时扫描任务（M5）。每 10 秒用标记价盯市一次。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LiquidationTask {

    private final LiquidationService liquidationService;

    @Scheduled(fixedDelay = 10_000)
    public void run() {
        try {
            int n = liquidationService.scanAndLiquidate();
            if (n > 0) {
                log.info("强平扫描完成: 强平 {} 个持仓", n);
            }
        } catch (Exception e) {
            log.error("强平扫描任务异常: {}", e.getMessage());
        }
    }
}
