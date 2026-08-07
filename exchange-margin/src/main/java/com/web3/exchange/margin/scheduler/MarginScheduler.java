package com.web3.exchange.margin.scheduler;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.web3.exchange.margin.config.MarginProperties;
import com.web3.exchange.margin.entity.MarginAccount;
import com.web3.exchange.margin.entity.MarginInterestRate;
import com.web3.exchange.margin.entity.MarginLoan;
import com.web3.exchange.margin.mapper.MarginAccountMapper;
import com.web3.exchange.margin.mapper.MarginInterestRateMapper;
import com.web3.exchange.margin.mapper.MarginLoanMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 杠杆定时任务：日利率计息 + 强制平仓。
 */
@Slf4j
@Component
public class MarginScheduler {

    private final MarginProperties props;
    private final MarginLoanMapper loanMapper;
    private final MarginAccountMapper accountMapper;
    private final MarginInterestRateMapper rateMapper;

    public MarginScheduler(MarginProperties props, MarginLoanMapper loanMapper,
                           MarginAccountMapper accountMapper, MarginInterestRateMapper rateMapper) {
        this.props = props;
        this.loanMapper = loanMapper;
        this.accountMapper = accountMapper;
        this.rateMapper = rateMapper;
    }

    /**
     * 计息：每整点执行。interest += principal_remain * rate_daily / 24
     * （日利率基点转小数：rate_bp / 10000 / 24）。
     */
    @Scheduled(cron = "0 0 * * * ?")
    public void accrueInterest() {
        if (!props.getInterest().isEnabled()) return;
        List<MarginLoan> loans = loanMapper.selectList(new LambdaQueryWrapper<MarginLoan>()
                .eq(MarginLoan::getStatus, 0)
                .gt(MarginLoan::getPrincipalRemain, 0));
        for (MarginLoan loan : loans) {
            long rateDailyBp = loan.getRateDaily();
            long hourInterest = loan.getPrincipalRemain() * rateDailyBp / 10000L / 24L;
            if (hourInterest <= 0) continue;
            loanMapper.update(null, new LambdaUpdateWrapper<MarginLoan>()
                    .eq(MarginLoan::getId, loan.getId())
                    .eq(MarginLoan::getStatus, 0)
                    .setSql("interest_accrued = interest_accrued + " + hourInterest));
            accountMapper.update(null, new LambdaUpdateWrapper<MarginAccount>()
                    .eq(MarginAccount::getUserId, loan.getUserId())
                    .eq(MarginAccount::getSymbol, loan.getSymbol())
                    .setSql("interest_accrued = interest_accrued + " + hourInterest));
        }
        log.info("[margin] 计息完成，扫描 {} 笔未还借单", loans.size());
    }

    /**
     * 强平：每 5 分钟执行。当 collateral / (borrowed + interest) < maintenance_ratio 时，
     * 用抵押折价（liquidationDiscount%）回购借入负债，剩余抵押退回。
     */
    @Scheduled(fixedDelay = 300000, initialDelay = 30000)
    public void liquidationScan() {
        if (!props.getLiquidation().isEnabled()) return;
        List<MarginAccount> accounts = accountMapper.selectList(new LambdaQueryWrapper<MarginAccount>()
                .gt(MarginAccount::getBorrowed, 0));
        for (MarginAccount acc : accounts) {
            MarginInterestRate rate = rateMapper.selectOne(new LambdaQueryWrapper<MarginInterestRate>()
                    .eq(MarginInterestRate::getSymbol, acc.getSymbol())
                    .last("limit 1"), false);
            if (rate == null) continue;
            long liability = acc.getBorrowed() + acc.getInterestAccrued();
            long ratioPct = liability == 0 ? 10000L : acc.getCollateral() * 100L / liability;
            if (ratioPct < rate.getMaintenanceRatio()) {
                liquidate(acc, liability);
            }
        }
    }

    private void liquidate(MarginAccount acc, long liability) {
        int discount = props.getLiquidation().getLiquidationDiscount();
        long discounted = liability * discount / 100L; // 折价后价值（用抵押覆盖）
        if (acc.getCollateral() < discounted) {
            // 抵押不足以覆盖折价负债：全额吃掉抵押，负债清零（剩余亏空记日志，MVP）
            accountMapper.update(null, new LambdaUpdateWrapper<MarginAccount>()
                    .eq(MarginAccount::getId, acc.getId())
                    .set(MarginAccount::getCollateral, 0L)
                    .set(MarginAccount::getBorrowed, 0L)
                    .set(MarginAccount::getInterestAccrued, 0L));
            loanMapper.update(null, new LambdaUpdateWrapper<MarginLoan>()
                    .eq(MarginLoan::getUserId, acc.getUserId())
                    .eq(MarginLoan::getSymbol, acc.getSymbol())
                    .eq(MarginLoan::getStatus, 0)
                    .set(MarginLoan::getStatus, 1)
                    .set(MarginLoan::getPrincipalRemain, 0L)
                    .set(MarginLoan::getInterestAccrued, 0L));
            log.warn("[margin] 强平(抵押不足) user={} symbol={} 抵押全损 liability={}",
                    acc.getUserId(), acc.getSymbol(), liability);
        } else {
            long remain = acc.getCollateral() - discounted;
            accountMapper.update(null, new LambdaUpdateWrapper<MarginAccount>()
                    .eq(MarginAccount::getId, acc.getId())
                    .set(MarginAccount::getCollateral, remain)
                    .set(MarginAccount::getBorrowed, 0L)
                    .set(MarginAccount::getInterestAccrued, 0L));
            loanMapper.update(null, new LambdaUpdateWrapper<MarginLoan>()
                    .eq(MarginLoan::getUserId, acc.getUserId())
                    .eq(MarginLoan::getSymbol, acc.getSymbol())
                    .eq(MarginLoan::getStatus, 0)
                    .set(MarginLoan::getStatus, 1)
                    .set(MarginLoan::getPrincipalRemain, 0L)
                    .set(MarginLoan::getInterestAccrued, 0L));
            log.info("[margin] 强平 user={} symbol={} liability={} 耗抵押={} 退回={}",
                    acc.getUserId(), acc.getSymbol(), liability, discounted, remain);
        }
    }
}
