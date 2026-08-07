package com.web3.exchange.admin.controller;

import com.web3.exchange.admin.dto.HealthReportRequest;
import com.web3.exchange.admin.service.AdminBService;
import com.web3.exchange.common.model.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Admin B 内部接口（/internal/admin/**）——服务健康上报（Feign）。
 */
@RestController
@RequestMapping("/internal/admin")
@Tag(name = "Admin B 内部接口")
public class AdminBInternalController {

    private final AdminBService adminBService;

    public AdminBInternalController(AdminBService adminBService) {
        this.adminBService = adminBService;
    }

    @Operation(summary = "服务健康上报")
    @PostMapping("/health/report")
    public Result<Void> reportHealth(@Valid @RequestBody HealthReportRequest req) {
        adminBService.reportHealth(req);
        return Result.success(null, "上报成功");
    }
}
