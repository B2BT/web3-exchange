package com.web3.exchange.chain.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.web3.exchange.chain.entity.AssetAddress;
import org.apache.ibatis.annotations.Mapper;

/**
 * 充币地址 Mapper（读 asset 库 t_asset_address）。
 */
@Mapper
public interface AssetAddressMapper extends BaseMapper<AssetAddress> {
}
