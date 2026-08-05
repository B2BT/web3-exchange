package com.web3.exchange.asset.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.web3.exchange.asset.entity.AssetAddress;
import com.web3.exchange.asset.mapper.AssetAddressMapper;
import com.web3.exchange.asset.service.AssetAddressService;
import org.springframework.stereotype.Service;

/**
 * 充币地址服务实现——用户/钱包的链上收款地址（t_asset_address）管理。
 * <p>为充值提供收款地址，供 chain 识别入账归属。地址派生/轮换等能力后续完善，
 * 当前提供 MyBatis-Plus 通用 CRUD。</p>
 */
@Service
public class AssetAddressServiceImpl extends ServiceImpl<AssetAddressMapper, AssetAddress> implements AssetAddressService {
}
