package com.web3.exchange.chain.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.web3.exchange.chain.entity.Chain;
import com.web3.exchange.chain.mapper.ChainMapper;
import com.web3.exchange.chain.service.ChainService;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 链配置服务实现。
 */
@Service
public class ChainServiceImpl extends ServiceImpl<ChainMapper, Chain> implements ChainService {

    @Override
    public List<Chain> listEnabled() {
        return list(new LambdaQueryWrapper<Chain>()
                .eq(Chain::getScanEnabled, 1)
                .eq(Chain::getStatus, 1)
                .orderByAsc(Chain::getSort));
    }

    @Override
    public Chain getByChainCode(String chainCode) {
        return getOne(new LambdaQueryWrapper<Chain>()
                .eq(Chain::getChainCode, chainCode)
                .last("limit 1"), false);
    }
}
