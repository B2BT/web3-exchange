package com.web3.exchange.admin.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.web3.exchange.admin.entity.Order;
import com.web3.exchange.admin.mapper.OrderMapper;
import com.web3.exchange.admin.service.AdminOrderService;
import com.web3.exchange.admin.vo.OrderVO;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * 后台订单管理服务实现：直接查 t_order（同库），全站跨用户分页。
 */
@Service
public class AdminOrderServiceImpl implements AdminOrderService {

    private final OrderMapper orderMapper;

    public AdminOrderServiceImpl(OrderMapper orderMapper) {
        this.orderMapper = orderMapper;
    }

    @Override
    public Page<OrderVO> pageOrders(int page, int size, String symbol, Integer status) {
        LambdaQueryWrapper<Order> qw = new LambdaQueryWrapper<>();
        if (StringUtils.hasText(symbol)) {
            qw.eq(Order::getSymbol, symbol.trim());
        }
        if (status != null) {
            qw.eq(Order::getStatus, status);
        }
        qw.orderByDesc(Order::getId);
        Page<Order> p = orderMapper.selectPage(new Page<>(page, size), qw);
        Page<OrderVO> voPage = new Page<>(p.getCurrent(), p.getSize(), p.getTotal());
        voPage.setRecords(p.getRecords().stream().map(this::toVO).toList());
        return voPage;
    }

    private OrderVO toVO(Order o) {
        OrderVO vo = new OrderVO();
        vo.setId(o.getId());
        vo.setOrderNo(o.getOrderNo());
        vo.setClientOid(o.getClientOid());
        vo.setUserId(o.getUserId());
        vo.setSymbol(o.getSymbol());
        vo.setBaseCoin(o.getBaseCoin());
        vo.setQuoteCoin(o.getQuoteCoin());
        vo.setSide(o.getSide());
        vo.setOrderType(o.getOrderType());
        vo.setTimeInForce(o.getTimeInForce());
        vo.setTriggerType(o.getTriggerType());
        vo.setTriggerPrice(o.getTriggerPrice());
        vo.setTriggerStatus(o.getTriggerStatus());
        vo.setOcoGroup(o.getOcoGroup());
        vo.setPrice(o.getPrice());
        vo.setQuantity(o.getQuantity());
        vo.setQuoteAmount(o.getQuoteAmount());
        vo.setRemaining(o.getRemaining());
        vo.setFilledAmount(o.getFilledAmount());
        vo.setFilledQuoteAmount(o.getFilledQuoteAmount());
        vo.setAvgPrice(o.getAvgPrice());
        vo.setTradeCount(o.getTradeCount());
        vo.setFee(o.getFee());
        vo.setFreezeQuoteAmount(o.getFreezeQuoteAmount());
        vo.setFreezeBaseAmount(o.getFreezeBaseAmount());
        vo.setStatus(o.getStatus());
        vo.setRemark(o.getRemark());
        vo.setCreateTime(o.getCreateTime());
        vo.setCancelTime(o.getCancelTime());
        vo.setFilledTime(o.getFilledTime());
        return vo;
    }
}
