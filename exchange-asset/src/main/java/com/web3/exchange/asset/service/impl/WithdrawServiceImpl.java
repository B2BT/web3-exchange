package com.web3.exchange.asset.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.web3.exchange.asset.entity.Withdraw;
import com.web3.exchange.asset.mapper.WithdrawMapper;
import com.web3.exchange.asset.service.WithdrawService;
import org.springframework.stereotype.Service;

/**
 * 提现服务实现——用户出金记录（t_withdraw）的管理。
 * <p>记录提现申请/审核/上链状态；业务流后续完善，当前提供 MyBatis-Plus 通用 CRUD。</p>
 */
@Service
public class WithdrawServiceImpl extends ServiceImpl<WithdrawMapper, Withdraw> implements WithdrawService {
}
