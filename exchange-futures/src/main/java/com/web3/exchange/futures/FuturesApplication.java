package com.web3.exchange.futures;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 永续合约模块启动类。
 * <p>P3.5 合约/永续，端口 8117。包含合约撮合、保证金仓位、资金费率、强平引擎。</p>
 */
@SpringBootApplication
@EnableDiscoveryClient
@EnableFeignClients
@EnableScheduling
public class FuturesApplication {
    public static void main(String[] args) {
        SpringApplication.run(FuturesApplication.class, args);
    }
}
