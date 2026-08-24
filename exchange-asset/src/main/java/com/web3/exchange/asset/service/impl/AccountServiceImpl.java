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

/**
 * 账户服务实现——钱包账户的开户、余额查询与<b>行锁锁定</b>。
 * <p>
 * 开户采用幂等设计：确保用户指定币种账户存在，首次开户会为所有已配置币种各建一行，
 * 重复开户由唯一索引 uk_user_symbol 兜底、返回既有账户。所有资金变动前都必须先经
 * {@link #lockByUserAndSymbol} 以 SELECT ... FOR UPDATE 行锁锁定账户行，
 * 从而将同一账户的并发资金操作串行化（资金安全第一道防线）。
 * </p>
 */
@Service
public class AccountServiceImpl extends ServiceImpl<AccountMapper, Account> implements AccountService {

    private final CoinService coinService;

    /** 读路径缓存：账户余额查询（Caffeine 2s，资金写时主动失效或短TTL兜底）。 */
    private final com.github.benmanes.caffeine.cache.Cache<String, AccountVO> balanceCache =
            com.github.benmanes.caffeine.cache.Caffeine.newBuilder()
                    .expireAfterWrite(java.time.Duration.ofSeconds(2))
                    .maximumSize(2048)
                    .build();

    /** 读路径缓存：用户全部账户列表（Caffeine 2s）。 */
    private final com.github.benmanes.caffeine.cache.Cache<Long, List<AccountVO>> userAccountsCache =
            com.github.benmanes.caffeine.cache.Caffeine.newBuilder()
                    .expireAfterWrite(java.time.Duration.ofSeconds(2))
                    .maximumSize(2048)
                    .build();

    public AccountServiceImpl(CoinService coinService) {
        this.coinService = coinService;
    }

    /** 资金变动后主动失效该用户缓存（由 LedgerService 资金操作在事务提交后调用）。 */
    public void invalidate(Long userId) {
        if (userId != null) {
            balanceCache.asMap().entrySet().removeIf(e -> e.getKey().startsWith(userId + ":"));
            userAccountsCache.invalidate(userId);
        }
    }

    /**
     * 幂等开户：确保用户该币种账户存在；若不存在，先为该用户所有已配置币种各建一行
     * （首次开户），再返回指定币种账户。并发下由 uk_user_symbol 唯一索引兜底，防重复建行。
     */
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
        String key = userId + ":" + symbol;
        AccountVO cached = balanceCache.getIfPresent(key);
        if (cached != null) {
            return cached;
        }
        Account account = getByUserAndSymbol(userId, symbol);
        if (account == null) {
            throw new NotFoundException("账户不存在: userId=" + userId + ", symbol=" + symbol);
        }
        AccountVO vo = toVO(account);
        balanceCache.put(key, vo);
        return vo;
    }

    @Override
    public List<AccountVO> listByUser(Long userId) {
        List<AccountVO> cached = userAccountsCache.getIfPresent(userId);
        if (cached != null) {
            return cached;
        }
        List<AccountVO> vos = list(new LambdaQueryWrapper<Account>()
                        .eq(Account::getUserId, userId)
                        .orderByAsc(Account::getSymbol))
                .stream().map(this::toVO).collect(Collectors.toList());
        userAccountsCache.put(userId, vos);
        return vos;
    }

    /**
     * <b>行锁锁定账户</b>（SELECT ... FOR UPDATE）：按 user+symbol 锁定账户行，
     * 使当前事务对该账户的资金操作串行化。账户不存在时自动开户后再锁定，
     * 以保证资金变动总有账户可锁。
     * <p><b>必须在事务内调用</b>（配合 LedgerService.doChange 在同一事务内完成
     * 「写流水 + 更新余额」），否则锁会在语句结束后立即释放、失去串行化效果。</p>
     */
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
