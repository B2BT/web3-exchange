package com.web3.exchange.futures.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.web3.exchange.common.exception.BusinessException;
import com.web3.exchange.futures.dto.FuturesFill;
import com.web3.exchange.futures.dto.PlaceFuturesOrderDTO;
import com.web3.exchange.futures.engine.FuturesMatchingEngine;
import com.web3.exchange.futures.entity.FuturesOrder;
import com.web3.exchange.futures.entity.FuturesPosition;
import com.web3.exchange.futures.entity.SwapContract;
import com.web3.exchange.futures.mapper.FuturesOrderMapper;
import com.web3.exchange.futures.mapper.FuturesPositionMapper;
import com.web3.exchange.futures.mapper.SwapContractMapper;
import com.web3.exchange.futures.service.FuturesAccountService;
import com.web3.exchange.futures.service.FuturesTradeService;
import com.web3.exchange.futures.service.MarkPriceService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/**
 * 合约交易服务实现：下单 → 撮合 → 持仓/保证金核算。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FuturesTradeServiceImpl implements FuturesTradeService {

    /** 常数：1=开多 2=开空 3=平多 4=平空 */
    private static final int OPEN_LONG = 1, OPEN_SHORT = 2, CLOSE_LONG = 3, CLOSE_SHORT = 4;

    private final FuturesOrderMapper orderMapper;
    private final FuturesPositionMapper positionMapper;
    private final SwapContractMapper contractMapper;
    private final FuturesMatchingEngine matchingEngine;
    private final FuturesAccountService accountService;
    private final MarkPriceService markPriceService;

    @Override
    @Transactional
    public FuturesOrder placeOrder(Long userId, PlaceFuturesOrderDTO dto) {
        SwapContract contract = contractMapper.selectOne(
                new LambdaQueryWrapper<SwapContract>().eq(SwapContract::getSymbol, dto.getSymbol())
                        .eq(SwapContract::getStatus, 0).last("LIMIT 1"));
        if (contract == null) {
            throw new BusinessException("合约交易对不存在或已下架");
        }
        int side = dto.getSide();
        int leverage = dto.getLeverage() == null ? 10 : dto.getLeverage();
        if (leverage < 1 || leverage > contract.getMaxLeverage()) {
            throw new BusinessException("杠杆超出范围");
        }

        // 换算数量为最小单位
        long qtyMin = toMinUnit(dto.getQuantity(), contract.getQtyDecimals());
        if (qtyMin <= 0) throw new BusinessException("数量不合法");

        // 构造订单
        FuturesOrder order = new FuturesOrder();
        order.setOrderNo("F" + IdWorker.getId());
        order.setUserId(userId);
        order.setSymbol(dto.getSymbol());
        order.setSide(side);
        order.setOrderType(dto.getOrderType());
        order.setQuantity(qtyMin);
        order.setRemaining(qtyMin);
        order.setFilled(0L);
        order.setAvgPrice(0L);
        order.setLeverage(leverage);
        order.setMarginMode(dto.getMarginMode() == null ? 1 : dto.getMarginMode());

        // 价格：限价取输入，市价用标记价（撮合时匹配订单簿）
        long limitPrice = 0;
        if (dto.getOrderType() == 1) {
            limitPrice = toMinUnit(dto.getPrice(), contract.getPriceDecimals());
            if (limitPrice <= 0) throw new BusinessException("限价不合法");
        }
        order.setPrice(limitPrice);

        // 开仓预冻结保证金 = 数量 × 价 / 杠杆（逐仓），市价单用标记价预估
        boolean isOpen = side == OPEN_LONG || side == OPEN_SHORT;
        if (isOpen) {
            long refPrice = limitPrice > 0 ? limitPrice : (markPriceService.getMarkPrice(dto.getSymbol()) == null
                    ? fallbackPrice(contract) : markPriceService.getMarkPrice(dto.getSymbol()));
            long margin = BigDecimal.valueOf(qtyMin)
                    .multiply(BigDecimal.valueOf(refPrice))
                    .divide(BigDecimal.valueOf(leverage), 0, RoundingMode.HALF_UP).longValue();
            accountService.addPositionMargin(userId, contract.getQuote(), margin);
        }

        // 撮合
        FuturesMatchingEngine.FillResult res = matchingEngine.place(order);

        // 逐笔成交核算持仓与盈亏
        applyFills(userId, contract, order, res.fills);

        // 更新订单状态与DB
        order.setAvgPrice(computeAvg(res.fills));
        order.setStatus(res.fullyFilled ? 2 : (order.getFilled() > 0 ? 1 : 0));
        orderMapper.insert(order);

        return order;
    }

    /** 逐笔成交更新持仓：按 userId 分组，开仓增加/均价加权，平仓减少并结算盈亏。 */
    private void applyFills(Long callerUserId, SwapContract c, FuturesOrder order, List<FuturesFill> fills) {
        // 按 userId 分组：taker 与 maker 各自核算
        java.util.Map<Long, List<FuturesFill>> byUser = new java.util.HashMap<>();
        for (FuturesFill f : fills) {
            byUser.computeIfAbsent(f.getUserId() == null ? callerUserId : f.getUserId(), k -> new java.util.ArrayList<>()).add(f);
        }
        for (var e : byUser.entrySet()) {
            Long uid = e.getKey();
            for (FuturesFill f : e.getValue()) {
                applySingleFill(uid, c, order, f);
            }
        }
    }

    private void applySingleFill(Long userId, SwapContract c, FuturesOrder order, FuturesFill f) {
            int side = f.getSide();
            boolean isOpen = side == OPEN_LONG || side == OPEN_SHORT;
            int posSide = (side == OPEN_LONG || side == CLOSE_LONG) ? 1 : 2;
            FuturesPosition pos = findOrCreatePos(userId, c.getSymbol(), posSide, order.getLeverage());

            if (isOpen) {
                // 增加持仓，均价加权
                long newSize = pos.getSize() + f.getQuantity();
                long oldSize = pos.getSize();
                if (oldSize == 0) {
                    pos.setEntryPrice(f.getPrice());
                } else {
                    long avg = BigDecimal.valueOf(pos.getEntryPrice())
                            .multiply(BigDecimal.valueOf(oldSize))
                            .add(BigDecimal.valueOf(f.getPrice()).multiply(BigDecimal.valueOf(f.getQuantity())))
                            .divide(BigDecimal.valueOf(newSize), 0, RoundingMode.HALF_UP).longValue();
                    pos.setEntryPrice(avg);
                }
                // 累加该笔成交的保证金（账户已在开单时冻结）
                long fillMargin = BigDecimal.valueOf(f.getQuantity())
                        .multiply(BigDecimal.valueOf(f.getPrice()))
                        .divide(BigDecimal.valueOf(order.getLeverage()), 0, RoundingMode.HALF_UP).longValue();
                pos.setIsolatedMargin((pos.getIsolatedMargin() == null ? 0 : pos.getIsolatedMargin()) + fillMargin);
                pos.setSize(newSize);
                pos.setUnrealizedPnl(computeUnrealized(pos, f.getPrice()));
            } else {
                // 平仓：减少持仓并结算盈亏
                long closeQty = Math.min(f.getQuantity(), pos.getSize());
                long pnl;
                if (posSide == 1) { // 多单平仓：(卖价 - 开仓价) × 数量
                    pnl = BigDecimal.valueOf(f.getPrice()).subtract(BigDecimal.valueOf(pos.getEntryPrice()))
                            .multiply(BigDecimal.valueOf(closeQty)).longValue();
                } else { // 空单平仓：(开仓价 - 买价) × 数量
                    pnl = BigDecimal.valueOf(pos.getEntryPrice()).subtract(BigDecimal.valueOf(f.getPrice()))
                            .multiply(BigDecimal.valueOf(closeQty)).longValue();
                }
                long newSize = pos.getSize() - closeQty;
                // 释放该部分保证金
                long posMargin = pos.getIsolatedMargin() == null ? 0 : pos.getIsolatedMargin();
                long released = pos.getSize() == 0 ? 0 : BigDecimal.valueOf(posMargin)
                        .multiply(BigDecimal.valueOf(closeQty))
                        .divide(BigDecimal.valueOf(pos.getSize()), 0, RoundingMode.HALF_UP)
                        .longValue();
                accountService.releasePositionMargin(userId, c.getQuote(), released);
                // 结算盈亏
                accountService.settleRealizedPnl(userId, c.getQuote(), pnl);
                pos.setRealizedPnl((pos.getRealizedPnl() == null ? 0 : pos.getRealizedPnl()) + pnl);
                pos.setIsolatedMargin(Math.max(0, posMargin - released));

                if (newSize <= 0) {
                    pos.setSize(0L);
                    pos.setStatus(1); // 已平仓
                    positionMapper.updateById(pos);
                    return;
                }
                pos.setSize(newSize);
                pos.setUnrealizedPnl(computeUnrealized(pos, f.getPrice()));
            }
            positionMapper.updateById(pos);
    }

    private long computeUnrealized(FuturesPosition pos, long mark) {
        long pnl;
        if (pos.getSide() == 1) {
            pnl = BigDecimal.valueOf(mark).subtract(BigDecimal.valueOf(pos.getEntryPrice()))
                    .multiply(BigDecimal.valueOf(pos.getSize())).longValue();
        } else {
            pnl = BigDecimal.valueOf(pos.getEntryPrice()).subtract(BigDecimal.valueOf(mark))
                    .multiply(BigDecimal.valueOf(pos.getSize())).longValue();
        }
        return pnl;
    }

    /** 查或建持仓（逐仓，side 维度）。 */
    private FuturesPosition findOrCreatePos(Long userId, String symbol, int posSide, int leverage) {
        FuturesPosition pos = positionMapper.selectOne(
                new LambdaQueryWrapper<FuturesPosition>()
                        .eq(FuturesPosition::getUserId, userId)
                        .eq(FuturesPosition::getSymbol, symbol)
                        .eq(FuturesPosition::getSide, posSide)
                        .eq(FuturesPosition::getStatus, 0).last("LIMIT 1"));
        if (pos != null) return pos;
        FuturesPosition np = new FuturesPosition();
        np.setUserId(userId);
        np.setSymbol(symbol);
        np.setSide(posSide);
        np.setSize(0L);
        np.setEntryPrice(0L);
        np.setLeverage(leverage);
        np.setIsolatedMargin(0L);
        np.setLiqPrice(0L);
        np.setUnrealizedPnl(0L);
        np.setRealizedPnl(0L);
        np.setStatus(0);
        positionMapper.insert(np);
        return positionMapper.selectOne(
                new LambdaQueryWrapper<FuturesPosition>()
                        .eq(FuturesPosition::getUserId, userId)
                        .eq(FuturesPosition::getSymbol, symbol)
                        .eq(FuturesPosition::getSide, posSide)
                        .eq(FuturesPosition::getStatus, 0).last("LIMIT 1"));
    }

    private long computeAvg(List<FuturesFill> fills) {
        if (fills.isEmpty()) return 0;
        BigDecimal totalQty = BigDecimal.ZERO, sum = BigDecimal.ZERO;
        for (FuturesFill f : fills) {
            totalQty = totalQty.add(BigDecimal.valueOf(f.getQuantity()));
            sum = sum.add(BigDecimal.valueOf(f.getPrice()).multiply(BigDecimal.valueOf(f.getQuantity())));
        }
        return totalQty.signum() == 0 ? 0 : sum.divide(totalQty, 0, RoundingMode.HALF_UP).longValue();
    }

    private long fallbackPrice(SwapContract c) {
        return "BTC".equals(c.getBase()) ? 6000000000000L : 250000000000L;
    }

    private long toMinUnit(String val, Integer decimals) {
        if (val == null || val.isBlank()) return 0;
        int dec = decimals == null ? 8 : decimals;
        try {
            return new BigDecimal(val).movePointRight(dec).setScale(0, RoundingMode.HALF_UP).longValue();
        } catch (Exception e) {
            return 0;
        }
    }

    @Override
    @Transactional
    public boolean cancel(Long userId, String symbol, String orderNo) {
        boolean removed = matchingEngine.cancel(symbol, orderNo);
        if (!removed) return false;
        FuturesOrder order = orderMapper.selectOne(
                new LambdaQueryWrapper<FuturesOrder>().eq(FuturesOrder::getOrderNo, orderNo)
                        .eq(FuturesOrder::getUserId, userId).last("LIMIT 1"));
        if (order != null && (order.getStatus() == 0 || order.getStatus() == 1)) {
            order.setStatus(3);
            orderMapper.updateById(order);
            // 释放剩余保证金
            boolean isOpen = order.getSide() == OPEN_LONG || order.getSide() == OPEN_SHORT;
            if (isOpen && order.getRemaining() > 0) {
                SwapContract c = contractMapper.selectOne(
                        new LambdaQueryWrapper<SwapContract>().eq(SwapContract::getSymbol, symbol).last("LIMIT 1"));
                long refPrice = order.getPrice() > 0 ? order.getPrice() : fallbackPrice(c);
                long margin = BigDecimal.valueOf(order.getRemaining())
                        .multiply(BigDecimal.valueOf(refPrice))
                        .divide(BigDecimal.valueOf(order.getLeverage()), 0, RoundingMode.HALF_UP).longValue();
                accountService.releasePositionMargin(userId, c == null ? "USDT" : c.getQuote(), margin);
            }
        }
        return true;
    }
}
