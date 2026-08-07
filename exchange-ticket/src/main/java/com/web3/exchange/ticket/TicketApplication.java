package com.web3.exchange.ticket;

import com.web3.exchange.common.handler.GlobalExceptionHandler;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;

/**
 * 客服工单服务启动类。
 */
@SpringBootApplication
@Import(GlobalExceptionHandler.class)
public class TicketApplication {
    public static void main(String[] args) {
        SpringApplication.run(TicketApplication.class, args);
    }
}
