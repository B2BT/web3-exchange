package com.web3.exchange.futures.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.web3.exchange.futures.entity.FuturesFillEntity;
import org.apache.ibatis.annotations.Mapper;

/**
 * 永续合约成交明细 Mapper。
 */
@Mapper
public interface FuturesFillMapper extends BaseMapper<FuturesFillEntity> {
}
