package com.web3.exchange.order.service;

import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.web3.exchange.common.asset.dto.FreezeRequest;
import com.web3.exchange.common.exception.ServiceException;
import com.web3.exchange.common.model.Result;
import com.web3.exchange.order.constant.OrderConstant;
import com.web3.exchange.order.dto.OrderVO;
import com.web3.exchange.order.dto.PlaceOrderRequest;
import com.web3.exchange.order.dto.TradeVO;
import com.web3.exchange.order.engine.MatchingEngine;
import com.web3.exchange.order.entity.Order;
import com.web3.exchange.order.entity.Symbol;
import com.web3.exchange.order.entity.Trade;
import com.web3.exchange.order.feign.AssetClient;
import com.web3.exchange.order.mapper.OrderMapper;
import com.web3.exchange.order.mapper.TradeMapper;
import com.web3.exchange.order.mq.TradeProducer;
import com.web3.exchange.order.util.QuoteCalculator.PrecisionContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.ArrayList;
import java.util.List;

/**
 * 订单主服务：<b>下单→落库(NEW)→预冻结→撮合→落成交→过户→状态机</b>（docs/order-domain.md §5.4）。
 * <p>
 * 资金铁律：本域<b>绝不直接改余额</b>，一律经 {@link AssetClient} 调 asset 内部接口。
 * 采用 TransactionTemplate 保证「落库订单 + 落成交 + 更新挂单」原子性；资产过户在事务提交后执行，
 * 过户失败仅置成交 settle_status=2（不影响成交事实），由补偿任务幂等重试。
 * 状态机用乐观锁（@Version）+ 交易对锁串行化做并发防护。
 * </p>
 */
@Slf4j
@Service
public class OrderService {

    private final SymbolService symbolService;
    private final OrderCoinService coinService;
    private final OrderMapper orderMapper;
    private final TradeMapper tradeMapper;
    private final MatchingEngine matchingEngine;
    private final TradeProducer tradeProducer;
    private final AssetClient assetClient;
    private final TransactionTemplate transactionTemplate;

    public OrderService(SymbolService symbolService,
                        OrderCoinService coinService,
                        OrderMapper orderMapper,
                        TradeMapper tradeMapper,
                        MatchingEngine matchingEngine,
                        TradeProducer tradeProducer,
                        AssetClient assetClient,
                        TransactionTemplate transactionTemplate) {
        this.symbolService = symbolService;
        this.coinService = coinService;
        this.orderMapper = orderMapper;
        this.tradeMapper = tradeMapper;
        this.matchingEngine = matchingEngine;
        this.tradeProducer = tradeProducer;
        this.assetClient = assetClient;
        this.transactionTemplate = transactionTemplate;
    }

    /** 下单结果（订单视图 + 本次产生的成交）。 */
    public static class PlaceOrderResult {
        public final OrderVO order;
        public final List<TradeVO> trades;

        PlaceOrderResult(OrderVO order, List<TradeVO> trades) {
            this.order = order;
            this.trades = trades;
        }
    }

    /**
     * 下单主流程：校验 → 落库(NEW) → asset 冻结 → 撮合 → 落成交/更新挂单 → 过户。
     */
    public PlaceOrderResult placeOrder(PlaceOrderRequest req) {
        Symbol sym = symbolService.requireActive(req.getSymbol());
        // 精度上下文：价格/数量精度取自 t_symbol，计价币 decimals 取自 t_coin（USDT=6）
        PrecisionContext ctx = new PrecisionContext(
                nz(sym.getPricePrecision()), nz(sym.getAmountPrecision()),
                coinService.requireDecimals(sym.getQuoteCoin()));
        validate(req, sym, ctx);
        Order order = buildOrder(req, sym, ctx);

        // 事务：落库 + 冻结 + 撮合 + 落成交/更新双方订单
        MatchingEngine.MatchResult match = transactionTemplate.execute(status -> {
            orderMapper.insert(order);
            // 预冻结：失败则订单 REJECTED（保留记录，不产生资金变动）
            if (!doFreeze(order)) {
                order.setStatus(OrderConstant.STATUS_REJECTED);
                order.setRemark("资金冻结失败");
                orderMapper.updateById(order);
                return null;
            }
            // 撮合（交易对锁串行化）；撮合与冻结共用同一精度上下文，保证名义额算法一致
            MatchingEngine.MatchResult mr = matchingEngine.match(order, ctx);
            // 落成交
            for (Trade t : mr.trades) {
                tradeMapper.insert(t);
            }
            // 更新被成交改动的挂单 DB 行
            for (Order maker : mr.changedMakers) {
                orderMapper.updateById(maker);
            }
            // 更新吃单 DB 行
            orderMapper.updateById(order);
            return mr;
        });

        // 冻结失败 → 已 REJECTED
        if (order.getStatus() == OrderConstant.STATUS_REJECTED) {
            log.info("[order] 下单{}被拒(冻结失败),状态 REJECTED", order.getOrderNo());
            return new PlaceOrderResult(toVO(order), List.of());
        }

        // 事务已提交：逐笔发 ORDER-TRADE 事务消息驱动 asset 过户（订单本地落库与消息可达原子一致）
        // 事务消息 executeLocalTransaction 按 tradeNo 查库（已提交→COMMIT）保证「本地成交先于消息被消费」。
        List<TradeVO> tradeVOs = new ArrayList<>();
        for (Trade t : match.trades) {
            boolean dispatched = false;
            try {
                org.apache.rocketmq.client.producer.TransactionSendResult r = tradeProducer.sendTradeSettle(t);
                dispatched = r != null
                        && r.getLocalTransactionState() == org.apache.rocketmq.client.producer.LocalTransactionState.COMMIT_MESSAGE;
                if (!dispatched) {
                    log.warn("[order] 成交{}事务消息未提交, 置 settle_status=2 待补偿", t.getTradeNo());
                }
            } catch (Exception e) {
                log.error("[order] 成交{}发送 ORDER-TRADE 事务消息异常, 置 settle_status=2 待补偿: {}",
                        t.getTradeNo(), e.getMessage(), e);
            }
            if (!dispatched) {
                // MQ 发送失败兜底：置 settle_status=2，由补偿任务直接 Feign 过户（requestId 幂等，不重复扣账）
                t.setSettleStatus(2);
                tradeMapper.updateById(t);
            }
            tradeVOs.add(toTradeVO(t));
        }
        log.info("[order] 下单{}完成 status={} remaining={} 成交{}笔",
                order.getOrderNo(), order.getStatus(), order.getRemaining(), tradeVOs.size());
        return new PlaceOrderResult(toVO(order), tradeVOs);
    }

    /**
     * 查询订单（对外视图）。
     */
    public OrderVO getOrder(Long userId, String orderNo) {
        return toVO(getEntity(userId, orderNo));
    }

    /** 查询订单实体（内部用）。 */
    private Order getEntity(Long userId, String orderNo) {
        Order o = orderMapper.selectOne(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<Order>()
                .eq(Order::getOrderNo, orderNo).eq(Order::getUserId, userId).last("limit 1"), false);
        if (o == null) {
            throw new ServiceException("订单不存在: " + orderNo);
        }
        return o;
    }

    /** 查询某订单的成交明细。 */
    public List<TradeVO> listTrades(Long userId, String orderNo) {
        getEntity(userId, orderNo);
        List<Trade> list = tradeMapper.selectList(new com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper<Trade>()
                .and(w -> w.eq(Trade::getTakerOrderNo, orderNo).or().eq(Trade::getMakerOrderNo, orderNo))
                .orderByAsc(Trade::getId));
        return list.stream().map(this::toTradeVO).toList();
    }

    // ---------- 下单校验 ----------

    private void validate(PlaceOrderRequest req, Symbol sym, PrecisionContext ctx) {
        if (req.getSide() == null || (req.getSide() != OrderConstant.SIDE_BUY && req.getSide() != OrderConstant.SIDE_SELL)) {
            throw new ServiceException("无效方向 side");
        }
        if (req.getOrderType() == null
                || (req.getOrderType() != OrderConstant.TYPE_LIMIT && req.getOrderType() != OrderConstant.TYPE_MARKET)) {
            throw new ServiceException("无效订单类型 orderType");
        }
        long priceTick = sym.getPriceTick() == null ? 1L : sym.getPriceTick();
        if (req.getOrderType() == OrderConstant.TYPE_LIMIT) {
            if (req.getPrice() == null || req.getPrice() <= 0) {
                throw new ServiceException("限价单价格必须>0");
            }
            if (req.getPrice() % priceTick != 0) {
                throw new ServiceException("价格须为 price_tick(" + priceTick + ") 的整数倍");
            }
            if (req.getQuantity() == null || req.getQuantity() <= 0) {
                throw new ServiceException("限价单数量必须>0");
            }
            if (req.getQuantity() < sym.getMinAmount()) {
                throw new ServiceException("下单数量小于最小下单量 " + sym.getMinAmount());
            }
            if (sym.getMaxAmount() != null && req.getQuantity() > sym.getMaxAmount()) {
                throw new ServiceException("下单数量超过单笔最大下单量 " + sym.getMaxAmount());
            }
            long notional = ctx.quoteAmount(req.getPrice(), req.getQuantity());
            if (sym.getMinNotional() != null && notional < sym.getMinNotional()) {
                throw new ServiceException("下单名义值小于最小名义值 " + sym.getMinNotional());
            }
        } else {
            // 市价单
            if (req.getSide() == OrderConstant.SIDE_BUY) {
                if (req.getQuoteAmount() == null || req.getQuoteAmount() <= 0) {
                    throw new ServiceException("市价买单必须提供 quoteAmount 预算");
                }
            } else {
                if (req.getQuantity() == null || req.getQuantity() <= 0) {
                    throw new ServiceException("市价卖单必须提供 quantity");
                }
            }
        }
    }

    /** 组装订单实体并计算冻结额（名义额用精度上下文精确换算，计价币最小单位）。 */
    private Order buildOrder(PlaceOrderRequest req, Symbol sym, PrecisionContext ctx) {
        Order o = new Order();
        o.setOrderNo(IdWorker.getIdStr());
        o.setClientOid(req.getClientOid());
        o.setUserId(req.getUserId());
        o.setSymbol(req.getSymbol());
        o.setBaseCoin(sym.getBaseCoin());
        o.setQuoteCoin(sym.getQuoteCoin());
        o.setSide(req.getSide());
        o.setOrderType(req.getOrderType());
        o.setPrice(req.getPrice() == null ? 0L : req.getPrice());
        o.setQuoteAmount(req.getQuoteAmount() == null ? 0L : req.getQuoteAmount());
        o.setFee(0L);
        o.setTradeCount(0);
        o.setFilledAmount(0L);
        o.setFilledQuoteAmount(0L);
        o.setAvgPrice(0L);
        o.setFreezeRequestId(o.getOrderNo());
        o.setStatus(OrderConstant.STATUS_NEW);

        if (req.getOrderType() == OrderConstant.TYPE_MARKET && req.getSide() == OrderConstant.SIDE_BUY) {
            // 市价买单：只冻结计价币预算，quantity=0（预算即计价币最小单位，无需换算）
            o.setQuantity(0L);
            o.setRemaining(0L);
            o.setFreezeQuoteAmount(req.getQuoteAmount());
            o.setFreezeBaseAmount(0L);
        } else {
            // 限价单 / 市价卖单：quantity 为基础币冻结量
            o.setQuantity(req.getQuantity());
            o.setRemaining(req.getQuantity());
            if (req.getSide() == OrderConstant.SIDE_BUY) {
                // 限价买单冻结额 = 精确换算的 price×quantity（计价币最小单位，溢出安全）
                o.setFreezeQuoteAmount(ctx.quoteAmount(req.getPrice(), req.getQuantity()));
                o.setFreezeBaseAmount(0L);
            } else {
                o.setFreezeQuoteAmount(0L);
                o.setFreezeBaseAmount(req.getQuantity());
            }
        }
        return o;
    }

    /** 下单预冻结：买单冻计价币、卖单冻基础币；失败返回 false。 */
    private boolean doFreeze(Order order) {
        FreezeRequest req = new FreezeRequest();
        req.setRequestId(order.getOrderNo());
        req.setUserId(order.getUserId());
        req.setBizType(OrderConstant.BIZ_FREEZE);
        req.setRefNo(order.getOrderNo());
        if (order.getSide() == OrderConstant.SIDE_BUY) {
            req.setSymbol(order.getQuoteCoin());
            req.setAmount(order.getFreezeQuoteAmount());
        } else {
            req.setSymbol(order.getBaseCoin());
            req.setAmount(order.getFreezeBaseAmount());
        }
        try {
            Result<?> r = assetClient.freeze(req);
            if (r != null && r.isSuccess()) {
                return true;
            }
            log.warn("[order] 冻结失败 orderNo={} code={} msg={}", order.getOrderNo(),
                    r == null ? "null" : r.getCode(), r == null ? "null" : r.getMessage());
            return false;
        } catch (Exception e) {
            log.error("[order] 冻结异常 orderNo={}: {}", order.getOrderNo(), e.getMessage());
            return false;
        }
    }

    // ---------- 视图转换 ----------

    /** null → 0（精度字段兜底）。 */
    private static int nz(Integer v) {
        return v == null ? 0 : v;
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

    private TradeVO toTradeVO(Trade t) {
        TradeVO v = new TradeVO();
        v.setId(t.getId());
        v.setTradeNo(t.getTradeNo());
        v.setSymbol(t.getSymbol());
        v.setPrice(t.getPrice());
        v.setQuantity(t.getQuantity());
        v.setQuoteAmount(t.getQuoteAmount());
        v.setTakerOrderNo(t.getTakerOrderNo());
        v.setMakerOrderNo(t.getMakerOrderNo());
        v.setTakerOrderId(t.getTakerOrderId());
        v.setMakerOrderId(t.getMakerOrderId());
        v.setTakerUserId(t.getTakerUserId());
        v.setMakerUserId(t.getMakerUserId());
        v.setTakerSide(t.getTakerSide());
        v.setBuyUserId(t.getBuyUserId());
        v.setSellUserId(t.getSellUserId());
        v.setSettleStatus(t.getSettleStatus());
        v.setTradeTime(t.getTradeTime());
        return v;
    }
}
