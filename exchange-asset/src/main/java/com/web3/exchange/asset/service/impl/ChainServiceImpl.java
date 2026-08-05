package com.web3.exchange.asset.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.web3.exchange.asset.entity.Chain;
import com.web3.exchange.asset.mapper.ChainMapper;
import com.web3.exchange.asset.service.ChainService;
import org.springframework.stereotype.Service;

/**
 * 链配置服务实现——底层区块链（t_chain）配置的查询与管理。
 * <p>集中维护各链的节点/确认数/Gas 参数，供充值扫描、提现上链等后续能力读取。
 * 当前提供 MyBatis-Plus 通用 CRUD。</p>
 */
@Service
public class ChainServiceImpl extends ServiceImpl<ChainMapper, Chain> implements ChainService {
}
