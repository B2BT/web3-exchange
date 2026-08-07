package com.web3.exchange.futures.service;

import com.web3.exchange.futures.entity.FuturesAccount;

/**
 * 合约账户服务。
 */
public interface FuturesAccountService {

    /** 幂等获取合约账户（不存在则创建，初始0）。 */
    FuturesAccount getOrCreate(Long userId, String coin);

    /** 划入合约账户（从现货转账或测试入金）。返回新账户。 */
    FuturesAccount deposit(Long userId, String coin, Long amount);

    /** 划出合约账户（转回现货）。 */
    FuturesAccount withdraw(Long userId, String coin, Long amount);

    /** 加保证金（开仓时占用）。 */
    FuturesAccount addPositionMargin(Long userId, String coin, Long amount);

    /** 释放保证金（平仓/减仓时）。 */
    FuturesAccount releasePositionMargin(Long userId, String coin, Long amount);

    /** 结算已实现盈亏（累加到余额与 realized_pnl）。 */
    FuturesAccount settleRealizedPnl(Long userId, String coin, Long pnl);
}
