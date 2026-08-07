package com.web3.exchange.futures.config;

import com.web3.exchange.futures.service.FundingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 资金费率定时结算任务（M4）。演示环境每分钟结算，便于观察；生产应为 8 小时。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class FundingSettleTask {

    private final FundingService fundingService;

    @Scheduled(fixedDelay = 60_000)
    public void run() {
        try {
            int n = fundingService.settleAll();
            if (n > 0) {
                log.info("资金费率结算完成: {} 个持仓", n);
            }
        } catch (Exception e) {
            log.error("资金费率结算任务异常: {}", e.getMessage());
        }
    }
}
