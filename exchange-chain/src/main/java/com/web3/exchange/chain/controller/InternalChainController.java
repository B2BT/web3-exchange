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
}
