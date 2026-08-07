package com.web3.exchange.margin.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.web3.exchange.margin.dto.MarginAccountVO;
import com.web3.exchange.margin.dto.MarginBorrowRequest;
import com.web3.exchange.margin.dto.MarginTransferRequest;
import com.web3.exchange.margin.entity.MarginLoan;
import com.web3.exchange.margin.service.MarginAccountService;
import com.web3.exchange.common.model.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 杠杆现货<b>对外</b> REST 接口（/api/margin/**，经网关路由，需登录）。
 */
@RestController
@RequestMapping("/api/margin")
@Tag(name = "杠杆现货接口")
public class MarginController {

    private final MarginAccountService marginAccountService;

    public MarginController(MarginAccountService marginAccountService) {
        this.marginAccountService = marginAccountService;
    }

    @Operation(summary = "杠杆账户开户")
    @PostMapping("/account/open")
    public Result<MarginAccountVO> open(@RequestParam("userId") Long userId,
                                        @RequestParam("symbol") String symbol) {
        return Result.success(marginAccountService.open(userId, symbol));
    }

    @Operation(summary = "抵押入金（现货→杠杆）")
    @PostMapping("/transfer-in")
    public Result<MarginAccountVO> transferIn(@Valid @RequestBody MarginTransferRequest req) {
        return Result.success(marginAccountService.transferIn(req));
    }

    @Operation(summary = "抵押出金（杠杆→现货）")
    @PostMapping("/transfer-out")
    public Result<MarginAccountVO> transferOut(@Valid @RequestBody MarginTransferRequest req) {
        return Result.success(marginAccountService.transferOut(req));
    }

    @Operation(summary = "借币")
    @PostMapping("/borrow")
    public Result<MarginAccountVO> borrow(@Valid @RequestBody MarginBorrowRequest req) {
        return Result.success(marginAccountService.borrow(req));
    }

    @Operation(summary = "还币")
    @PostMapping("/repay")
    public Result<MarginAccountVO> repay(@Valid @RequestBody MarginBorrowRequest req) {
        return Result.success(marginAccountService.repay(req));
    }

    @Operation(summary = "杠杆账户详情")
    @GetMapping("/account")
    public Result<MarginAccountVO> account(@RequestParam("userId") Long userId,
                                           @RequestParam("symbol") String symbol) {
        return Result.success(marginAccountService.getAccount(userId, symbol));
    }

    @Operation(summary = "借币记录分页")
    @GetMapping("/loans")
    public Result<Page<MarginLoan>> loans(@RequestParam("userId") Long userId,
                                          @RequestParam(value = "page", defaultValue = "1") int page,
                                          @RequestParam(value = "size", defaultValue = "20") int size) {
        return Result.success(marginAccountService.pageLoans(userId, page, size));
    }
}
