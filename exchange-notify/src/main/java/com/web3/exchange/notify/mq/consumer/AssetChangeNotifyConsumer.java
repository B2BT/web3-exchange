package com.web3.exchange.notify.mq.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.web3.exchange.common.asset.dto.LedgerVO;
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
 * ASSET-CHANGE 通知消费者——消费资产资金变动事件，为用户生成站内通知。
 * <p>
 * 映射（见 docs/notify-domain.md §4）：
 * <ul>
 *   <li>{@code bizType=DEPOSIT} → {@code DEPOSIT_CONFIRMED}（充值到账），biz_ref=refNo(depositId)</li>
 *   <li>{@code bizType=WITHDRAW} → {@code WITHDRAW_SUCCESS}（提现成功），biz_ref=refNo(withdrawId)</li>
 * </ul>
 * 本期只处理 {DEPOSIT, WITHDRAW}，其余高频中间流水（FREEZE/UNFREEZE/TRANSFER/FEE）不生成通知。
 * </p>
 * <p>
 * <b>幂等（双层）</b>：① 消费层 Redis SETNX（bizKey=requestId，TTL 24h），重复投递/重放直接 ACK 跳过；
 * ② 业务层 t_notification.uk_user_type_bizref(user_id,type,biz_ref) 唯一索引兜底——即便 SETNX 过期，
 * createWithIdempotent 撞唯一索引也会跳过，保证重复事件不重复通知。
 * </p>
 */
@Component
@RocketMQMessageListener(
        topic = Topics.ASSET_CHANGE,
        consumerGroup = "notify-asset-change-group",
        selectorExpression = "*"
)
public class AssetChangeNotifyConsumer implements RocketMQListener<MessageExt> {

    private static final Logger log = LoggerFactory.getLogger(AssetChangeNotifyConsumer.class);
    private static final String DEDUP_KEY_PREFIX = "mq:dedup:ASSET-CHANGE:notify:";
    private static final Duration DEDUP_TTL = Duration.ofHours(24);

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final NotificationService notificationService;

    public AssetChangeNotifyConsumer(StringRedisTemplate redisTemplate,
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

        // 2. 解析资金流水视图
        LedgerVO ledger;
        try {
            String body = new String(msg.getBody(), StandardCharsets.UTF_8);
            ledger = objectMapper.readValue(body, LedgerVO.class);
        } catch (Exception e) {
            log.error("消费 ASSET-CHANGE 消息体解析失败。bizKey={}, err={}", bizKey, e.getMessage(), e);
            throw new IllegalArgumentException("ASSET-CHANGE 消息体解析失败: " + bizKey, e); // 触发重投
        }
        if (ledger == null || ledger.getUserId() == null) {
            log.error("ASSET-CHANGE 消息体不完整，触发重投。bizKey={}", bizKey);
            throw new IllegalArgumentException("ASSET-CHANGE 消息体不完整: " + bizKey);
        }

        // 3. 仅处理充值/提现两类，映射并生成通知
        Notification notification = mapToNotification(ledger);
        if (notification == null) {
            log.debug("ASSET-CHANGE 事件非充值/提现，不生成通知。bizKey={}, bizType={}",
                    bizKey, ledger.getBizType());
            return;
        }

        boolean inserted = notificationService.createWithIdempotent(notification);
        log.info("消费 ASSET-CHANGE 生成通知。bizKey={}, userId={}, type={}, bizRef={}, inserted={}",
                bizKey, ledger.getUserId(), notification.getType(), notification.getBizRef(), inserted);
    }

    /**
     * 事件 → 通知类型映射。仅 bizType∈{DEPOSIT, WITHDRAW} 返回通知，其余返回 null（不生成）。
     */
    private Notification mapToNotification(LedgerVO ledger) {
        String bizType = ledger.getBizType();
        String type;
        String title;
        String content;
        String bizRef;
        if ("DEPOSIT".equalsIgnoreCase(bizType)) {
            type = "DEPOSIT_CONFIRMED";
            title = "充值到账";
            content = String.format("您的 %s 充值已到账 %d，单号 %s",
                    nvl(ledger.getSymbol()), ledger.getAmount(), nvl(ledger.getRefNo()));
            bizRef = ledger.getRefNo();
        } else if ("WITHDRAW".equalsIgnoreCase(bizType)) {
            type = "WITHDRAW_SUCCESS";
            title = "提现成功";
            content = String.format("您的 %s 提现已处理成功 %d，单号 %s",
                    nvl(ledger.getSymbol()), ledger.getAmount(), nvl(ledger.getRefNo()));
            bizRef = ledger.getRefNo();
        } else {
            return null;
        }
        if (bizRef == null || bizRef.isBlank()) {
            log.warn("ASSET-CHANGE 事件缺少业务单号(refNo)，跳过。requestId={}", ledger.getRequestId());
            return null;
        }
        return new Notification()
                .setUserId(ledger.getUserId())
                .setType(type)
                .setTitle(title)
                .setContent(content)
                .setBizType(bizType)
                .setBizRef(bizRef)
                .setSymbol(ledger.getSymbol())
                .setAmount(ledger.getAmount())
                .setIsRead(0)
                .setChannel("INBOX");
    }

    private String nvl(String s) {
        return (s == null || s.isBlank()) ? "-" : s;
    }
}
