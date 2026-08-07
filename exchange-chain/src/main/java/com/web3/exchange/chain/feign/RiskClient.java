package com.web3.exchange.chain.feign;

import com.web3.exchange.common.model.Result;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

/**
 * 风控域内部接口客户端（Feign，对接 exchange-risk 的 /internal/risk/**）。
 * <p>提现前置风控：反钓鱼码校验 + 二次验证码生成/校验。</p>
 */
@FeignClient(name = "exchange-risk", path = "/internal/risk")
public interface RiskClient {

    /** 提现前置(反钓鱼码+生成二次验证码) */
    @PostMapping("/withdraw/precheck")
    Result<WithdrawRiskResult> preCheckWithdraw(@RequestBody WithdrawRiskRequest req);

    /** 提现二次验证码校验 */
    @PostMapping("/withdraw/verify")
    Result<Boolean> verifyWithdraw(@RequestBody WithdrawVerifyRequest req);

    class WithdrawRiskRequest {
        public Long userId;
        public Long withdrawId;
        public String symbol;
        public Long amount;
        public String phrase;
    }

    class WithdrawRiskResult {
        public boolean pass;
        public boolean needVerify;
        public String verifyCode;
        public String reason;
    }

    class WithdrawVerifyRequest {
        public Long userId;
        public Long withdrawId;
        public String verifyCode;
    }
}
