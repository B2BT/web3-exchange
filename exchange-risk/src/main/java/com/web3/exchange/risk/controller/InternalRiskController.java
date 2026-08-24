package com.web3.exchange.risk.controller;

import com.web3.exchange.common.model.Result;
import com.web3.exchange.risk.dto.LoginRecordRequest;
import com.web3.exchange.risk.dto.OrderRiskRequest;
import com.web3.exchange.risk.dto.OrderRiskResult;
import com.web3.exchange.risk.dto.WithdrawRiskRequest;
import com.web3.exchange.risk.dto.WithdrawRiskResult;
import com.web3.exchange.risk.dto.WithdrawVerifyRequest;
import com.web3.exchange.risk.entity.LoginLog;
import com.web3.exchange.risk.service.RiskService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 风控<b>内部</b>接口（/internal/risk/**）——仅供服务间 Feign 调用，网关不路由 /internal/**。
 */
@RestController
@RequestMapping("/internal/risk")
@Tag(name = "风控内部接口")
public class InternalRiskController {

    private final RiskService riskService;

    public InternalRiskController(RiskService riskService) {
        this.riskService = riskService;
    }

    @Operation(summary = "下单风控前置")
    @PostMapping("/order/precheck")
    public Result<OrderRiskResult> preCheckOrder(@Valid @RequestBody OrderRiskRequest req) {
        return Result.success(riskService.preCheckOrder(req));
    }

    @Operation(summary = "提现前置(反钓鱼码+二次验证)")
    @PostMapping("/withdraw/precheck")
    public Result<WithdrawRiskResult> preCheckWithdraw(@Valid @RequestBody WithdrawRiskRequest req) {
        return Result.success(riskService.preCheckWithdraw(req));
    }

    @Operation(summary = "提现二次验证码校验")
    @PostMapping("/withdraw/verify")
    public Result<Boolean> verifyWithdraw(@Valid @RequestBody WithdrawVerifyRequest req) {
        return Result.success(riskService.verifyWithdraw(req));
    }

    @Operation(summary = "记录登录日志")
    @PostMapping("/login/record")
    public Result<LoginLog> recordLogin(@Valid @RequestBody LoginRecordRequest req) {
        return Result.success(riskService.recordLogin(req));
    }

    @Operation(summary = "AML黑名单清单")
    @GetMapping("/aml/blacklist")
    public Result<java.util.List<com.web3.exchange.risk.entity.AmlBlacklist>> amlBlacklist() {
        return Result.success(((com.web3.exchange.risk.service.impl.RiskServiceImpl) riskService).listBlacklist());
    }
}
