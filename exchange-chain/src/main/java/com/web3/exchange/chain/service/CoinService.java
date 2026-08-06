package com.web3.exchange.chain.service;

import com.web3.exchange.chain.entity.Coin;

import java.util.List;

/**
 * 币种配置服务。
 */
public interface CoinService {

    /** 按币种符号查币种。 */
    Coin getBySymbol(String symbol);

    /** 某链下所有币种（本链场景取全部，按需过滤类型）。 */
    List<Coin> listByChain(String chainCode);
}
