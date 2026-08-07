package com.web3.exchange.staking.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.web3.exchange.staking.entity.StakingProduct;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface StakingProductMapper extends BaseMapper<StakingProduct> {
}
