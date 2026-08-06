package com.web3.exchange.asset.mq.producer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.web3.exchange.asset.mq.Topics;
import com.web3.exchange.common.asset.dto.LedgerVO;
import org.apache.rocketmq.common.message.MessageConst;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.Message;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Component;

/**
 * 资产事件生产者——封装向 {@code ASSET-CHANGE} 主题发送资金变动事件。
 * <p>
 * 定位：<b>事件通知补充，非资金主链路</b>。当前以「Feign 同步 + 幂等」为资金核心
 * （见 {@code docs/asset-domain.md} §5.3），本组件仅负责在资金变动成功后对外广播
 * 一条事件，供 order/notify 订阅做余额刷新、对账、推送等旁路处理。
 * </p>
 * <p>
 * <b>发送失败降级</b>：所有发送异常均被捕获并仅记录日志，<b>绝不向上抛出</b>，
 * 从而不影响调用方（资金主流程）的正常返回。若后续需引入 RocketMQ 事务消息保证
 * 强一致，可在此组件基础上扩展（body 复用 {@link LedgerVO}，bizKey 用 requestId）。
 * </p>
 */
@Component
public class AssetEventProducer {

    private static final Logger log = LoggerFactory.getLogger(AssetEventProducer.class);

    private final RocketMQTemplate rocketMQTemplate;
    private final ObjectMapper objectMapper;

    public AssetEventProducer(RocketMQTemplate rocketMQTemplate, ObjectMapper objectMapper) {
        this.rocketMQTemplate = rocketMQTemplate;
        this.objectMapper = objectMapper;
    }

    /**
     * 发布一笔资金变动事件到 {@code ASSET-CHANGE}。
     * <p>
     * 以 {@link LedgerVO#getRequestId()} 作为消息 {@code KEYS}（业务键），
     * 供消费端幂等去重与按 key 查询。发送失败仅记录 warn 日志，不抛异常。
     * </p>
     *
     * @param ledger 资金变动后的流水视图（不可为 null）
     */
    public void publishAssetChange(LedgerVO ledger) {
        if (ledger == null) {
            return;
        }
        try {
            String json = objectMapper.writeValueAsString(ledger);
            Message<String> msg = MessageBuilder.withPayload(json)
                    .setHeader(MessageConst.PROPERTY_KEYS, ledger.getRequestId())
                    .build();
            rocketMQTemplate.syncSend(Topics.ASSET_CHANGE, msg);
            log.debug("已发送 ASSET-CHANGE 事件 requestId={}", ledger.getRequestId());
        } catch (Exception e) {
            // 发送失败降级：仅记录日志，不影响资金主流程（骨架阶段以 Feign 同步为准）
            log.warn("发送 ASSET-CHANGE 事件失败，已降级（不影响资金主流程）。requestId={}, err={}",
                    ledger.getRequestId(), e.getMessage());
        }
    }
}
