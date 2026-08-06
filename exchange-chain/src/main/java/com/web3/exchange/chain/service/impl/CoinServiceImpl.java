package com.web3.exchange.chain.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.web3.exchange.chain.entity.Coin;
import com.web3.exchange.chain.mapper.CoinMapper;
import com.web3.exchange.chain.service.CoinService;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 币种配置服务实现。
 */
@Service
public class CoinServiceImpl extends ServiceImpl<CoinMapper, Coin> implements CoinService {

    @Override
    public Coin getBySymbol(String symbol) {
        return getOne(new LambdaQueryWrapper<Coin>()
                .eq(Coin::getSymbol, symbol)
                .last("limit 1"), false);
    }

    @Override
    public List<Coin> listByChain(String chainCode) {
        return list(new LambdaQueryWrapper<Coin>()
                .eq(Coin::getChainCode, chainCode)
                .eq(Coin::getStatus, 1)
                .orderByAsc(Coin::getSort));
    }
}
