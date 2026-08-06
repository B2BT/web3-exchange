package com.web3.exchange.admin.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.web3.exchange.admin.vo.OrderVO;

/**
 * 后台订单管理服务：全站订单分页（跨用户）。
 */
public interface AdminOrderService {

    /** 全站订单分页，支持 symbol/status 过滤。 */
    Page<OrderVO> pageOrders(int page, int size, String symbol, Integer status);
}
