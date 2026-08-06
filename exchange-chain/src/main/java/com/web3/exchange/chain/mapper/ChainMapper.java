package com.web3.exchange.chain.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.web3.exchange.chain.entity.Chain;
import org.apache.ibatis.annotations.Mapper;

/**
 * 链配置 Mapper（读 asset 库 t_chain）。
 */
@Mapper
public interface ChainMapper extends BaseMapper<Chain> {
}
