package com.web3.exchange.asset;

import com.web3.exchange.common.handler.GlobalExceptionHandler;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;

/**
 * 资产模块启动类
 */
@SpringBootApplication
@Import(GlobalExceptionHandler.class)
@MapperScan("com.web3.exchange.asset.mapper")
public class AssetApplication {
    public static void main(String[] args) {
        SpringApplication.run(AssetApplication.class, args);
    }
}
