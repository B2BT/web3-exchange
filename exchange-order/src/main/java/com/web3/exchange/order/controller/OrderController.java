package com.web3.exchange.order.controller;

import com.web3.exchange.common.model.Result;
import com.web3.exchange.order.dto.CancelOrderRequest;
import com.web3.exchange.order.dto.OrderVO;
import com.web3.exchange.order.dto.PlaceOrderRequest;
import com.web3.exchange.order.dto.TradeVO;
import com.web3.exchange.order.service.CancelService;
import com.web3.exchange.order.service.OrderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 订单<b>对外</b> REST 接口（/api/order/**，经网关路由）。
 * <p>提供下单、撤单、查单、查成交。资金操作一律走 asset 内部接口，本控制器只负责业务编排。</p>
 */
@RestController
@RequestMapping("/api/order")
@Tag(name = "订单对外接口")
public class OrderController {

    private final OrderService orderService;
    private final CancelService cancelService;

    public OrderController(OrderService orderService, CancelService cancelService) {
        this.orderService = orderService;
        this.cancelService = cancelService;
    }

    /**
     * 下单：校验 → 落库 → 冻结 → 撮合 → 成交落库/过户。返回订单 + 本次成交。
     */
    @Operation(summary = "下单")
    @PostMapping("/place")
    public Result<OrderService.PlaceOrderResult> place(@Valid @RequestBody PlaceOrderRequest req) {
        return Result.success(orderService.placeOrder(req));
    }

    /**
     * 撤单：仅 NEW/PARTIAL_FILLED 可撤；FILLED/CANCELLED/REJECTED 幂等返回「已终态」。
     */
    @Operation(summary = "撤单")
    @PostMapping("/cancel")
    public Result<OrderVO> cancel(@Valid @RequestBody CancelOrderRequest req) {
        return Result.success(cancelService.cancel(req));
    }

    /**
     * 查询订单。
     */
    @Operation(summary = "查询订单")
    @GetMapping("/get")
    public Result<OrderVO> get(@RequestParam("userId") Long userId,
                               @RequestParam("orderNo") String orderNo) {
        return Result.success(orderService.getOrder(userId, orderNo));
    }

    /**
     * 查询某订单的成交明细。
     */
    @Operation(summary = "查询订单成交明细")
    @GetMapping("/trades")
    public Result<List<TradeVO>> trades(@RequestParam("userId") Long userId,
                                        @RequestParam("orderNo") String orderNo) {
        return Result.success(orderService.listTrades(userId, orderNo));
    }
}
