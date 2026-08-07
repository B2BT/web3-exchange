package com.web3.exchange.risk.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.web3.exchange.common.model.Result;
import com.web3.exchange.risk.dto.PhishingRequest;
import com.web3.exchange.risk.entity.AntiPhishing;
import com.web3.exchange.risk.entity.LoginLog;
import com.web3.exchange.risk.entity.RiskRule;
import com.web3.exchange.risk.service.RiskService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 风控<b>对外</b> REST 接口（/api/risk/**，经网关路由，需登录）。
 */
@RestController
@RequestMapping("/api/risk")
@Tag(name = "风控对外接口")
public class RiskController {

    private final RiskService riskService;

    public RiskController(RiskService riskService) {
        this.riskService = riskService;
    }

    @Operation(summary = "风控规则列表")
    @GetMapping("/rules")
    public Result<List<RiskRule>> rules() {
        return Result.success(riskService.listRules());
    }

    @Operation(summary = "设置反钓鱼码")
    @PostMapping("/phishing/set")
    public Result<AntiPhishing> setPhishing(@Valid @RequestBody PhishingRequest req) {
        return Result.success(riskService.setPhishing(req.getUserId(), req.getPhrase()));
    }

    @Operation(summary = "查询反钓鱼码")
    @GetMapping("/phishing")
    public Result<AntiPhishing> getPhishing(@RequestParam("userId") Long userId) {
        return Result.success(riskService.getPhishing(userId));
    }

    @Operation(summary = "我的登录日志分页")
    @GetMapping("/login-logs")
    public Result<Page<LoginLog>> loginLogs(@RequestParam("userId") Long userId,
                                            @RequestParam(value = "page", defaultValue = "1") int page,
                                            @RequestParam(value = "size", defaultValue = "20") int size) {
        return Result.success(riskService.pageLoginLogs(userId, page, size));
    }
}
