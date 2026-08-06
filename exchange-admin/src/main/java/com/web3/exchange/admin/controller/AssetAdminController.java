package com.web3.exchange.admin.controller;

import com.web3.exchange.admin.service.AdminAssetService;
import com.web3.exchange.admin.vo.AssetSummaryVO;
import com.web3.exchange.common.model.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 后台资产汇总接口（/api/admin/asset/**，需 ADMIN 角色）。
 */
@RestController
@RequestMapping("/api/admin/asset")
@Tag(name = "后台资产汇总")
public class AssetAdminController {

    private final AdminAssetService adminAssetService;

    public AssetAdminController(AdminAssetService adminAssetService) {
        this.adminAssetService = adminAssetService;
    }

    @Operation(summary = "各币种全站总可用/冻结汇总")
    @GetMapping("/summary")
    public Result<List<AssetSummaryVO>> summary() {
        return Result.success(adminAssetService.summary());
    }
}
