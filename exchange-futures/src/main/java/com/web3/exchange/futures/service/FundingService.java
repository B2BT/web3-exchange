package com.web3.exchange.futures.service;

/**
 * 资金费率结算服务（M4）。
 * <p>每 funding_interval 定时结算：多空持仓按资金费率在账户间转移，使合约价锚定现货价。</p>
 */
public interface FundingService {

    /** 对所有上架合约执行一次资金费率结算。返回处理的交易对数。 */
    int settleAll();
}
