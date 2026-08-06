package com.web3.exchange.asset.controller;

import com.web3.exchange.asset.service.AccountService;
import com.web3.exchange.asset.service.AssetAddressService;
import com.web3.exchange.asset.service.LedgerService;
import com.web3.exchange.common.asset.dto.AccountVO;
import com.web3.exchange.common.asset.dto.LedgerVO;
import com.web3.exchange.common.model.PageData;
import com.web3.exchange.common.model.Result;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 资产<b>对外</b> REST 接口（/api/asset/**）——经网关鉴权后对外提供服务。
 * <p>
 * 当前为基础封装：查询用户资产总览、单币种余额、充值地址（地址生成为占位，Phase 2 接入）。
 * 资金操作（冻结/解冻/过户/入账）由内部接口 /internal/asset/** 承担，不在此对外暴露，
 * 以免绕过业务层校验直接改动资金。
 * </p>
 */
@RestController
@RequestMapping("/api/asset")
@Tag(name = "资产对外接口")
public class AssetApiController {

    private final AccountService accountService;
    private final AssetAddressService assetAddressService;
    private final LedgerService ledgerService;

    public AssetApiController(AccountService accountService, AssetAddressService assetAddressService,
                              LedgerService ledgerService) {
        this.accountService = accountService;
        this.assetAddressService = assetAddressService;
        this.ledgerService = ledgerService;
    }

    /**
     * 查询用户资产总览：返回该用户全部币种账户（可用/冻结/总余额）。
     */
    @Operation(summary = "查询用户资产总览")
    @GetMapping("/accounts")
    public Result<List<AccountVO>> accounts(@RequestParam("userId") Long userId) {
        return Result.success(accountService.listByUser(userId));
    }

    /**
     * 查询单币种余额；账户不存在返回 notFound。
     */
    @Operation(summary = "查询单币种余额")
    @GetMapping("/balance")
    public Result<AccountVO> balance(@RequestParam("userId") Long userId,
                                     @RequestParam("symbol") String symbol) {
        return Result.success(accountService.getBalance(userId, symbol));
    }

    /**
     * 获取充值地址（基础版：按用户+链+币种）。
     * 当前为占位实现，冷/热钱包地址派生将在 Phase 2 接入。
     */
    @Operation(summary = "获取充值地址（基础版：按用户+链+币种）")
    @GetMapping("/address")
    public Result<Object> address(@RequestParam("userId") Long userId,
                                  @RequestParam("chainCode") String chainCode) {
        // 基础版：预留地址查询占位，Phase 2 接热钱包冷钱包派生
        return Result.success(null, "地址生成能力将在 Phase 2 接入");
    }

    /**
     * 资金明细分页查询（按 userId），createTime 降序。page 默认1，size 默认20，上限100。
     */
    @Operation(summary = "资金明细分页查询")
    @GetMapping("/ledger/list")
    public Result<PageData<LedgerVO>> ledgerList(@RequestParam("userId") Long userId,
                                                 @RequestParam(value = "page", defaultValue = "1") int page,
                                                 @RequestParam(value = "size", defaultValue = "20") int size) {
        Page<LedgerVO> voPage = ledgerService.pageLedgersByUserId(userId, page, size);
        return Result.success(PageData.of(voPage));
    }
}
