package com.web3.exchange.chain.controller;

import com.web3.exchange.chain.dto.AuditRequest;
import com.web3.exchange.chain.dto.WithdrawVO;
import com.web3.exchange.chain.service.WithdrawService;
import com.web3.exchange.common.model.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 链上域<b>内部</b>接口（/internal/chain/**）——服务间调用，网关不路由 /internal/**。
 * 供提现补偿/重试与回执确认查询使用。
 */
@RestController
@RequestMapping("/internal/chain")
@Tag(name = "链上域内部接口")
public class InternalChainController {

    private final WithdrawService withdrawService;

    public InternalChainController(WithdrawService withdrawService) {
        this.withdrawService = withdrawService;
    }

    /** 触发上链：将 status=2 的单签名广播（供补偿/重试）。 */
    @Operation(summary = "触发上链")
    @PostMapping("/withdraw/send")
    public Result<Void> send(@RequestParam("withdrawId") Long withdrawId) {
        withdrawService.send(withdrawId);
        return Result.success();
    }

    /** 查询确认：查回执，触发成功扣减或失败回滚。 */
    @Operation(summary = "查询提现确认")
    @GetMapping("/withdraw/confirm")
    public Result<WithdrawVO> confirm(@RequestParam("withdrawId") Long withdrawId) {
        withdrawService.confirm(withdrawId);
        return Result.success(withdrawService.getWithdraw(withdrawId));
    }

    /** 提现审核（管理平台 exchange-admin 调用）：approve=true 冻结上链 / false 拒绝。 */
    @Operation(summary = "提现审核")
    @PostMapping("/withdraw/audit")
    public Result<WithdrawVO> audit(@RequestParam("withdrawId") Long withdrawId,
                                    @RequestBody AuditRequest req) {
        return Result.success(withdrawService.audit(withdrawId, req));
    }

    /** 冷钱包：待签名提现清单（管理平台拉取待离线签名的大额提现）。 */
    @Operation(summary = "冷钱包待签名清单")
    @GetMapping("/withdraw/cold/pending")
    public Result<java.util.List<WithdrawVO>> coldPending() {
        return Result.success(withdrawService.listColdPending());
    }

    /** 冷钱包：提交离线签名（N 审 1 签，达到阈值后广播）。 */
    @Operation(summary = "提交冷钱包签名")
    @PostMapping("/withdraw/cold/sign")
    public Result<WithdrawVO> coldSign(@RequestParam("withdrawId") Long withdrawId,
                                       @RequestParam("signedRawHex") String signedRawHex) {
        return Result.success(withdrawService.submitColdSignature(withdrawId, signedRawHex));
    }
}
