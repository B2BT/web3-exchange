package com.web3.exchange.asset.mq.consumer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.web3.exchange.asset.mq.Topics;
import com.web3.exchange.common.asset.dto.LedgerVO;
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
 * 幂等消费骨架——监听 {@code ASSET-CHANGE} 主题，演示「Redis SETNX 去重 + 业务处理」的标准消费姿势。
 * <p>
 * <b>定位</b>：这是给后续 order / notify 接入 MQ 消费的<b>参考骨架</b>（RocketMQ 至少一次投递，
 * 消费端必须自行去重）。asset 正常业务上通常不去消费自己发的事件，本类仅用于验证骨架可编译、可监听；
 * 真实消费逻辑（余额刷新/对账/推送）由订阅方各自实现。骨架先写死监听以便验证。
 * </p>
 * <p>
 * <b>幂等设计</b>：以消息 {@code KEYS}（生产者写入的 requestId）为业务键，先 Redis {@code SETNX}，
 * 若已存在（重复投递/重放）则直接返回 ACK 跳过，避免重复处理；TTL 24h。若消费端需<b>写库</b>，
 * 还应叠加业务表唯一索引兜底（见 {@code docs/mq-topics.md} §三）。
 * </p>
 * <p><b>后续 order/notify 接入方式</b>：新建一个监听类，@RocketMQMessageListener 分别指向
 * {@code ORDER-TRADE} / {@code ASSET-CHANGE}，consumerGroup 用 {@code order-order-trade-group} /
 * {@code notify-asset-change-group}，内部同样做 SETNX 去重。</p>
 */
@Component
@RocketMQMessageListener(
        topic = Topics.ASSET_CHANGE,
        consumerGroup = "asset-asset-change-demo-group",
        selectorExpression = "*"   // 订阅全部 Tag（FREEZE/UNFREEZE/TRANSFER/DEPOSIT/...）
)
public class AssetEventConsumer implements RocketMQListener<MessageExt> {

    private static final Logger log = LoggerFactory.getLogger(AssetEventConsumer.class);
    private static final String DEDUP_KEY_PREFIX = "mq:dedup:ASSET-CHANGE:";
    private static final Duration DEDUP_TTL = Duration.ofHours(24);

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    public AssetEventConsumer(StringRedisTemplate redisTemplate, ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public void onMessage(MessageExt msg) {
        String bizKey = msg.getKeys();   // 生产者写入的 requestId（业务键）
        if (bizKey == null || bizKey.isBlank()) {
            // 无业务键：退化为按 msgId 去重，避免漏处理
            bizKey = msg.getMsgId();
        }

        // 1. 幂等去重：SETNX，重复则直接跳过（已消费过）
        Boolean first = redisTemplate.opsForValue()
                .setIfAbsent(DEDUP_KEY_PREFIX + bizKey, "1", DEDUP_TTL);
        if (!Boolean.TRUE.equals(first)) {
            log.info("重复消息，幂等跳过。topic={}, bizKey={}", msg.getTopic(), bizKey);
            return;
        }

        // 2. 业务处理骨架：解析消息体 + 打印日志（真实逻辑由订阅方补齐）
        try {
            String body = new String(msg.getBody(), StandardCharsets.UTF_8);
            LedgerVO ledger = objectMapper.readValue(body, LedgerVO.class);
            log.info("消费 ASSET-CHANGE 事件成功。bizKey={}, userId={}, symbol={}, bizType={}, "
                            + "direction={}, amount={}, refNo={}",
                    bizKey, ledger.getUserId(), ledger.getSymbol(), ledger.getBizType(),
                    ledger.getDirection(), ledger.getAmount(), ledger.getRefNo());
            // TODO 骨架：order 在此做余额刷新/对账；notify 在此做资金变动推送（按需解耦）
        } catch (Exception e) {
            // 解析/处理失败：记录告警。若要触发重投可向上抛出（默认 16 次后进死信）。
            log.error("消费 ASSET-CHANGE 事件失败。bizKey={}, err={}", bizKey, e.getMessage(), e);
        }
    }
}
