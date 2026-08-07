package com.web3.exchange.staking.scheduler;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.web3.exchange.common.asset.dto.CreditRequest;
import com.web3.exchange.common.model.Result;
import com.web3.exchange.staking.entity.StakingInterest;
import com.web3.exchange.staking.entity.StakingPosition;
import com.web3.exchange.staking.entity.StakingProduct;
import com.web3.exchange.staking.feign.AssetClient;
import com.web3.exchange.staking.mapper.StakingInterestMapper;
import com.web3.exchange.staking.mapper.StakingPositionMapper;
import com.web3.exchange.staking.mapper.StakingProductMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * 质押收益结算定时任务（每日结算）。
 * <p>每持仓按 日利率 = annual_rate_bp/10000/365 累计当日收益 → 写收益流水 →
 * 调 asset credit 入账（requestId=STK_INT:{posId}:{date} 幂等）→ 已结收益归 total。</p>
 */
@Slf4j
@Component
public class StakingScheduler {

    private final StakingPositionMapper positionMapper;
    private final StakingProductMapper productMapper;
    private final StakingInterestMapper interestMapper;
    private final AssetClient assetClient;

    public StakingScheduler(StakingPositionMapper positionMapper,
                            StakingProductMapper productMapper,
                            StakingInterestMapper interestMapper,
                            AssetClient assetClient) {
        this.positionMapper = positionMapper;
        this.productMapper = productMapper;
        this.interestMapper = interestMapper;
        this.assetClient = assetClient;
    }

    /** 每日 00:05 结算一次（也支持手动触发）。 */
    @Scheduled(cron = "0 5 0 * * ?")
    public void settleDaily() {
        String date = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyyMMdd"));
        List<StakingPosition> positions = positionMapper.selectList(new LambdaQueryWrapper<StakingPosition>()
                .eq(StakingPosition::getStatus, 0));
        int settled = 0;
        for (StakingPosition pos : positions) {
            StakingProduct product = productMapper.selectOne(new LambdaQueryWrapper<StakingProduct>()
                    .eq(StakingProduct::getProductCode, pos.getProductCode())
                    .last("limit 1"), false);
            if (product == null || product.getAnnualRateBp() == null) continue;
            // 当日收益 = 本金 * 年化 / 10000 / 365
            long dayInterest = pos.getAmount() * product.getAnnualRateBp() / 10000L / 365L;
            if (dayInterest <= 0) continue;
            String requestId = "STK_INT:" + pos.getId() + ":" + date;
            // 幂等：已有该持仓当日流水则跳过
            long exist = interestMapper.selectCount(new LambdaQueryWrapper<StakingInterest>()
                    .eq(StakingInterest::getRequestId, requestId));
            if (exist > 0) continue;
            // credit 入账
            CreditRequest cr = new CreditRequest();
            cr.setRequestId(requestId);
            cr.setUserId(pos.getUserId());
            cr.setSymbol(pos.getSymbol());
            cr.setAmount(dayInterest);
            cr.setBizType("DEPOSIT");
            cr.setRefNo(String.valueOf(pos.getId()));
            cr.setRemark("质押收益结算 " + date);
            Result<?> r = assetClient.credit(cr);
            if (r == null || !r.isSuccess()) {
                log.error("[staking] 收益入账失败 posId={} err={}", pos.getId(), r == null ? "null" : r.getMessage());
                continue;
            }
            // 写收益流水
            StakingInterest si = new StakingInterest();
            si.setId(IdWorker.getId());
            si.setUserId(pos.getUserId());
            si.setPositionId(pos.getId());
            si.setSymbol(pos.getSymbol());
            si.setAmount(dayInterest);
            si.setSettleDate(date);
            si.setRequestId(requestId);
            si.setRemark("质押收益 " + date);
            interestMapper.insert(si);
            // 结转已结收益
            positionMapper.update(null, new LambdaUpdateWrapper<StakingPosition>()
                    .eq(StakingPosition::getId, pos.getId())
                    .setSql("total_interest = total_interest + " + dayInterest));
            settled++;
        }
        log.info("[staking] 收益结算完成 date={} 处理={} 笔", date, settled);
    }
}
