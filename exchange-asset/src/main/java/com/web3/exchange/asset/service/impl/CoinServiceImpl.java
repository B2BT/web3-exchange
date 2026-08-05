package com.web3.exchange.asset.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.web3.exchange.asset.entity.Coin;
import com.web3.exchange.asset.mapper.CoinMapper;
import com.web3.exchange.asset.service.CoinService;
import org.springframework.stereotype.Service;

/**
 * 币种服务实现——按 symbol 查询币种配置（含 decimals 精度），
 * 供开户、金额换算等业务读取；当前提供 MyBatis-Plus 通用 CRUD。
 */
@Service
public class CoinServiceImpl extends ServiceImpl<CoinMapper, Coin> implements CoinService {

    @Override
    public Coin getBySymbol(String symbol) {
        return this.getOne(new LambdaQueryWrapper<Coin>()
                .eq(Coin::getSymbol, symbol)
                .last("limit 1"), false);
    }
}
