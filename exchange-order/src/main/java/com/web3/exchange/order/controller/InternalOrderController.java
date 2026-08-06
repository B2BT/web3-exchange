package com.web3.exchange.order.controller;

import com.web3.exchange.common.model.Result;
import com.web3.exchange.order.engine.MatchingEngine;
import com.web3.exchange.order.service.TradeService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

/**
 * 订单<b>内部</b>接口（/internal/order/**）——仅供服务间调用/运维，网关不路由 /internal/**。
 * <p>提供：结算失败补偿触发、内存订单簿状态快照（运维/对账用）。</p>
 */
@RestController
@RequestMapping("/internal/order")
@Tag(name = "订单内部接口")
public class InternalOrderController {

    private final TradeService tradeService;
    private final MatchingEngine matchingEngine;

    public InternalOrderController(TradeService tradeService, MatchingEngine matchingEngine) {
        this.tradeService = tradeService;
        this.matchingEngine = matchingEngine;
    }

    /**
     * 触发成交结算补偿：对 settle_status=2 的成交幂等重试过户。
     */
    @Operation(summary = "触发成交结算补偿")
    @PostMapping("/compensate")
    public Result<Object> compensate(@RequestParam("symbol") String symbol) {
        tradeService.compensatePending(symbol);
        return Result.success(null, "补偿触发完成");
    }

    /**
     * 内存订单簿状态快照（运维/验证用）。
     */
    @Operation(summary = "订单簿状态快照")
    @GetMapping("/book")
    public Result<Map<String, Object>> book(@RequestParam("symbol") String symbol) {
        Map<String, Object> m = new HashMap<>();
        m.put("symbol", symbol);
        m.put("orderCount", matchingEngine.orderCount(symbol));
        m.put("bestBidPrice", matchingEngine.bestBidPrice(symbol));
        m.put("bestAskPrice", matchingEngine.bestAskPrice(symbol));
        return Result.success(m);
    }
}
