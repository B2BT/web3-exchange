package com.web3.exchange.admin.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.web3.exchange.admin.service.AdminOrderService;
import com.web3.exchange.admin.vo.OrderVO;
import com.web3.exchange.common.model.PageData;
import com.web3.exchange.common.model.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 后台订单管理接口（/api/admin/order/**，需 ADMIN 角色）。全站跨用户订单分页。
 */
@RestController
@RequestMapping("/api/admin/order")
@Tag(name = "后台订单管理")
public class OrderAdminController {

    private final AdminOrderService adminOrderService;

    public OrderAdminController(AdminOrderService adminOrderService) {
        this.adminOrderService = adminOrderService;
    }

    @Operation(summary = "全站订单分页")
    @GetMapping("/list")
    public Result<PageData<OrderVO>> list(@RequestParam(value = "page", defaultValue = "1") int page,
                                          @RequestParam(value = "size", defaultValue = "20") int size,
                                          @RequestParam(value = "symbol", required = false) String symbol,
                                          @RequestParam(value = "status", required = false) Integer status) {
        Page<OrderVO> p = adminOrderService.pageOrders(page, size, symbol, status);
        return Result.success(PageData.of(p));
    }
}
