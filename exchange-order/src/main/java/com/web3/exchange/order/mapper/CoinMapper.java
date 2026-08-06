package com.web3.exchange.order.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.web3.exchange.order.entity.Coin;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 币种 Mapper（order 域只读，查 t_coin 精度）。
 */
@Mapper
public interface CoinMapper extends BaseMapper<Coin> {

    /**
     * 按符号查询币种（含精度 decimals），不存在返回 null。
     */
    @Select("SELECT id, symbol, decimals FROM t_coin WHERE symbol = #{symbol} AND is_deleted = 0 LIMIT 1")
    Coin selectBySymbol(@Param("symbol") String symbol);
}
