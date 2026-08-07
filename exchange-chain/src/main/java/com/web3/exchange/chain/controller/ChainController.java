package com.web3.exchange.chain.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.web3.exchange.chain.dto.AssetAddressVO;
import com.web3.exchange.chain.dto.AuditRequest;
import com.web3.exchange.chain.dto.DepositVO;
import com.web3.exchange.chain.dto.WithdrawApplyRequest;
import com.web3.exchange.chain.dto.WithdrawVO;
import com.web3.exchange.chain.entity.AssetAddress;
import com.web3.exchange.chain.feign.RiskClient;
import com.web3.exchange.chain.service.DepositService;
import com.web3.exchange.chain.service.WithdrawService;
import com.web3.exchange.common.exception.BusinessException;
import com.web3.exchange.common.model.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
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
@Slf4j
public class ChainController {

    private final DepositService depositService;
    private final WithdrawService withdrawService;
    private final RiskClient riskClient;

    public ChainController(DepositService depositService, WithdrawService withdrawService, RiskClient riskClient) {
        this.depositService = depositService;
        this.withdrawService = withdrawService;
        this.riskClient = riskClient;
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
        // 每链一地址，VO 的 symbol 以请求的为准（避免同链 ETH/USDT 共用时展示错位）
        vo.setSymbol(symbol);
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
        // 风控前置（P2.4）：反钓鱼码校验（已设置则须正确）
        try {
            RiskClient.WithdrawRiskRequest r = new RiskClient.WithdrawRiskRequest();
            r.userId = req.getUserId();
            r.withdrawId = 0L; // 落单前未知，先用0占位（risk 仅校验反钓鱼码）
            r.symbol = req.getSymbol();
            r.amount = req.getAmount();
            r.phrase = req.getAntiPhishing();
            Result<RiskClient.WithdrawRiskResult> res = riskClient.preCheckWithdraw(r);
            if (res != null && res.isSuccess() && res.getData() != null && !res.getData().pass) {
                throw new BusinessException(res.getData().reason);
            }
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            // 风控降级：调用失败不阻塞提现
            log.warn("[chain] 提现风控校验异常,降级放行 userId={}: {}", req.getUserId(), e.getMessage());
        }
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
