package com.web3.exchange.order.config;

import com.web3.exchange.order.engine.MatchingEngine;
import com.web3.exchange.order.entity.Order;
import com.web3.exchange.order.mapper.OrderMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 启动重建订单簿（docs/order-domain.md §5.6，Phase 2 简化）。
 * <p>
 * 应用就绪后，把 t_order 中 status IN (0,1) 且 remaining>0 的限价单按 create_time 升序
 * 重新灌入内存订单簿。已冻结部分直接入簿（不重复冻结/过户），靠 asset 幂等 requestId 兜底；
 * 市价单一次性撮合不落簿，无需恢复。
 * </p>
 */
@Slf4j
@Component
public class OrderBookRecovery implements ApplicationListener<ApplicationReadyEvent> {

    private final OrderMapper orderMapper;
    private final MatchingEngine matchingEngine;

    public OrderBookRecovery(OrderMapper orderMapper, MatchingEngine matchingEngine) {
        this.orderMapper = orderMapper;
        this.matchingEngine = matchingEngine;
    }

    @Override
    public void onApplicationEvent(ApplicationReadyEvent event) {
        List<Order> active = orderMapper.selectAllActiveLimitOrders();
        if (active.isEmpty()) {
            log.info("[order] 启动重建订单簿：无活跃限价单");
            return;
        }
        Map<String, List<Order>> bySymbol = active.stream()
                .collect(Collectors.groupingBy(Order::getSymbol));
        for (Map.Entry<String, List<Order>> e : bySymbol.entrySet()) {
            for (Order o : e.getValue()) {
                matchingEngine.addRestingOrder(o);
            }
            log.info("[order] 启动重建订单簿：{} 恢复 {} 个活跃限价单", e.getKey(), e.getValue().size());
        }
    }
}
