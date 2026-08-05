package com.web3.exchange.asset.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.web3.exchange.asset.service.AccountService;
import com.web3.exchange.asset.service.LedgerService;
import com.web3.exchange.common.asset.dto.AccountVO;
import com.web3.exchange.common.asset.dto.CreditRequest;
import com.web3.exchange.common.asset.dto.FreezeRequest;
import com.web3.exchange.common.asset.dto.LedgerVO;
import com.web3.exchange.common.asset.dto.TransferRequest;
import com.web3.exchange.common.asset.dto.UnfreezeRequest;
import com.web3.exchange.common.model.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 资产内部接口（仅服务间 Feign 调用，网关不路由 /internal/**）。
 */
@RestController
@RequestMapping("/internal/asset")
@Tag(name = "资产内部接口")
public class InternalAssetController {

    private final AccountService accountService;
    private final LedgerService ledgerService;

    public InternalAssetController(AccountService accountService, LedgerService ledgerService) {
        this.accountService = accountService;
        this.ledgerService = ledgerService;
    }

    @PostMapping("/account/open")
    @Operation(summary = "幂等开户")
    public Result<AccountVO> open(@RequestParam("userId") Long userId,
                                  @RequestParam("symbol") String symbol) {
        return Result.success(accountService.open(userId, symbol));
    }

    @GetMapping("/account/balance")
    @Operation(summary = "查询单账户余额")
    public Result<AccountVO> balance(@RequestParam("userId") Long userId,
                                     @RequestParam("symbol") String symbol) {
        return Result.success(accountService.getBalance(userId, symbol));
    }

    @GetMapping("/account/list")
    @Operation(summary = "按用户列出全部币种账户")
    public Result<List<AccountVO>> list(@RequestParam("userId") Long userId) {
        return Result.success(accountService.listByUser(userId));
    }

    @PostMapping("/freeze")
    @Operation(summary = "冻结")
    public Result<LedgerVO> freeze(@Valid @RequestBody FreezeRequest req) {
        return Result.success(ledgerService.freeze(req));
    }

    @PostMapping("/unfreeze")
    @Operation(summary = "解冻")
    public Result<LedgerVO> unfreeze(@Valid @RequestBody UnfreezeRequest req) {
        return Result.success(ledgerService.unfreeze(req));
    }

    @PostMapping("/transfer")
    @Operation(summary = "过户")
    public Result<LedgerVO> transfer(@Valid @RequestBody TransferRequest req) {
        return Result.success(ledgerService.transfer(req));
    }

    @PostMapping("/credit")
    @Operation(summary = "充值入账")
    public Result<LedgerVO> credit(@Valid @RequestBody CreditRequest req) {
        return Result.success(ledgerService.credit(req));
    }

    @GetMapping("/ledger/list")
    @Operation(summary = "分页查流水")
    public Result<Page<LedgerVO>> ledgerList(@RequestParam(value = "accountId", required = false) Long accountId,
                                             @RequestParam(value = "page", defaultValue = "1") int page,
                                             @RequestParam(value = "size", defaultValue = "20") int size) {
        return Result.success(ledgerService.pageLedgers(accountId, page, size));
    }
}
