package com.web3.exchange.futures.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.web3.exchange.futures.entity.MarkPrice;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface MarkPriceMapper extends BaseMapper<MarkPrice> {
}
