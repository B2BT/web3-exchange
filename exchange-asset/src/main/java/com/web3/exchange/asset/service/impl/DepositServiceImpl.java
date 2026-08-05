package com.web3.exchange.asset.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.web3.exchange.asset.entity.Deposit;
import com.web3.exchange.asset.mapper.DepositMapper;
import com.web3.exchange.asset.service.DepositService;
import org.springframework.stereotype.Service;

@Service
public class DepositServiceImpl extends ServiceImpl<DepositMapper, Deposit> implements DepositService {
}
