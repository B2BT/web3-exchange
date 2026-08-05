package com.web3.exchange.asset.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.web3.exchange.asset.entity.Withdraw;
import com.web3.exchange.asset.mapper.WithdrawMapper;
import com.web3.exchange.asset.service.WithdrawService;
import org.springframework.stereotype.Service;

@Service
public class WithdrawServiceImpl extends ServiceImpl<WithdrawMapper, Withdraw> implements WithdrawService {
}
