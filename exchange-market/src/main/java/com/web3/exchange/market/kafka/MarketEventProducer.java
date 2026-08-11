package com.web3.exchange.market.kafka;

import com.web3.exchange.market.kafka.dto.MarketEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

/**
 * 行情 Kafka 生产者。
 * <p>Binance WS 收到真实行情后写入 Kafka topic，供多个下游消费者独立消费（K线聚合/深度/标记价/前端推送）。
 * 失败静默降级（不阻塞 WS 接收），保证行情主链路不受 Kafka 影响。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MarketEventProducer {

    private final KafkaTemplate<String, MarketEvent> kafkaTemplate;

    /** 发布一条行情事件到指定 topic，key=symbol（保证同币顺序）。失败仅告警不抛出。 */
    public void publish(String topic, MarketEvent event) {
        try {
            event.setEventTime(System.currentTimeMillis());
            kafkaTemplate.send(topic, event.getSymbol(), event);
        } catch (Exception e) {
            log.warn("[kafka-producer] 发布行情事件失败 topic={} type={}: {}", topic, event.getType(), e.getMessage());
        }
    }
}
