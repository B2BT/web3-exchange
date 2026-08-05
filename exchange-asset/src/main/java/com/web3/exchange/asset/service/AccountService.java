package com.web3.exchange.asset.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.web3.exchange.asset.entity.Account;
import com.web3.exchange.common.asset.dto.AccountVO;

import java.util.List;

/**
 * 账户服务——钱包账户的开户、余额查询与行锁锁定。
 * <p>开户幂等、每人每币种一账户；资金变动前必须先经 lockByUserAndSymbol
 * 以 FOR UPDATE 行锁锁定账户，保证并发资金操作串行化。</p>
 */
public interface AccountService extends IService<Account> {

    /**
     * 幂等开户：确保用户该币种账户存在（首次为所有已配置币种各建一行），返回指定币种账户。
     */
    AccountVO open(Long userId, String symbol);

    /**
     * 查询单账户余额；不存在返回 null。
     */
    AccountVO getBalance(Long userId, String symbol);

    /**
     * 查询用户全部币种账户（钱包总览）。
     */
    List<AccountVO> listByUser(Long userId);

    /**
     * 行锁：按 user+symbol 锁定账户（FOR UPDATE），不存在则自动开户后再次锁定。
     * 必须在事务内调用。
     */
    Account lockByUserAndSymbol(Long userId, String symbol);

    AccountVO toVO(Account account);
}
