package com.web3.exchange.asset.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.web3.exchange.asset.entity.Chain;

/**
 * 链配置服务——底层区块链（t_chain）配置的查询与管理。
 * <p>集中维护各链的节点/确认数/Gas 参数，供充值扫描、提现上链等后续能力读取。
 * 当前提供 MyBatis-Plus 通用 CRUD。</p>
 */
public interface ChainService extends IService<Chain> {
}
