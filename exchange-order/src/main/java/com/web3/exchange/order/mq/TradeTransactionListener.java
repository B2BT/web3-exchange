package com.web3.exchange.order.mq;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.web3.exchange.common.order.dto.TradeSettleDTO;
import com.web3.exchange.order.entity.Trade;
import com.web3.exchange.order.mapper.TradeMapper;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.common.message.MessageConst;
import org.apache.rocketmq.spring.annotation.RocketMQTransactionListener;
import org.apache.rocketmq.spring.core.RocketMQLocalTransactionListener;
import org.apache.rocketmq.spring.core.RocketMQLocalTransactionState;
import org.springframework.messaging.Message;
import org.springframework.stereotype.Component;

/**
 * ORDER-TRADE 事务消息本地事务监听器——保证「本地成交落库」与「消息可达」原子一致。
 * <p>
 * 通过 {@code @RocketMQTransactionListener} 自动注册到 RocketMQTemplate 的
 * {@code TransactionMQProducer}（RocketMQTransactionConfiguration 于启动时注入）。
 * </p>
 * <p>
 * <b>executeLocalTransaction</b>：sendMessageInTransaction 发送时同步回调，按 tradeNo 查
 * t_trade：已提交（存在）→ COMMIT，否则 ROLLBACK。由于 {@link TradeProducer} 在本地事务提交
 * 后才发送，正常情况下恒为 COMMIT；若本地事务被回滚（成交未落库），消息将 ROLLBACK 不投递。
 * </p>
 * <p>
 * <b>checkLocalTransaction</b>：半消息超时回查，同上按 tradeNo 查库返回对应状态，兜底
 * 「DB 已提交但 broker 未收到 COMMIT」的极端场景，保证最终投递。
 * </p>
 */
@Slf4j
@Component
@RocketMQTransactionListener(rocketMQTemplateBeanName = "rocketMQTemplate")
public class TradeTransactionListener implements RocketMQLocalTransactionListener {

    private final TradeMapper tradeMapper;
    private final ObjectMapper objectMapper;

    public TradeTransactionListener(TradeMapper tradeMapper, ObjectMapper objectMapper) {
        this.tradeMapper = tradeMapper;
        this.objectMapper = objectMapper;
    }

    @Override
    public RocketMQLocalTransactionState executeLocalTransaction(Message msg, Object arg) {
        String tradeNo = arg != null ? arg.toString() : extractTradeNo(msg);
        boolean committed = exists(tradeNo);
        log.info("[order] 事务消息执行本地事务 tradeNo={} 成交已提交={} → {}",
                tradeNo, committed, committed ? "COMMIT" : "ROLLBACK");
        return committed ? RocketMQLocalTransactionState.COMMIT : RocketMQLocalTransactionState.ROLLBACK;
    }

    @Override
    public RocketMQLocalTransactionState checkLocalTransaction(Message msg) {
        String tradeNo = extractTradeNo(msg);
        boolean committed = exists(tradeNo);
        log.info("[order] 事务消息回查本地状态 tradeNo={} 成交已提交={} → {}",
                tradeNo, committed, committed ? "COMMIT" : "ROLLBACK");
        return committed ? RocketMQLocalTransactionState.COMMIT : RocketMQLocalTransactionState.ROLLBACK;
    }

    /** 按 tradeNo 查询成交是否已提交。 */
    private boolean exists(String tradeNo) {
        if (tradeNo == null || tradeNo.isBlank()) {
            return false;
        }
        return tradeMapper.exists(new LambdaQueryWrapper<Trade>().eq(Trade::getTradeNo, tradeNo));
    }

    /** 从事务消息（spring Message）中提取 tradeNo：优先 KEYS，其次解析消息体。 */
    private String extractTradeNo(Message<?> msg) {
        if (msg == null) {
            return null;
        }
        Object keys = msg.getHeaders().get(MessageConst.PROPERTY_KEYS);
        if (keys != null && !keys.toString().isBlank()) {
            return keys.toString();
        }
        try {
            Object payload = msg.getPayload();
            if (payload instanceof String s && !s.isBlank()) {
                return objectMapper.readValue(s, TradeSettleDTO.class).getTradeNo();
            }
        } catch (Exception ignored) {
            // 解析失败降级为 null → ROLLBACK
        }
        return null;
    }
}
