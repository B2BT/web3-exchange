package com.web3.exchange.order.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.web3.exchange.order.entity.Trade;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 成交 Mapper。
 */
@Mapper
public interface TradeMapper extends BaseMapper<Trade> {

    /**
     * 查询结算失败待补偿的成交（settle_status=2），按 symbol 过滤，用于定时补偿扫描。
     */
    @Select("SELECT * FROM t_trade WHERE symbol = #{symbol} AND settle_status = 2 AND is_deleted = 0 LIMIT 200")
    List<Trade> selectPendingSettle(@Param("symbol") String symbol);

    /**
     * 查询某交易对最近成交（按 trade_time 降序取前 limit 条），供公开行情接口使用。
     */
    @Select("SELECT * FROM t_trade WHERE symbol = #{symbol} AND is_deleted = 0 ORDER BY trade_time DESC LIMIT #{limit}")
    List<Trade> selectRecentBySymbol(@Param("symbol") String symbol, @Param("limit") int limit);
}
