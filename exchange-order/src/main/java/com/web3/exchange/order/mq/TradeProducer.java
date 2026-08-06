package com.web3.exchange.order.mq;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.web3.exchange.common.order.dto.TradeSettleDTO;
import com.web3.exchange.order.entity.Trade;
import lombok.extern.slf4j.Slf4j;
import org.apache.rocketmq.client.producer.TransactionSendResult;
import org.apache.rocketmq.common.message.MessageConst;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Component;

/**
 * ORDER-TRADE 事务消息生产者——成交结算指令经 <b>RocketMQ 事务消息</b>发送。
 * <p>
 * 目的：实现「order 本地写库（t_order/t_trade）+ 发 ORDER-TRADE 消息」的<b>原子一致</b>。
 * 采用 {@code sendMessageInTransaction}，配合 {@link TradeTransactionListener}：本地成交
 * 提交后才 COMMIT 发送，本地未落库则 ROLLBACK——消息被消费者（asset 过户）看到时，
 * 本地成交一定已持久化，从而驱动「下单→撮合→成交→事务消息→过户」的最终一致性闭环。
 * </p>
 * <p>
 * 消息体 = {@link TradeSettleDTO}（结算指令，含 tradeNo/买卖双方/price/quantity/quoteAmount/
 * symbol 等），{@code KEYS = tradeNo} 供 asset 消费端幂等去重；过户幂等号 = tradeNo:Q / tradeNo:B。
 * </p>
 *
 * @see TradeTransactionListener
 */
@Slf4j
@Component
public class TradeProducer {

    /** ORDER-TRADE 主题（见 docs/mq-topics.md） */
    public static final String TOPIC_ORDER_TRADE = "ORDER-TRADE";

    private final RocketMQTemplate rocketMQTemplate;
    private final ObjectMapper objectMapper;

    public TradeProducer(RocketMQTemplate rocketMQTemplate, ObjectMapper objectMapper) {
        this.rocketMQTemplate = rocketMQTemplate;
        this.objectMapper = objectMapper;
    }

    /**
     * 发送一笔成交的 ORDER-TRADE 事务消息。
     * <p>必须在本地 t_trade 提交<b>之后</b>调用：本方法内部 sendMessageInTransaction 会
     * 触发 {@link TradeTransactionListener#executeLocalTransaction}，其按 tradeNo 查库，
     * 成交已提交则 COMMIT、未提交则 ROLLBACK，从而保证「本地落库与消息可达」原子一致。</p>
     *
     * @param trade 已落库（已提交）的成交
     * @return 事务发送结果（含最终本地事务状态）
     */
    public TransactionSendResult sendTradeSettle(Trade trade) {
        TradeSettleDTO dto = toDTO(trade);
        String json;
        try {
            json = objectMapper.writeValueAsString(dto);
        } catch (Exception e) {
            throw new IllegalStateException("序列化 ORDER-TRADE 消息体失败: " + trade.getTradeNo(), e);
        }
        Message<String> msg = MessageBuilder.withPayload(json)
                .setHeader(MessageConst.PROPERTY_KEYS, trade.getTradeNo())
                .build();
        TransactionSendResult result = rocketMQTemplate.sendMessageInTransaction(TOPIC_ORDER_TRADE, msg, trade.getTradeNo());
        log.info("[order] 已发送 ORDER-TRADE 事务消息 tradeNo={} state={} msgId={}",
                trade.getTradeNo(), result.getLocalTransactionState(), result.getMsgId());
        return result;
    }

    /** 成交 → 结算指令（消息体）。 */
    private TradeSettleDTO toDTO(Trade t) {
        TradeSettleDTO dto = new TradeSettleDTO();
        dto.setTradeNo(t.getTradeNo());
        dto.setSymbol(t.getSymbol());
        dto.setBaseCoin(t.getSymbol().split("/")[0]);
        dto.setQuoteCoin(t.getSymbol().split("/")[1]);
        dto.setPrice(t.getPrice());
        dto.setQuantity(t.getQuantity());
        dto.setQuoteAmount(t.getQuoteAmount());
        dto.setBuyUserId(t.getBuyUserId());
        dto.setSellUserId(t.getSellUserId());
        dto.setTakerOrderNo(t.getTakerOrderNo());
        dto.setMakerOrderNo(t.getMakerOrderNo());
        return dto;
    }
}
