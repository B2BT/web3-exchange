package com.web3.exchange.auth.feign;

import com.web3.exchange.common.model.Result;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

/**
 * 风控域内部接口客户端（Feign，对接 exchange-risk 的 /internal/risk/**）。
 * <p>记录登录日志（供异常登录检测）。</p>
 */
@FeignClient(name = "exchange-risk", path = "/internal/risk")
public interface RiskClient {

    /** 记录登录日志 */
    @PostMapping("/login/record")
    Result<Object> recordLogin(@RequestBody LoginRecordRequest req);

    class LoginRecordRequest {
        public Long userId;
        public String username;
        public String ip;
        public String userAgent;
        public Integer result;
    }
}
