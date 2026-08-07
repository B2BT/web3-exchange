package com.web3.exchange.futures.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.web3.exchange.common.exception.BusinessException;
import com.web3.exchange.futures.entity.FuturesAccount;
import com.web3.exchange.futures.mapper.FuturesAccountMapper;
import com.web3.exchange.futures.service.FuturesAccountService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 合约账户服务实现。
 */
@Service
@RequiredArgsConstructor
public class FuturesAccountServiceImpl implements FuturesAccountService {

    private final FuturesAccountMapper accountMapper;

    @Override
    public FuturesAccount getOrCreate(Long userId, String coin) {
        FuturesAccount acc = accountMapper.selectOne(
                new LambdaQueryWrapper<FuturesAccount>().eq(FuturesAccount::getUserId, userId)
                        .eq(FuturesAccount::getCoin, coin).last("LIMIT 1"));
        if (acc != null) return acc;
        FuturesAccount na = new FuturesAccount();
        na.setUserId(userId);
        na.setCoin(coin);
        na.setMarginBalance(0L);
        na.setAvailableBalance(0L);
        na.setPositionMargin(0L);
        na.setUnrealizedPnl(0L);
        na.setRealizedPnl(0L);
        accountMapper.insert(na);
        return accountMapper.selectOne(
                new LambdaQueryWrapper<FuturesAccount>().eq(FuturesAccount::getUserId, userId)
                        .eq(FuturesAccount::getCoin, coin).last("LIMIT 1"));
    }

    @Override
    @Transactional
    public FuturesAccount deposit(Long userId, String coin, Long amount) {
        FuturesAccount acc = getOrCreate(userId, coin);
        acc.setMarginBalance(acc.getMarginBalance() + amount);
        acc.setAvailableBalance(acc.getAvailableBalance() + amount);
        accountMapper.updateById(acc);
        return acc;
    }

    @Override
    @Transactional
    public FuturesAccount withdraw(Long userId, String coin, Long amount) {
        FuturesAccount acc = getOrCreate(userId, coin);
        if (acc.getAvailableBalance() < amount) {
            throw new BusinessException("合约可用余额不足");
        }
        acc.setMarginBalance(acc.getMarginBalance() - amount);
        acc.setAvailableBalance(acc.getAvailableBalance() - amount);
        accountMapper.updateById(acc);
        return acc;
    }

    @Override
    @Transactional
    public FuturesAccount addPositionMargin(Long userId, String coin, Long amount) {
        FuturesAccount acc = getOrCreate(userId, coin);
        if (acc.getAvailableBalance() < amount) {
            throw new BusinessException("合约可用余额不足，无法开仓");
        }
        acc.setAvailableBalance(acc.getAvailableBalance() - amount);
        acc.setPositionMargin(acc.getPositionMargin() + amount);
        accountMapper.updateById(acc);
        return acc;
    }

    @Override
    @Transactional
    public FuturesAccount releasePositionMargin(Long userId, String coin, Long amount) {
        FuturesAccount acc = getOrCreate(userId, coin);
        long rel = Math.min(amount, acc.getPositionMargin());
        acc.setPositionMargin(Math.max(0, acc.getPositionMargin() - rel));
        acc.setAvailableBalance(acc.getAvailableBalance() + rel);
        accountMapper.updateById(acc);
        return acc;
    }

    @Override
    @Transactional
    public FuturesAccount settleRealizedPnl(Long userId, String coin, Long pnl) {
        FuturesAccount acc = getOrCreate(userId, coin);
        acc.setMarginBalance(acc.getMarginBalance() + pnl);
        acc.setAvailableBalance(acc.getAvailableBalance() + pnl);
        acc.setRealizedPnl(acc.getRealizedPnl() + pnl);
        accountMapper.updateById(acc);
        return acc;
    }
}
