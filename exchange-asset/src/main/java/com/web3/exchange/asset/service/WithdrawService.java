package com.web3.exchange.asset.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.web3.exchange.asset.entity.Withdraw;

/**
 * 提现服务——用户出金记录（t_withdraw）的管理。
 * <p>记录提现申请/审核/上链状态；业务流后续完善，当前提供 MyBatis-Plus 通用 CRUD。</p>
 */
public interface WithdrawService extends IService<Withdraw> {
}
