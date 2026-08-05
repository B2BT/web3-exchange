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
 * 资产<b>内部</b>接口（/internal/asset/**）——仅供服务间 Feign 调用，网关不路由 /internal/**，
 * 不对外暴露。
 * <p>
 * 提供开户、查余额、按用户列出、冻结、解冻、过户、充值入账、流水分页等能力。
 * 调用方：order（冻结/解冻/过户）、chain（充值入账 credit）、user（可选查询）。
 * 所有资金操作请求须携带调用方生成的 requestId 以保证幂等；金额一律为最小单位 long。
 * 所有写操作内部为同一本地事务（写流水 + 更新余额），配行锁 + 乐观锁，失败整体回滚。
 * </p>
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

    /**
     * 幂等开户：确保用户该币种账户存在。已存在则直接返回现有账户，
     * 首次调用为所有已配置币种各建一行（由 AccountService.open 处理）。
     */
    @PostMapping("/account/open")
    @Operation(summary = "幂等开户")
    public Result<AccountVO> open(@RequestParam("userId") Long userId,
                                  @RequestParam("symbol") String symbol) {
        return Result.success(accountService.open(userId, symbol));
    }

    /**
     * 查询单个账户余额；账户不存在返回 notFound。
     */
    @GetMapping("/account/balance")
    @Operation(summary = "查询单账户余额")
    public Result<AccountVO> balance(@RequestParam("userId") Long userId,
                                     @RequestParam("symbol") String symbol) {
        return Result.success(accountService.getBalance(userId, symbol));
    }

    /**
     * 按用户列出全部币种账户（钱包总览）。
     */
    @GetMapping("/account/list")
    @Operation(summary = "按用户列出全部币种账户")
    public Result<List<AccountVO>> list(@RequestParam("userId") Long userId) {
        return Result.success(accountService.listByUser(userId));
    }

    /**
     * 冻结：可用余额转入冻结（下单锁仓）。调用方生成 requestId 幂等。
     */
    @PostMapping("/freeze")
    @Operation(summary = "冻结")
    public Result<LedgerVO> freeze(@Valid @RequestBody FreezeRequest req) {
        return Result.success(ledgerService.freeze(req));
    }

    /**
     * 解冻：冻结余额释放回可用（撤单退锁）。调用方生成 requestId 幂等。
     */
    @PostMapping("/unfreeze")
    @Operation(summary = "解冻")
    public Result<LedgerVO> unfreeze(@Valid @RequestBody UnfreezeRequest req) {
        return Result.success(ledgerService.unfreeze(req));
    }

    /**
     * 过户（成交结算）：单事务内 from 冻结减少 + to 可用增加，各写一条流水。
     * 调用方生成 requestId（通常取成交单号）幂等。
     */
    @PostMapping("/transfer")
    @Operation(summary = "过户")
    public Result<LedgerVO> transfer(@Valid @RequestBody TransferRequest req) {
        return Result.success(ledgerService.transfer(req));
    }

    /**
     * 充值入账：chain 扫描确认后调用，给用户可用余额加钱（写 DEPOSIT 流水）。
     * requestId 由 depositId 派生，唯一索引兜底防同一笔充值重复入账。
     */
    @PostMapping("/credit")
    @Operation(summary = "充值入账")
    public Result<LedgerVO> credit(@Valid @RequestBody CreditRequest req) {
        return Result.success(ledgerService.credit(req));
    }

    /**
     * 分页查询资产流水（供对账/审计）。
     */
    @GetMapping("/ledger/list")
    @Operation(summary = "分页查流水")
    public Result<Page<LedgerVO>> ledgerList(@RequestParam(value = "accountId", required = false) Long accountId,
                                             @RequestParam(value = "page", defaultValue = "1") int page,
                                             @RequestParam(value = "size", defaultValue = "20") int size) {
        return Result.success(ledgerService.pageLedgers(accountId, page, size));
    }
}
