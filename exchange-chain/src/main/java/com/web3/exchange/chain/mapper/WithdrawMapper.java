package com.web3.exchange.chain.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.web3.exchange.chain.entity.Withdraw;
import org.apache.ibatis.annotations.Mapper;

/**
 * 提现记录 Mapper（读/写 asset 库 t_withdraw）。
 */
@Mapper
public interface WithdrawMapper extends BaseMapper<Withdraw> {
}
