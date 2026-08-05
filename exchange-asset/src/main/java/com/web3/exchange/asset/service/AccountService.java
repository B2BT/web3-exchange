package com.web3.exchange.asset.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.web3.exchange.asset.entity.Account;
import com.web3.exchange.common.asset.dto.AccountVO;

import java.util.List;

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
