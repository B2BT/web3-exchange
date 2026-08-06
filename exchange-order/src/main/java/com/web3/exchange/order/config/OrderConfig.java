package com.web3.exchange.order.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.web.client.RestTemplate;

/**
 * 订单域基础设施配置。
 * <p>
 * {@link RestTemplate} 用于条件单触发任务从 market 服务拉取最新行情价（/api/market/ticker/list）；
 * {@link EnableScheduling} 开启 @Scheduled 定时任务（条件单行情触发）。
 * </p>
 */
@Configuration
@EnableScheduling
public class OrderConfig {

    @Bean
    public RestTemplate orderRestTemplate() {
        return new RestTemplate();
    }
}
