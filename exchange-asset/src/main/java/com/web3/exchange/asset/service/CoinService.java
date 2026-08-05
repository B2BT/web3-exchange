package com.web3.exchange.asset.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.web3.exchange.asset.entity.Coin;

public interface CoinService extends IService<Coin> {
    /**
     * 按符号查询币种（含精度），不存在返回 null
     */
    Coin getBySymbol(String symbol);
}
