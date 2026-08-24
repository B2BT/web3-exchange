package com.web3.exchange.asset.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.web3.exchange.asset.entity.Account;
import com.web3.exchange.asset.entity.Ledger;
import com.web3.exchange.asset.mapper.AccountMapper;
import com.web3.exchange.asset.mapper.LedgerMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 资金对账引擎（第三梯队·业务健壮性）。
 * <p>账实核对：① 账户内部不变式 total == available + frozen；
 * ② 账实一致：账户当前余额 = 该账户最新一笔记账流水后的余额（before+after 追踪链）。
 * 对账结果输出到日志 + 控制台接口，供审计发现资金偏差。</p>
 * 用于定期（默认每 5 分钟）自动对账，也可手动触发。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ReconciliationService {

    private final AccountMapper accountMapper;
    private final LedgerMapper ledgerMapper;

    /**
     * 定时对账（默认每 5 分钟）。发现不平衡输出 ERROR 日志并累计计数。
     */
    @Scheduled(fixedRate = 300_000, initialDelay = 60_000)
    public void scheduledReconcile() {
        ReconcileReport report = runReconcile();
        if (report.getUnbalancedTotal() > 0) {
            log.error("[对账] 发现资金不平衡 {} 项！symbol={} 明细见接口 /api/asset/reconcile",
                    report.getUnbalancedTotal(), report.getSymbol());
        } else {
            log.info("[对账] 核对 {} 账户，账实一致，无不平衡", report.getCheckedAccounts());
        }
    }

    /**
     * 执行对账并返回报告。
     */
    public ReconcileReport runReconcile() {
        ReconcileReport report = new ReconcileReport();
        List<Account> accounts = accountMapper.selectList(new LambdaQueryWrapper<Account>()
                .in(Account::getStatus, 0, 1));
        report.setCheckedAccounts(accounts.size());
        report.setCheckedAt(LocalDateTime.now());

        StringBuilder sb = new StringBuilder();
        for (Account a : accounts) {
            // ① 内部不变式
            long total = (a.getTotal() == null ? 0 : a.getTotal());
            long available = a.getAvailable() == null ? 0 : a.getAvailable();
            long frozen = a.getFrozen() == null ? 0 : a.getFrozen();
            if (total != (available + frozen)) {
                report.incUnbalanced("内部不变式 total!=available+frozen",
                        a.getUserId(), a.getSymbol(), total, available, frozen);
                sb.append(String.format("  [不变式] user=%s symbol=%s total=%d avail=%d frozen=%d%n",
                        a.getUserId(), a.getSymbol(), total, available, frozen));
                continue;
            }
            // ② 账实一致：取该账户最新一笔流水
            Ledger last = ledgerMapper.selectOne(new LambdaQueryWrapper<Ledger>()
                    .eq(Ledger::getAccountId, a.getId())
                    .orderByDesc(Ledger::getId)
                    .last("limit 1"));
            if (last == null) {
                continue; // 无流水的新账户，跳过
            }
            long lastAvail = last.getAfterAvailable() == null ? 0 : last.getAfterAvailable();
            long lastFrozen = last.getAfterFrozen() == null ? 0 : last.getAfterFrozen();
            if (lastAvail != available || lastFrozen != frozen) {
                report.incUnbalanced("账实不一致(流水分录≠账户当前值)",
                        a.getUserId(), a.getSymbol(), total, available, frozen);
                sb.append(String.format("  [账实] user=%s symbol=%s 账户(avail=%d,frozen=%d) != 最新流水(after_avail=%d,after_frozen=%d)%n",
                        a.getUserId(), a.getSymbol(), available, frozen, lastAvail, lastFrozen));
            }
        }
        if (report.getUnbalancedTotal() > 0) {
            log.warn("[对账] 资金不平衡明细:\n{}", sb);
        }
        return report;
    }

    /**
     * 对账报告（控制台/接口返回）。
     */
    public static class ReconcileReport {
        private int checkedAccounts = 0;
        private int unbalancedTotal = 0;
        private String symbol = "";
        private int unbalancedCount = 0;
        private LocalDateTime checkedAt;
        private final java.util.List<String> details = new java.util.ArrayList<>();

        public void incUnbalanced(String type, Long userId, String sym, long total, long avail, long frozen) {
            unbalancedTotal++;
            unbalancedCount++;
            symbol = sym;
            details.add(String.format("[%s] user=%s symbol=%s total=%d avail=%d frozen=%d",
                    type, userId, sym, total, avail, frozen));
        }

        @Override
        public String toString() {
            return "核对账户" + checkedAccounts + "个, 不平衡" + unbalancedTotal + "项, 时间" + checkedAt;
        }

        public int getCheckedAccounts() { return checkedAccounts; }
        public void setCheckedAccounts(int n) { this.checkedAccounts = n; }
        public int getUnbalancedTotal() { return unbalancedTotal; }
        public String getSymbol() { return symbol; }
        public int getUnbalancedCount() { return unbalancedCount; }
        public LocalDateTime getCheckedAt() { return checkedAt; }
        public void setCheckedAt(LocalDateTime t) { this.checkedAt = t; }
        public java.util.List<String> getDetails() { return details; }
    }
}
