package com.web3.exchange.staking;

import com.web3.exchange.common.handler.GlobalExceptionHandler;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.Import;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Staking/Earn 模块启动类（exchange-staking，端口 8112）。
 * <p>
 * 提供质押产品、质押/赎回、年化收益定时结算。通过 @Import(GlobalExceptionHandler.class)
 * 引入公共异常处理；@EnableFeignClients 调用 exchange-asset（质押 freeze / 赎回 unfreeze / 收益 credit）；
 * @MapperScan 扫描本模块 mapper；@EnableScheduling 开启收益结算任务。
 * </p>
 */
@SpringBootApplication
@Import(GlobalExceptionHandler.class)
@MapperScan("com.web3.exchange.staking.mapper")
@EnableFeignClients(basePackages = "com.web3.exchange.staking.feign")
@EnableScheduling
public class StakingApplication {
    public static void main(String[] args) {
        SpringApplication.run(StakingApplication.class, args);
    }
}
