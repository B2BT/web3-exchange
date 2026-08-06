package com.web3.exchange.chain.scanner;

import com.web3.exchange.chain.service.WithdrawService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 提现回执确认任务：定时扫描 status=2 且已上链的待确认提现，成功后扣减、失败回滚。
 */
@Slf4j
@Component
public class WithdrawConfirmTask {

    private final WithdrawService withdrawService;

    public WithdrawConfirmTask(WithdrawService withdrawService) {
        this.withdrawService = withdrawService;
    }

    @Scheduled(fixedDelay = 3000, initialDelay = 20000)
    public void confirmPending() {
        try {
            withdrawService.confirmPending();
        } catch (Exception e) {
            log.warn("[withdraw] 确认任务异常: {}", e.getMessage());
        }
    }
}
