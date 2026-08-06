package com.web3.exchange.notify.mq.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.web3.exchange.common.order.dto.TradeSettleDTO;
import com.web3.exchange.notify.entity.Notification;
import com.web3.exchange.notify.mq.Topics;
import com.web3.exchange.notify.service.NotificationService;
import org.apache.rocketmq.common.message.MessageExt;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * ORDER-TRADE 通知消费者——消费撮合成交事件，为买卖双方各生成一条成交通知。
 * <p>
 * 映射（见 docs/notify-domain.md §4）：一笔成交给 {@code buyUserId} 与 {@code sellUserId}
 * 各生成一条 {@code TRADE_FILLED} 通知，biz_ref 分别取 {@code tradeNo:BUY} / {@code tradeNo:SELL}，
 * 保证同一用户同一笔成交与买卖双方各自幂等、各得一条。
 * </p>
 * <p>
 * <b>幂等（双层）</b>：① 消费层 Redis SETNX（bizKey=tradeNo，TTL 24h），重复投递直接 ACK 跳过；
 * ② 业务层 uk_user_type_bizref(user_id,type,biz_ref) 唯一索引兜底——即便 SETNX 过期，
 * createWithIdempotent 撞唯一索引也会跳过，重复事件不重复通知。
 * </p>
 */
@Component
@RocketMQMessageListener(
        topic = Topics.ORDER_TRADE,
        consumerGroup = "notify-order-trade-group",
        selectorExpression = "*"
)
public class OrderTradeNotifyConsumer implements RocketMQListener<MessageExt> {

    private static final Logger log = LoggerFactory.getLogger(OrderTradeNotifyConsumer.class);
    private static final String DEDUP_KEY_PREFIX = "mq:dedup:ORDER-TRADE:notify:";
    private static final Duration DEDUP_TTL = Duration.ofHours(24);

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final NotificationService notificationService;

    public OrderTradeNotifyConsumer(StringRedisTemplate redisTemplate,
                                    ObjectMapper objectMapper,
                                    NotificationService notificationService) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        this.notificationService = notificationService;
    }

    @Override
    public void onMessage(MessageExt msg) {
        String bizKey = msg.getKeys() != null ? msg.getKeys() : msg.getMsgId();

        // 1. 消费层幂等去重：SETNX，重复则跳过（已处理过）
        Boolean first = redisTemplate.opsForValue()
                .setIfAbsent(DEDUP_KEY_PREFIX + bizKey, "1", DEDUP_TTL);
        if (!Boolean.TRUE.equals(first)) {
            log.info("重复消息，幂等跳过。topic={}, bizKey={}", msg.getTopic(), bizKey);
            return;
        }

        // 2. 解析成交结算指令
        TradeSettleDTO dto;
        try {
            String body = new String(msg.getBody(), StandardCharsets.UTF_8);
            dto = objectMapper.readValue(body, TradeSettleDTO.class);
        } catch (Exception e) {
            log.error("消费 ORDER-TRADE 消息体解析失败。bizKey={}, err={}", bizKey, e.getMessage(), e);
            throw new IllegalArgumentException("ORDER-TRADE 消息体解析失败: " + bizKey, e); // 触发重投
        }
        if (dto == null || dto.getTradeNo() == null || dto.getBuyUserId() == null || dto.getSellUserId() == null) {
            log.error("ORDER-TRADE 消息体不完整，触发重投。bizKey={}", bizKey);
            throw new IllegalArgumentException("ORDER-TRADE 消息体不完整: " + bizKey);
        }

        // 3. 买卖双方各生成一条 TRADE_FILLED 通知
        boolean buyerInserted = notificationService.createWithIdempotent(buildNotification(dto, true));
        boolean sellerInserted = notificationService.createWithIdempotent(buildNotification(dto, false));
        log.info("消费 ORDER-TRADE 生成通知。tradeNo={}, symbol={}, buy={}, sell={}, buyerInserted={}, sellerInserted={}",
                dto.getTradeNo(), dto.getSymbol(), dto.getBuyUserId(), dto.getSellUserId(),
                buyerInserted, sellerInserted);
    }

    /**
     * 构造一条成交通知。{@code buyer=true} 生成买方通知（biz_ref=tradeNo:BUY）；{@code buyer=false} 生成卖方通知（biz_ref=tradeNo:SELL）。
     */
    private Notification buildNotification(TradeSettleDTO dto, boolean buyer) {
        String symbol = nvl(dto.getSymbol());
        Long userId = buyer ? dto.getBuyUserId() : dto.getSellUserId();
        String action = buyer ? "买入" : "卖出";
        String bizRef = dto.getTradeNo() + (buyer ? ":BUY" : ":SELL");
        String content = String.format("您%s %s 已成交 %d，成交价 %d，金额 %d",
                action, symbol, dto.getQuantity(), dto.getPrice(), dto.getQuoteAmount());
        return new Notification()
                .setUserId(userId)
                .setType("TRADE_FILLED")
                .setTitle("订单成交")
                .setContent(content)
                .setBizType("TRADE")
                .setBizRef(bizRef)
                .setSymbol(symbol)
                .setAmount(dto.getQuoteAmount())
                .setIsRead(0)
                .setChannel("INBOX");
    }

    private String nvl(String s) {
        return (s == null || s.isBlank()) ? "-" : s;
    }
}
