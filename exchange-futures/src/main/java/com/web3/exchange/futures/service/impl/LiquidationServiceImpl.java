package com.web3.exchange.futures.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.web3.exchange.futures.entity.FuturesAccount;
import com.web3.exchange.futures.entity.FuturesPosition;
import com.web3.exchange.futures.entity.MarkPrice;
import com.web3.exchange.futures.entity.SwapContract;
import com.web3.exchange.futures.mapper.FuturesAccountMapper;
import com.web3.exchange.futures.mapper.FuturesPositionMapper;
import com.web3.exchange.futures.mapper.MarkPriceMapper;
import com.web3.exchange.futures.mapper.SwapContractMapper;
import com.web3.exchange.futures.service.LiquidationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * 强平引擎服务实现（M5）。
 * <p>逐仓：账户权益 = isolated_margin + 该仓未实现盈亏（用标记价）。当 权益 &lt; 名义价值 × MMR 触发强平。
 * 强平 = 按标记价平仓（结算盈亏、释放保证金、清空仓位）。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LiquidationServiceImpl implements LiquidationService {

    private final FuturesPositionMapper positionMapper;
    private final FuturesAccountMapper accountMapper;
    private final MarkPriceMapper markPriceMapper;
    private final SwapContractMapper contractMapper;

    @Override
    @Transactional
    public int scanAndLiquidate() {
        var positions = positionMapper.selectList(
                new LambdaQueryWrapper<FuturesPosition>()
                        .eq(FuturesPosition::getStatus, 0)
                        .gt(FuturesPosition::getSize, 0));
        int liquidated = 0;
        for (FuturesPosition pos : positions) {
            try {
                if (isLiquidatable(pos)) {
                    liquidate(pos);
                    liquidated++;
                }
            } catch (Exception e) {
                log.error("强平检测异常 userId={} symbol={}: {}", pos.getUserId(), pos.getSymbol(), e.getMessage());
            }
        }
        return liquidated;
    }

    /** 判断是否触发强平：账户权益 < 名义价值 × MMR。 */
    private boolean isLiquidatable(FuturesPosition pos) {
        MarkPrice mp = markPriceMapper.selectOne(
                new LambdaQueryWrapper<MarkPrice>().eq(MarkPrice::getSymbol, pos.getSymbol()).last("LIMIT 1"));
        if (mp == null || mp.getMarkPrice() == null) return false;
        long mark = mp.getMarkPrice();

        SwapContract c = contractMapper.selectOne(
                new LambdaQueryWrapper<SwapContract>().eq(SwapContract::getSymbol, pos.getSymbol()).last("LIMIT 1"));
        if (c == null) return false;

        // 未实现盈亏（用标记价）；(价差 × 数量 ÷ 1e8)
        long upnl;
        if (pos.getSide() == 1) {
            upnl = notional(pos.getSize(), mark - pos.getEntryPrice());
        } else {
            upnl = notional(pos.getSize(), pos.getEntryPrice() - mark);
        }
        // 账户权益 = 逐仓保证金 + 未实现盈亏
        long equity = (pos.getIsolatedMargin() == null ? 0 : pos.getIsolatedMargin()) + upnl;
        // 名义价值 × MMR（MMR 基点：5000 = 50%）
        long notionalVal = notional(pos.getSize(), mark);
        BigDecimal maintenance = BigDecimal.valueOf(notionalVal)
                .multiply(BigDecimal.valueOf(c.getMmr() == null ? 5000 : c.getMmr()))
                .divide(BigDecimal.valueOf(10000), 0, RoundingMode.HALF_UP);
        boolean trigger = BigDecimal.valueOf(equity).compareTo(maintenance) < 0;
        if (trigger) {
            log.warn("强平触发 userId={} symbol={} side={} equity={} maintenance={}", pos.getUserId(), pos.getSymbol(), pos.getSide(), equity, maintenance.longValue());
        }
        return trigger;
    }

    /** 强平处置：按标记价平仓。 */
    private void liquidate(FuturesPosition pos) {
        MarkPrice mp = markPriceMapper.selectOne(
                new LambdaQueryWrapper<MarkPrice>().eq(MarkPrice::getSymbol, pos.getSymbol()).last("LIMIT 1"));
        long mark = mp != null && mp.getMarkPrice() != null ? mp.getMarkPrice() : pos.getEntryPrice();

        // 结算盈亏 (价差 × 数量 ÷ 1e8)
        long pnl;
        if (pos.getSide() == 1) {
            pnl = notional(pos.getSize(), mark - pos.getEntryPrice());
        } else {
            pnl = notional(pos.getSize(), pos.getEntryPrice() - mark);
        }
        // 平仓后剩余保证金 = isolated_margin + pnl（可为负，从账户扣减）
        long remainingMargin = (pos.getIsolatedMargin() == null ? 0 : pos.getIsolatedMargin()) + pnl;

        FuturesAccount acc = accountMapper.selectOne(
                new LambdaQueryWrapper<FuturesAccount>()
                        .eq(FuturesAccount::getUserId, pos.getUserId())
                        .eq(FuturesAccount::getCoin, quoteOf(pos)).last("LIMIT 1"));
        if (acc != null) {
            // 原占用保证金全部释放（用 pnl 修正：实际到账 = 保证金 + pnl）
            acc.setPositionMargin(Math.max(0, acc.getPositionMargin() - (pos.getIsolatedMargin() == null ? 0 : pos.getIsolatedMargin())));
            acc.setAvailableBalance(Math.max(0, acc.getAvailableBalance() + remainingMargin));
            acc.setMarginBalance(Math.max(0, acc.getMarginBalance() + pnl));
            acc.setRealizedPnl((acc.getRealizedPnl() == null ? 0 : acc.getRealizedPnl()) + pnl);
            accountMapper.updateById(acc);
        }

        pos.setSize(0L);
        pos.setStatus(1); // 已平仓（强平）
        pos.setRealizedPnl((pos.getRealizedPnl() == null ? 0 : pos.getRealizedPnl()) + pnl);
        positionMapper.updateById(pos);
        log.info("已强平 userId={} symbol={} side={} mark={} pnl={}", pos.getUserId(), pos.getSymbol(), pos.getSide(), mark, pnl);
    }

    private String quoteOf(FuturesPosition pos) {
        SwapContract c = contractMapper.selectOne(
                new LambdaQueryWrapper<SwapContract>().eq(SwapContract::getSymbol, pos.getSymbol()).last("LIMIT 1"));
        return c != null ? c.getQuote() : "USDT";
    }

    /**
     * 名义金额（USDT 最小单位）= 币数量最小单位 × 价格最小单位 / 1e8。
     * quantity 与 price 都是 1e-8 尺度最小单位，需 ÷1e8 还原为 USDT 最小单位。
     */
    private long notional(long qtyMin, long priceMin) {
        return BigDecimal.valueOf(qtyMin)
                .multiply(BigDecimal.valueOf(priceMin))
                .divide(BigDecimal.valueOf(1_0000_0000L), 0, RoundingMode.HALF_UP)
                .longValue();
    }
}
