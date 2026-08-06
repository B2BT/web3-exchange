package com.web3.exchange.chain.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.web3.exchange.chain.entity.Coin;
import org.apache.ibatis.annotations.Mapper;

/**
 * 币种 Mapper（读 asset 库 t_coin）。
 */
@Mapper
public interface CoinMapper extends BaseMapper<Coin> {
}
