package com.web3.exchange.asset.controller;

import com.web3.exchange.asset.service.AccountService;
import com.web3.exchange.asset.service.AssetAddressService;
import com.web3.exchange.common.asset.dto.AccountVO;
import com.web3.exchange.common.model.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 资产对外 REST 接口（经网关鉴权，/api/asset/**）。基础封装，内部逻辑复用内部接口能力。
 */
@RestController
@RequestMapping("/api/asset")
@Tag(name = "资产对外接口")
public class AssetApiController {

    private final AccountService accountService;
    private final AssetAddressService assetAddressService;

    public AssetApiController(AccountService accountService, AssetAddressService assetAddressService) {
        this.accountService = accountService;
        this.assetAddressService = assetAddressService;
    }

    @Operation(summary = "查询用户资产总览")
    @GetMapping("/accounts")
    public Result<List<AccountVO>> accounts(@RequestParam("userId") Long userId) {
        return Result.success(accountService.listByUser(userId));
    }

    @Operation(summary = "查询单币种余额")
    @GetMapping("/balance")
    public Result<AccountVO> balance(@RequestParam("userId") Long userId,
                                     @RequestParam("symbol") String symbol) {
        return Result.success(accountService.getBalance(userId, symbol));
    }

    @Operation(summary = "获取充值地址（基础版：按用户+链+币种）")
    @GetMapping("/address")
    public Result<Object> address(@RequestParam("userId") Long userId,
                                  @RequestParam("chainCode") String chainCode) {
        // 基础版：预留地址查询占位，Phase 2 接热钱包冷钱包派生
        return Result.success(null, "地址生成能力将在 Phase 2 接入");
    }
}
