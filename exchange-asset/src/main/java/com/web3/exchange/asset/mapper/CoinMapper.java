package com.web3.exchange.asset.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.web3.exchange.asset.entity.Coin;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface CoinMapper extends BaseMapper<Coin> {
}
