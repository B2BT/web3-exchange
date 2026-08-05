package com.web3.exchange.asset.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.web3.exchange.asset.entity.Deposit;
import com.web3.exchange.asset.mapper.DepositMapper;
import com.web3.exchange.asset.service.DepositService;
import org.springframework.stereotype.Service;

/**
 * 充值服务实现——链上充值记录（t_deposit）的管理。
 * <p>记录每笔充值交易及入账状态，配合 chain 扫描与 credit 入账。tx_hash 唯一，
 * 保证同一笔链上交易只能入账一次。当前提供 MyBatis-Plus 通用 CRUD。</p>
 */
@Service
public class DepositServiceImpl extends ServiceImpl<DepositMapper, Deposit> implements DepositService {
}
