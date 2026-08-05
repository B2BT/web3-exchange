package com.web3.exchange.asset.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.web3.exchange.asset.entity.AssetAddress;

/**
 * 充币地址服务——用户/钱包的链上收款地址（t_asset_address）管理。
 * <p>为充值提供收款地址，供 chain 识别入账归属。地址派生/轮换等能力后续完善，
 * 当前提供 MyBatis-Plus 通用 CRUD。</p>
 */
public interface AssetAddressService extends IService<AssetAddress> {
}
