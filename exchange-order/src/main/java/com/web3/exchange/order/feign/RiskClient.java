package com.web3.exchange.order.feign;

import com.web3.exchange.common.model.Result;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

/**
 * 风控域内部接口客户端（Feign，对接 exchange-risk 的 /internal/risk/**）。
 * <p>下单前置风控：滑点/限额拦截超限单。</p>
 */
@FeignClient(name = "exchange-risk", path = "/internal/risk")
public interface RiskClient {

    /** 下单风控前置校验 */
    @PostMapping("/order/precheck")
    Result<OrderRiskResult> preCheckOrder(@RequestBody OrderRiskRequest req);

    /** 下单风控请求 */
    class OrderRiskRequest {
        public Long userId;
        public String symbol;
        public Integer side;
        public Integer orderType;
        public Long price;
        public Long quantity;
        public Long quoteAmount;
        public Long bestAsk;
        public Long bestBid;
    }

    /** 下单风控结果 */
    class OrderRiskResult {
        public boolean pass;
        public String reason;
    }
}
