package com.web3.exchange.auth;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Lazy;

/**
 * 权限框架启动类
 */
@SpringBootApplication
@EnableDiscoveryClient
@EnableFeignClients(
        basePackages = {
                "com.web3.exchange.auth.feign",
        }
)
public class AuthApplication {
    public static void main(String[] args) {
        SpringApplication.run(AuthApplication.class, args);
    }

    /**
     * 解决Feign和Spring Security循环依赖
     */
//    @Bean
//    @Lazy
//    public FeignContext feignContext() {
//        return new FeignContext();
//    }
}
