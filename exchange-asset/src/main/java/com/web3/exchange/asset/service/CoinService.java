package com.web3.exchange.asset.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.web3.exchange.asset.entity.Coin;

/**
 * 币种服务——平台支持币种（t_coin）的查询。
 * <p>提供按 symbol 查币种（含 decimals 精度等），供开户、金额换算等使用。</p>
 */
public interface CoinService extends IService<Coin> {
    /**
     * 按符号查询币种（含精度），不存在返回 null
     */
    Coin getBySymbol(String symbol);
}
