package com.web3.exchange.futures.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.web3.exchange.futures.entity.FundingSettle;
import com.web3.exchange.futures.entity.FuturesAccount;
import com.web3.exchange.futures.entity.FuturesPosition;
import com.web3.exchange.futures.entity.MarkPrice;
import com.web3.exchange.futures.entity.SwapContract;
import com.web3.exchange.futures.mapper.FundingSettleMapper;
import com.web3.exchange.futures.mapper.FuturesAccountMapper;
import com.web3.exchange.futures.mapper.FuturesPositionMapper;
import com.web3.exchange.futures.mapper.MarkPriceMapper;
import com.web3.exchange.futures.mapper.SwapContractMapper;
import com.web3.exchange.futures.service.FundingService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;

/**
 * 资金费率结算服务实现。
 * <p>资金费率 rate 为基点（1基点=0.01%，默认演示 10 基点=0.1%）。结算额 = 名义价值 × rate。
 * 多头支付给空头（rate>0 时多头付费），计入账户 margin 与 realized_pnl。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FundingServiceImpl implements FundingService {

    private final SwapContractMapper contractMapper;
    private final FuturesPositionMapper positionMapper;
    private final FuturesAccountMapper accountMapper;
    private final MarkPriceMapper markPriceMapper;
    private final FundingSettleMapper settleMapper;

    @Override
    @Transactional
    public int settleAll() {
        var contracts = contractMapper.selectList(
                new LambdaQueryWrapper<SwapContract>().eq(SwapContract::getStatus, 0));
        int handled = 0;
        for (SwapContract c : contracts) {
            try {
                handled += settleContract(c);
            } catch (Exception e) {
                log.error("资金费率结算异常 symbol={}: {}", c.getSymbol(), e.getMessage());
            }
        }
        return handled;
    }

    private int settleContract(SwapContract c) {
        MarkPrice mp = markPriceMapper.selectOne(
                new LambdaQueryWrapper<MarkPrice>().eq(MarkPrice::getSymbol, c.getSymbol()).last("LIMIT 1"));
        if (mp == null || mp.getMarkPrice() == null) return 0;
        long mark = mp.getMarkPrice();

        var positions = positionMapper.selectList(
                new LambdaQueryWrapper<FuturesPosition>()
                        .eq(FuturesPosition::getSymbol, c.getSymbol())
                        .eq(FuturesPosition::getStatus, 0)
                        .gt(FuturesPosition::getSize, 0));
        if (positions.isEmpty()) return 0;

        // 演示资金费率：固定 10 基点 = 0.001（真实应为标记价-现货价差计算）
        long rateBps = 10;
        BigDecimal rate = BigDecimal.valueOf(rateBps).divide(BigDecimal.valueOf(10000), 8, RoundingMode.HALF_UP);

        int count = 0;
        for (FuturesPosition pos : positions) {
            // 名义价值 = size × mark ÷ 1e8；结算额 = 名义价值 × rate
            long notionalVal = BigDecimal.valueOf(pos.getSize())
                    .multiply(BigDecimal.valueOf(mark))
                    .divide(BigDecimal.valueOf(1_0000_0000L), 0, RoundingMode.HALF_UP)
                    .longValue();
            BigDecimal fee = BigDecimal.valueOf(notionalVal).multiply(rate).setScale(0, RoundingMode.HALF_UP);
            long feeLong = fee.longValue();
            // 多头支付给空头：rate>0 时多头扣、空头加
            long amount = pos.getSide() == 1 ? -feeLong : feeLong;

            FuturesAccount acc = accountMapper.selectOne(
                    new LambdaQueryWrapper<FuturesAccount>()
                            .eq(FuturesAccount::getUserId, pos.getUserId())
                            .eq(FuturesAccount::getCoin, c.getQuote()).last("LIMIT 1"));
            if (acc == null) continue;

            acc.setMarginBalance(acc.getMarginBalance() + amount);
            acc.setAvailableBalance(Math.max(0, acc.getAvailableBalance() + amount));
            acc.setRealizedPnl((acc.getRealizedPnl() == null ? 0 : acc.getRealizedPnl()) + amount);
            accountMapper.updateById(acc);
            count++;
        }

        // 记录结算
        FundingSettle settle = new FundingSettle();
        settle.setSymbol(c.getSymbol());
        settle.setRate((int) rateBps);
        settle.setMarkPrice(mark);
        settle.setBasePrice(mark);
        settle.setFundingTime(LocalDateTime.now());
        settleMapper.insert(settle);
        return count;
    }
}
