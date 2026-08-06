package com.web3.exchange.monitor;

import com.web3.exchange.common.handler.GlobalExceptionHandler;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.context.annotation.Import;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 监控模块启动类（exchange-monitor，端口 8108）。
 * <p>
 * 运维监控服务：通过 Nacos 服务发现定时拉取各业务服务实例，探测实例健康并采集简化指标，
 * 通过 /api/monitor/** 对外提供查询。开启 {@link EnableScheduling} 驱动定时任务，
 * {@link EnableDiscoveryClient} 注册自身并用于拉取注册中心服务列表，
 * {@code @Import(GlobalExceptionHandler.class)} 引入公共异常处理。
 * </p>
 */
@SpringBootApplication
@EnableScheduling
@EnableDiscoveryClient
@Import(GlobalExceptionHandler.class)
public class MonitorApplication {
    public static void main(String[] args) {
        SpringApplication.run(MonitorApplication.class, args);
    }
}
