package com.web3.exchange.gateway;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

/**
 * 网关服务启动类
 *
 * 说明：
 * 1. 网关基于 Spring Cloud Gateway（WebFlux 响应式栈），不含 Servlet 依赖，也不连接数据库。
 * 2. 由于 exchange-common 传递引入的 mybatis-plus / mysql 驱动会触发数据源自动配置，
 *    必须排除 DataSourceAutoConfiguration，避免网关启动时尝试初始化数据源而失败。
 * 3. 网关仅加载 com.web3.exchange.gateway 包下的组件（如 AuthFilter）。
 */
@SpringBootApplication(
        scanBasePackages = "com.web3.exchange.gateway",
        exclude = DataSourceAutoConfiguration.class
)
@EnableDiscoveryClient
public class GatewayApplication {

    public static void main(String[] args) {
        SpringApplication.run(GatewayApplication.class, args);
    }
}
