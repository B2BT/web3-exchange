package com.web3.exchange.asset;

import com.web3.exchange.common.handler.GlobalExceptionHandler;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 资产模块启动类（exchange-asset，端口 8103）。
 * <p>
 * 资产服务提供钱包账户/余额/冻结/解冻/过户/充值入账/流水等资金能力。
 * 通过 @Import(GlobalExceptionHandler.class) 引入公共异常处理（否则扫不到 common 包的
 * @RestControllerAdvice，错误响应无法统一为 Result 结构）；@MapperScan 扫描本模块 mapper。
 * </p>
 */
@SpringBootApplication
@Import(GlobalExceptionHandler.class)
@MapperScan("com.web3.exchange.asset.mapper")
@EnableScheduling
public class AssetApplication {
    public static void main(String[] args) {
        SpringApplication.run(AssetApplication.class, args);
    }
}
