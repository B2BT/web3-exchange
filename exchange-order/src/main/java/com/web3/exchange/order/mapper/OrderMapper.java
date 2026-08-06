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
            + "AND NOT (trigger_type > 0 AND trigger_status = 0) "
            + "ORDER BY create_time ASC")
    List<Order> selectActiveLimitOrders(@Param("symbol") String symbol, @Param("side") Integer side);

    /**
     * 加载全部交易对的活跃限价单（status IN (0,1) 且 remaining>0），按 create_time 升序，
     * 用于启动重建全部订单簿（组内按 symbol 分组后仍保持时间序）。
     * <p>排除未触发的条件单（trigger_type>0 且 trigger_status=0）：它们不入盘口，待行情触发。</p>
     */
    @Select("SELECT * FROM t_order WHERE order_type = 1 AND remaining > 0 "
            + "AND status IN (0,1) AND is_deleted = 0 "
            + "AND NOT (trigger_type > 0 AND trigger_status = 0) "
            + "ORDER BY create_time ASC")
    List<Order> selectAllActiveLimitOrders();

    /**
     * 加载某交易对全部待触发条件单（trigger_status=0 且 trigger_type>0），用于行情触发任务（idx_trigger_status_symbol）。
     */
    @Select("SELECT * FROM t_order WHERE symbol = #{symbol} AND trigger_type > 0 AND trigger_status = 0 "
            + "AND status = 0 AND is_deleted = 0 ORDER BY create_time ASC")
    List<Order> selectPendingConditionalOrders(@Param("symbol") String symbol);

    /**
     * 加载存在待触发条件单的交易对去重列表，供行情触发任务拉取行情。
     */
    @Select("SELECT DISTINCT symbol FROM t_order WHERE trigger_type > 0 AND trigger_status = 0 "
            + "AND status = 0 AND is_deleted = 0")
    List<String> selectPendingTriggerSymbols();
}
