package com.web3.exchange.order;

import com.web3.exchange.common.handler.GlobalExceptionHandler;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.Import;

/**
 * 订单模块启动类（exchange-order，端口 8104）。
 * <p>
 * 交易核心：下单校验→落库→asset 预冻结→内存撮合→成交落库/过户→状态机→撤单解冻。
 * 通过 @Import(GlobalExceptionHandler.class) 引入公共异常处理（统一 Result 结构）；
 * @EnableFeignClients 开启对 exchange-asset 内部资金接口（freeze/transfer/unfreeze）的调用；
 * @MapperScan 扫描本模块 mapper。
 * </p>
 */
@SpringBootApplication
@Import(GlobalExceptionHandler.class)
@MapperScan("com.web3.exchange.order.mapper")
@EnableFeignClients(basePackages = "com.web3.exchange.order.feign")
public class OrderApplication {
    public static void main(String[] args) {
        SpringApplication.run(OrderApplication.class, args);
    }
}
