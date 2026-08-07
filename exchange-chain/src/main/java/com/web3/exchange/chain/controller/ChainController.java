package com.web3.exchange.chain.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.web3.exchange.chain.dto.AssetAddressVO;
import com.web3.exchange.chain.dto.AuditRequest;
import com.web3.exchange.chain.dto.DepositVO;
import com.web3.exchange.chain.dto.WithdrawApplyRequest;
import com.web3.exchange.chain.dto.WithdrawVO;
import com.web3.exchange.chain.entity.AssetAddress;
import com.web3.exchange.chain.service.DepositService;
import com.web3.exchange.chain.service.WithdrawService;
import com.web3.exchange.common.model.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 链上域<b>对外</b> REST 接口（/api/chain/**，经网关路由）。充值/提现业务编排。
 */
@RestController
@RequestMapping("/api/chain")
@Tag(name = "链上域对外接口")
public class ChainController {

    private final DepositService depositService;
    private final WithdrawService withdrawService;

    public ChainController(DepositService depositService, WithdrawService withdrawService) {
        this.depositService = depositService;
        this.withdrawService = withdrawService;
    }

    @Operation(summary = "查询用户充币地址（无则自动生成 BIP44 派生地址）")
    @GetMapping("/deposit/address")
    public Result<AssetAddressVO> depositAddress(@RequestParam("userId") Long userId,
                                                 @RequestParam("chainCode") String chainCode,
                                                 @RequestParam("symbol") String symbol) {
        AssetAddress addr = depositService.getOrCreateDepositAddress(userId, chainCode, symbol);
        AssetAddressVO vo = new AssetAddressVO();
        vo.setId(addr.getId());
        vo.setUserId(addr.getUserId());
        vo.setChainCode(addr.getChainCode());
        vo.setSymbol(addr.getSymbol());
        vo.setAddress(addr.getAddress());
        vo.setMemo(addr.getMemo());
        vo.setAddressType(addr.getAddressType());
        vo.setIsActive(addr.getIsActive());
        return Result.success(vo);
    }

    @Operation(summary = "充值记录分页")
    @GetMapping("/deposit/list")
    public Result<Page<DepositVO>> depositList(@RequestParam("userId") Long userId,
                                               @RequestParam(value = "page", defaultValue = "1") int page,
                                               @RequestParam(value = "size", defaultValue = "20") int size) {
        return Result.success(depositService.pageByUser(userId, page, size));
    }

    @Operation(summary = "申请提现")
    @PostMapping("/withdraw/apply")
    public Result<WithdrawVO> withdrawApply(@Valid @RequestBody WithdrawApplyRequest req) {
        return Result.success(withdrawService.apply(req));
    }

    @Operation(summary = "提现记录分页")
    @GetMapping("/withdraw/list")
    public Result<Page<WithdrawVO>> withdrawList(@RequestParam("userId") Long userId,
                                                 @RequestParam(value = "page", defaultValue = "1") int page,
                                                 @RequestParam(value = "size", defaultValue = "20") int size) {
        return Result.success(withdrawService.pageByUser(userId, page, size));
    }

    @Operation(summary = "提现详情")
    @GetMapping("/withdraw/{id}")
    public Result<WithdrawVO> withdrawDetail(@PathVariable("id") Long id) {
        return Result.success(withdrawService.getWithdraw(id));
    }

    @Operation(summary = "提现审核（运营）")
    @PostMapping("/withdraw/{id}/audit")
    public Result<WithdrawVO> withdrawAudit(@PathVariable("id") Long id,
                                            @RequestBody AuditRequest req) {
        return Result.success(withdrawService.audit(id, req));
    }
}
