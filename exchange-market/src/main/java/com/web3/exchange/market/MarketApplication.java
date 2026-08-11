package com.web3.exchange.market;

import com.web3.exchange.common.handler.GlobalExceptionHandler;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 行情模块启动类（exchange-market，端口 8106）。
 * <p>
 * 只读行情服务：消费 {@code ORDER-TRADE} 成交事件，内存聚合 K线(OHLCV 1m-1d)/ticker，
 * 对外提供 REST 查询。不产生任何资金/订单写操作，无 DB 依赖（纯内存）。
 * </p>
 */
@SpringBootApplication
@EnableScheduling
@EnableKafka
@Import(GlobalExceptionHandler.class)
public class MarketApplication {
    public static void main(String[] args) {
        SpringApplication.run(MarketApplication.class, args);
    }
}
