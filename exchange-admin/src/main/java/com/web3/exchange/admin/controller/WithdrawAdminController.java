package com.web3.exchange.admin.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.web3.exchange.admin.dto.AuditRequest;
import com.web3.exchange.admin.service.AdminWithdrawService;
import com.web3.exchange.admin.vo.WithdrawVO;
import com.web3.exchange.common.model.PageData;
import com.web3.exchange.common.model.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 后台提现管理接口（/api/admin/withdraw/**，需 ADMIN 角色）。
 */
@RestController
@RequestMapping("/api/admin/withdraw")
@Tag(name = "后台提现管理")
public class WithdrawAdminController {

    private final AdminWithdrawService adminWithdrawService;

    public WithdrawAdminController(AdminWithdrawService adminWithdrawService) {
        this.adminWithdrawService = adminWithdrawService;
    }

    @Operation(summary = "提现申请分页")
    @GetMapping("/list")
    public Result<PageData<WithdrawVO>> list(@RequestParam(value = "page", defaultValue = "1") int page,
                                             @RequestParam(value = "size", defaultValue = "20") int size,
                                             @RequestParam(value = "status", required = false) Integer status) {
        Page<WithdrawVO> p = adminWithdrawService.pageWithdraws(page, size, status);
        return Result.success(PageData.of(p));
    }

    @Operation(summary = "提现审核")
    @PostMapping("/{id}/audit")
    public Result<WithdrawVO> audit(@PathVariable("id") Long id,
                                    @RequestBody AuditRequest req) {
        return Result.success(adminWithdrawService.audit(id, req));
    }
}
