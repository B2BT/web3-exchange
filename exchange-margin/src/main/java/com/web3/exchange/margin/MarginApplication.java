package com.web3.exchange.margin;

import com.web3.exchange.common.handler.GlobalExceptionHandler;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.Import;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 杠杆现货模块启动类（exchange-margin，端口 8110）。
 * <p>
 * 提供杠杆账户（抵押/借入/利息）、借币/还币、日利率计息与强制平仓。
 * 通过 @Import(GlobalExceptionHandler.class) 引入公共异常处理；@EnableFeignClients 调用
 * exchange-asset 内部资金接口（抵押入金 freeze）；@MapperScan 扫描本模块 mapper；
 * @EnableScheduling 开启计息与强平定时任务。
 * </p>
 */
@SpringBootApplication
@Import(GlobalExceptionHandler.class)
@MapperScan("com.web3.exchange.margin.mapper")
@EnableFeignClients(basePackages = "com.web3.exchange.margin.feign")
@EnableScheduling
public class MarginApplication {
    public static void main(String[] args) {
        SpringApplication.run(MarginApplication.class, args);
    }
}
