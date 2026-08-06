package com.web3.exchange.market.mq.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.web3.exchange.common.order.dto.TradeSettleDTO;
import com.web3.exchange.market.market.MarketAggregator;
import com.web3.exchange.market.mq.Topics;
import org.apache.rocketmq.common.message.MessageExt;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

/**
 * ORDER-TRADE 行情消费者——订阅 order 的成交事件，聚合 K线/ticker。
 * <p>
 * 使用独立消费组 {@code market-order-trade-group}，与 asset/notify 各自消费互不影响
 * （RocketMQ 同主题不同消费组分别消费，见 docs/mq-topics.md）。
 * </p>
 * <p>
 * 消息体 = {@link TradeSettleDTO}（KEYS = tradeNo）。行情为可重放聚合，重复投递/重投
 * 由 {@link MarketAggregator} 按 tradeNo 内存去重收敛，不重复累计 volume。
 * </p>
 */
@Component
@RocketMQMessageListener(
        topic = Topics.ORDER_TRADE,
        consumerGroup = Topics.MARKET_ORDER_TRADE_GROUP,
        selectorExpression = "*"   // 订阅全部 Tag
)
public class OrderTradeMarketConsumer implements RocketMQListener<MessageExt> {

    private static final Logger log = LoggerFactory.getLogger(OrderTradeMarketConsumer.class);

    private final ObjectMapper objectMapper;
    private final MarketAggregator aggregator;

    public OrderTradeMarketConsumer(ObjectMapper objectMapper, MarketAggregator aggregator) {
        this.objectMapper = objectMapper;
        this.aggregator = aggregator;
    }

    @Override
    public void onMessage(MessageExt msg) {
        String bizKey = msg.getKeys() != null ? msg.getKeys() : msg.getMsgId();
        // 解析结算指令
        TradeSettleDTO dto;
        try {
            String body = new String(msg.getBody(), StandardCharsets.UTF_8);
            dto = objectMapper.readValue(body, TradeSettleDTO.class);
        } catch (Exception e) {
            log.error("消费 ORDER-TRADE 消息体解析失败。bizKey={}, err={}", bizKey, e.getMessage(), e);
            throw new IllegalArgumentException("ORDER-TRADE 消息体解析失败: " + bizKey, e); // 触发重投
        }
        if (dto == null || dto.getTradeNo() == null || dto.getSymbol() == null) {
            log.error("ORDER-TRADE 消息体不完整，触发重投。bizKey={}", bizKey);
            throw new IllegalArgumentException("ORDER-TRADE 消息体不完整: " + bizKey);
        }
        // 聚合 K线 + ticker（内部按 tradeNo 幂等去重）
        aggregator.onTrade(dto);
        log.info("[market] 消费 ORDER-TRADE 聚合行情成功。tradeNo={} symbol={} price={} qty={} quote={}",
                dto.getTradeNo(), dto.getSymbol(), dto.getPrice(), dto.getQuantity(), dto.getQuoteAmount());
    }
}
