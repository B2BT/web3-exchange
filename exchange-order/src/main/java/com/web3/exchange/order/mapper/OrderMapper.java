package com.web3.exchange.order.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.web3.exchange.order.entity.Order;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 订单 Mapper。
 */
@Mapper
public interface OrderMapper extends BaseMapper<Order> {

    /**
     * 加载活跃订单（status IN (0,1) 且 remaining>0 的限价单）用于启动重建订单簿，按 create_time 升序。
     */
    @Select("SELECT * FROM t_order WHERE symbol = #{symbol} AND side = #{side} "
            + "AND order_type = 1 AND remaining > 0 AND status IN (0,1) AND is_deleted = 0 "
            + "ORDER BY create_time ASC")
    List<Order> selectActiveLimitOrders(@Param("symbol") String symbol, @Param("side") Integer side);

    /**
     * 加载全部交易对的活跃限价单（status IN (0,1) 且 remaining>0），按 create_time 升序，
     * 用于启动重建全部订单簿（组内按 symbol 分组后仍保持时间序）。
     */
    @Select("SELECT * FROM t_order WHERE order_type = 1 AND remaining > 0 "
            + "AND status IN (0,1) AND is_deleted = 0 ORDER BY create_time ASC")
    List<Order> selectAllActiveLimitOrders();
}
