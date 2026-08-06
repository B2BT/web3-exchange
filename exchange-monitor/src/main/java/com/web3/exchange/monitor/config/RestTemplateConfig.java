package com.web3.exchange.monitor.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.ResponseErrorHandler;
import org.springframework.web.client.RestTemplate;

/**
 * 监控域 HTTP 客户端配置。
 * <p>
 * 使用 {@link RestTemplate} 直接按实例 IP:端口探测各服务（不依赖目标服务是否暴露 actuator）。
 * 配置 NoOp 错误处理器：目标服务 4xx/5xx（如未暴露 actuator 返回 500）也视为"可达"，
 * 探测仅以能否建立连接并收到响应判定，配置读/连接超时保证定时探测不长期阻塞。
 * </p>
 */
@Configuration
public class RestTemplateConfig {

    @Bean
    public RestTemplate monitorRestTemplate(org.springframework.core.env.Environment env) {
        int timeout = env.getProperty("monitor.probe-timeout-ms", Integer.class, 3000);
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(timeout);
        factory.setReadTimeout(timeout);
        RestTemplate restTemplate = new RestTemplate(factory);
        // 任何 HTTP 状态码（含 4xx/5xx）都视为收到响应，不抛异常
        restTemplate.setErrorHandler(new ResponseErrorHandler() {
            @Override
            public boolean hasError(ClientHttpResponse response) {
                return false;
            }

            @Override
            public void handleError(ClientHttpResponse response) {
                // no-op
            }
        });
        return restTemplate;
    }
}
