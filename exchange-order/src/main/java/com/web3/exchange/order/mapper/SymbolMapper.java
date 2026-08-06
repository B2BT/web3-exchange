package com.web3.exchange.order.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.web3.exchange.order.entity.Symbol;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 交易对 Mapper。
 */
@Mapper
public interface SymbolMapper extends BaseMapper<Symbol> {

    /**
     * 查询交易中（status=1）的交易对。
     */
    @Select("SELECT * FROM t_symbol WHERE symbol = #{symbol} AND status = 1 AND is_deleted = 0 LIMIT 1")
    Symbol selectActiveBySymbol(@Param("symbol") String symbol);
}
