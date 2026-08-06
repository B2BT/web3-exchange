package com.web3.exchange.notify;

import com.web3.exchange.common.handler.GlobalExceptionHandler;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;

/**
 * 通知模块启动类（exchange-notify，端口 8107）。
 * <p>
 * 站内通知（inbox）服务：消费资金/交易事件（ASSET-CHANGE / ORDER-TRADE）为对应用户生成
 * 可查询、可标记已读的通知记录；提供查询/未读数/已读接口。
 * 通过 @Import(GlobalExceptionHandler.class) 引入公共异常处理，@MapperScan 扫描本模块 mapper。
 * </p>
 */
@SpringBootApplication
@Import(GlobalExceptionHandler.class)
@MapperScan("com.web3.exchange.notify.mapper")
public class NotifyApplication {
    public static void main(String[] args) {
        SpringApplication.run(NotifyApplication.class, args);
    }
}
