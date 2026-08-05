package com.web3.exchange.asset.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.web3.exchange.asset.entity.Account;
import com.web3.exchange.asset.entity.Coin;
import com.web3.exchange.asset.mapper.AccountMapper;
import com.web3.exchange.asset.service.AccountService;
import com.web3.exchange.asset.service.CoinService;
import com.web3.exchange.common.asset.dto.AccountVO;
import com.web3.exchange.common.exception.BusinessException;
import com.web3.exchange.common.exception.NotFoundException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class AccountServiceImpl extends ServiceImpl<AccountMapper, Account> implements AccountService {

    private final CoinService coinService;

    public AccountServiceImpl(CoinService coinService) {
        this.coinService = coinService;
    }

    @Override
    public AccountVO open(Long userId, String symbol) {
        Account exist = getByUserAndSymbol(userId, symbol);
        if (exist != null) {
            return toVO(exist);
        }
        // 首次为所有已配置币种各建一行（幂等：uk_user_symbol 兜底）
        List<Coin> coins = coinService.list();
        for (Coin coin : coins) {
            createAccountQuietly(userId, coin);
        }
        Account account = getByUserAndSymbol(userId, symbol);
        if (account == null) {
            throw new BusinessException("币种不存在: " + symbol);
        }
        return toVO(account);
    }

    @Override
    public AccountVO getBalance(Long userId, String symbol) {
        Account account = getByUserAndSymbol(userId, symbol);
        if (account == null) {
            throw new NotFoundException("账户不存在: userId=" + userId + ", symbol=" + symbol);
        }
        return toVO(account);
    }

    @Override
    public List<AccountVO> listByUser(Long userId) {
        return list(new LambdaQueryWrapper<Account>()
                        .eq(Account::getUserId, userId)
                        .orderByAsc(Account::getSymbol))
                .stream().map(this::toVO).collect(Collectors.toList());
    }

    @Override
    public Account lockByUserAndSymbol(Long userId, String symbol) {
        Account account = baseMapper.selectByUserAndSymbolForUpdate(userId, symbol);
        if (account != null) {
            return account;
        }
        Coin coin = coinService.getBySymbol(symbol);
        if (coin == null) {
            throw new BusinessException("币种不存在: " + symbol);
        }
        createAccountQuietly(userId, coin);
        account = baseMapper.selectByUserAndSymbolForUpdate(userId, symbol);
        if (account == null) {
            throw new BusinessException("账户创建失败");
        }
        return account;
    }

    private void createAccountQuietly(Long userId, Coin coin) {
        if (coin == null) {
            return;
        }
        try {
            Account account = new Account()
                    .setUserId(userId)
                    .setCoinId(coin.getId())
                    .setSymbol(coin.getSymbol())
                    .setAvailable(0L)
                    .setFrozen(0L)
                    .setTotal(0L)
                    .setStatus(1);
            this.save(account);
        } catch (DuplicateKeyException ignore) {
            // 已存在，忽略
        }
    }

    private Account getByUserAndSymbol(Long userId, String symbol) {
        return this.getOne(new LambdaQueryWrapper<Account>()
                .eq(Account::getUserId, userId)
                .eq(Account::getSymbol, symbol)
                .last("limit 1"), false);
    }

    @Override
    public AccountVO toVO(Account a) {
        AccountVO vo = new AccountVO();
        vo.setAccountId(a.getId());
        vo.setUserId(a.getUserId());
        vo.setCoinId(a.getCoinId());
        vo.setSymbol(a.getSymbol());
        vo.setAvailable(a.getAvailable());
        vo.setFrozen(a.getFrozen());
        vo.setTotal(a.getTotal());
        vo.setStatus(a.getStatus());
        vo.setVersion(a.getVersion());
        return vo;
    }
}
