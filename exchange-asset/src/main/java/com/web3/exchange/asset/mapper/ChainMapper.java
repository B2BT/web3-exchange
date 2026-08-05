package com.web3.exchange.asset.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.web3.exchange.asset.entity.Chain;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ChainMapper extends BaseMapper<Chain> {
}
