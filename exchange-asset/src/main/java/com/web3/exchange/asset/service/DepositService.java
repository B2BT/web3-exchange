package com.web3.exchange.asset.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.web3.exchange.asset.entity.Deposit;

/**
 * 充值服务——链上充值记录（t_deposit）的管理。
 * <p>记录每笔充值交易及入账状态，配合 chain 扫描与 credit 入账。tx_hash 唯一，
 * 保证同一笔链上交易只能入账一次。当前提供 MyBatis-Plus 通用 CRUD。</p>
 */
public interface DepositService extends IService<Deposit> {
}
