package com.web3.exchange.market.kafka;

import com.web3.exchange.market.kafka.dto.MarketEvent;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.kafka.support.serializer.JsonSerializer;

import java.util.HashMap;
import java.util.Map;

/**
 * Kafka 配置（行情管道）。
 * <p>生产/消费 MarketEvent JSON；topic 自动创建（分区 4，副本 1，供多消费者并行消费）。
 * broker 地址默认 localhost:9092，可用 server-settings.kafka.bootstrap-servers 覆盖。</p>
 */
@Configuration
public class KafkaConfig {

    @Value("${server-settings.kafka.bootstrap-servers:localhost:9092}")
    private String bootstrapServers;

    /** 生产端配置 */
    private Map<String, Object> producerProps() {
        Map<String, Object> props = new HashMap<>();
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, JsonSerializer.class);
        props.put(ProducerConfig.ACKS_CONFIG, "1"); // 降低延迟，容忍少量丢失（行情场景可接受）
        props.put(ProducerConfig.LINGER_MS_CONFIG, 5);
        return props;
    }

    @Bean
    public ProducerFactory<String, MarketEvent> producerFactory() {
        return new DefaultKafkaProducerFactory<>(producerProps());
    }

    @Bean
    public KafkaTemplate<String, MarketEvent> kafkaTemplate() {
        return new KafkaTemplate<>(producerFactory());
    }

    /** 消费端配置（不同消费者组并行消费同一 topic） */
    private Map<String, Object> consumerProps(String group) {
        Map<String, Object> props = new HashMap<>();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        props.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, JsonDeserializer.class);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, group);
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "latest"); // 行情实时为主，启动只读新数据
        props.put(JsonDeserializer.TRUSTED_PACKAGES, "com.web3.exchange.market.kafka.dto");
        props.put(JsonDeserializer.VALUE_DEFAULT_TYPE, MarketEvent.class.getName());
        return props;
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, MarketEvent> marketKafkaListenerContainerFactory() {
        ConcurrentKafkaListenerContainerFactory<String, MarketEvent> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        ConsumerFactory<String, MarketEvent> cf = new DefaultKafkaConsumerFactory<>(consumerProps("market-consumer"));
        factory.setConsumerFactory(cf);
        return factory;
    }

    /** 行情 topic 定义（分区 4 → 支持多消费者并行；副本 1 单机够用） */
    @Bean
    public NewTopic marketTickerTopic() {
        return TopicBuilder.name(KafkaTopics.MARKET_TICKER).partitions(4).replicas(1).build();
    }

    @Bean
    public NewTopic marketKlineTopic() {
        return TopicBuilder.name(KafkaTopics.MARKET_KLINE).partitions(4).replicas(1).build();
    }

    @Bean
    public NewTopic marketDepthTopic() {
        return TopicBuilder.name(KafkaTopics.MARKET_DEPTH).partitions(4).replicas(1).build();
    }
}
