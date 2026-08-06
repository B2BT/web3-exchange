package com.web3.exchange.order.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.web3.exchange.common.asset.dto.UnfreezeRequest;
import com.web3.exchange.common.exception.ServiceException;
import com.web3.exchange.common.model.Result;
import com.web3.exchange.order.constant.OrderConstant;
import com.web3.exchange.order.dto.CancelOrderRequest;
import com.web3.exchange.order.dto.OrderVO;
import com.web3.exchange.order.engine.MatchingEngine;
import com.web3.exchange.order.entity.Order;
import com.web3.exchange.order.feign.AssetClient;
import com.web3.exchange.order.mapper.OrderMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * 撤单服务：<b>簿移除 → 状态置 CANCELLED → 解冻剩余</b>（docs/order-domain.md §4/§6.3）。
 * <p>
 * 前置校验：仅 NEW(0)/PARTIAL_FILLED(1) 可撤；FILLED/CANCELLED/REJECTED 幂等返回「已终态」。
 * 状态更新用乐观锁 + WHERE status IN(0,1) 并发防护，防止与成交/重复撤单竞态。
 * 解冻金额 = 剩余冻结（买单 freezeQuoteAmount−filledQuoteAmount；卖单 freezeBaseAmount−filledAmount），
 * 幂等 requestId = orderNo:C。
 * </p>
 */
@Slf4j
@Service
public class CancelService {

    private final OrderMapper orderMapper;
    private final MatchingEngine matchingEngine;
    private final AssetClient assetClient;

    public CancelService(OrderMapper orderMapper, MatchingEngine matchingEngine, AssetClient assetClient) {
        this.orderMapper = orderMapper;
        this.matchingEngine = matchingEngine;
        this.assetClient = assetClient;
    }

    /**
     * 撤单（返回撤单后的订单视图）。
     */
    @Transactional(rollbackFor = Exception.class)
    public OrderVO cancel(CancelOrderRequest req) {
        Order order = orderMapper.selectOne(new LambdaQueryWrapper<Order>()
                .eq(Order::getOrderNo, req.getOrderNo())
                .eq(Order::getUserId, req.getUserId())
                .last("limit 1"), false);
        if (order == null) {
            throw new ServiceException("订单不存在: " + req.getOrderNo());
        }
        int st = order.getStatus();
        if (st == OrderConstant.STATUS_FILLED || st == OrderConstant.STATUS_CANCELLED
                || st == OrderConstant.STATUS_REJECTED) {
            // 已终态：幂等返回
            throw new ServiceException("订单已终态(status=" + st + ")，不可撤单");
        }

        // 1) 从内存订单簿移除（同一交易对锁内，与撮合串行）
        matchingEngine.removeBookOrder(order);

        // 2) 状态机流转：乐观锁 + WHERE status IN(0,1) 并发防护
        LambdaUpdateWrapper<Order> uw = new LambdaUpdateWrapper<>();
        uw.eq(Order::getId, order.getId())
                .in(Order::getStatus, OrderConstant.STATUS_NEW, OrderConstant.STATUS_PARTIAL)
                .set(Order::getStatus, OrderConstant.STATUS_CANCELLED)
                .set(Order::getCancelTime, LocalDateTime.now());
        // 条件单（未触发，trigger_type>0 且 trigger_status=0）撤单 → 同步置 trigger_status=2(已取消)
        boolean isPendingConditional = order.getTriggerType() != null && order.getTriggerType() > 0
                && order.getTriggerStatus() != null && order.getTriggerStatus() == OrderConstant.TRIGGER_STATUS_PENDING;
        if (isPendingConditional) {
            uw.set(Order::getTriggerStatus, OrderConstant.TRIGGER_STATUS_CANCELLED);
        }
        boolean updated = orderMapper.update(null, uw) > 0;
        if (!updated) {
            // 并发下状态已变化（可能刚成交/已撤），回滚本次撤单
            throw new ServiceException("订单状态已变化，撤单失败，请刷新重试");
        }
        order.setStatus(OrderConstant.STATUS_CANCELLED);
        order.setCancelTime(LocalDateTime.now());
        if (isPendingConditional) {
            order.setTriggerStatus(OrderConstant.TRIGGER_STATUS_CANCELLED);
        }

        // 3) 解冻剩余冻结（成交部分已过户，无需再解冻）
        UnfreezeRequest unfreeze = new UnfreezeRequest();
        unfreeze.setRequestId(order.getOrderNo() + ":C");
        unfreeze.setUserId(order.getUserId());
        unfreeze.setBizType(OrderConstant.BIZ_UNFREEZE);
        unfreeze.setRefNo(order.getOrderNo());
        long amount;
        if (order.getSide() == OrderConstant.SIDE_BUY) {
            unfreeze.setSymbol(order.getQuoteCoin());
            amount = order.getFreezeQuoteAmount() - order.getFilledQuoteAmount();
        } else {
            unfreeze.setSymbol(order.getBaseCoin());
            amount = order.getFreezeBaseAmount() - order.getFilledAmount();
        }
        unfreeze.setAmount(Math.max(0L, amount));

        if (unfreeze.getAmount() > 0) {
            try {
                Result<?> r = assetClient.unfreeze(unfreeze);
                if (r == null || !r.isSuccess()) {
                    log.warn("[order] 撤单解冻未成功 orderNo={} code={} msg={}",
                            order.getOrderNo(), r == null ? "null" : r.getCode(), r == null ? "null" : r.getMessage());
                }
            } catch (Exception e) {
                // 解冻失败不阻断撤单结果；由补偿/对账处理（幂等 requestId=orderNo:C）
                log.error("[order] 撤单解冻异常 orderNo={}: {}", order.getOrderNo(), e.getMessage());
            }
        }
        log.info("[order] 撤单成功 orderNo={} 解冻{}={}", order.getOrderNo(), unfreeze.getSymbol(), unfreeze.getAmount());
        return toVO(order);
    }

    private OrderVO toVO(Order o) {
        OrderVO v = new OrderVO();
        v.setId(o.getId());
        v.setOrderNo(o.getOrderNo());
        v.setClientOid(o.getClientOid());
        v.setUserId(o.getUserId());
        v.setSymbol(o.getSymbol());
        v.setBaseCoin(o.getBaseCoin());
        v.setQuoteCoin(o.getQuoteCoin());
        v.setSide(o.getSide());
        v.setOrderType(o.getOrderType());
        v.setPrice(o.getPrice());
        v.setQuantity(o.getQuantity());
        v.setQuoteAmount(o.getQuoteAmount());
        v.setRemaining(o.getRemaining());
        v.setFilledAmount(o.getFilledAmount());
        v.setFilledQuoteAmount(o.getFilledQuoteAmount());
        v.setAvgPrice(o.getAvgPrice());
        v.setTradeCount(o.getTradeCount());
        v.setFee(o.getFee());
        v.setFreezeQuoteAmount(o.getFreezeQuoteAmount());
        v.setFreezeBaseAmount(o.getFreezeBaseAmount());
        v.setStatus(o.getStatus());
        v.setRemark(o.getRemark());
        v.setCreateTime(o.getCreateTime());
        v.setCancelTime(o.getCancelTime());
        v.setFilledTime(o.getFilledTime());
        return v;
    }
}
