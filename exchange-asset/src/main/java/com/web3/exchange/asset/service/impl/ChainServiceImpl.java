package com.web3.exchange.asset.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.web3.exchange.asset.entity.Chain;
import com.web3.exchange.asset.mapper.ChainMapper;
import com.web3.exchange.asset.service.ChainService;
import org.springframework.stereotype.Service;

@Service
public class ChainServiceImpl extends ServiceImpl<ChainMapper, Chain> implements ChainService {
}
