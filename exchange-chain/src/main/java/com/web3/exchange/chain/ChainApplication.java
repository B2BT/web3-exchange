package com.web3.exchange.chain;

import com.web3.exchange.common.handler.GlobalExceptionHandler;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.Import;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 链上域模块启动类（exchange-chain，端口 8105）。
 * <p>
 * 负责：充值区块扫描（web3j eth_getLogs/eth_getBlockByNumber → 命中充币地址落单 →
 * 确认数达标调 asset credit 入账）；提现（申请 → 审核冻结 → 离线签名 → 广播 → 回执确认扣减/失败回滚）。
 * 通过 @Import(GlobalExceptionHandler.class) 引入公共异常处理；@EnableFeignClients 开启对
 * exchange-asset 内部资金接口（credit/freeze/unfreeze/transfer）的调用；@MapperScan 扫描本模块 mapper；
 * @EnableScheduling 开启充值扫描与提现确认定时任务。
 * </p>
 */
@SpringBootApplication
@Import(GlobalExceptionHandler.class)
@MapperScan("com.web3.exchange.chain.mapper")
@EnableFeignClients(basePackages = "com.web3.exchange.chain.feign")
@EnableScheduling
public class ChainApplication {
    public static void main(String[] args) {
        SpringApplication.run(ChainApplication.class, args);
    }
}
