package com.web3.exchange.staking.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.web3.exchange.common.model.Result;
import com.web3.exchange.staking.dto.StakingRequest;
import com.web3.exchange.staking.entity.StakingInterest;
import com.web3.exchange.staking.entity.StakingPosition;
import com.web3.exchange.staking.entity.StakingProduct;
import com.web3.exchange.staking.service.StakingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Staking/Earn 对外 REST 接口（/api/staking/**，经网关路由，需登录）。
 */
@RestController
@RequestMapping("/api/staking")
@Tag(name = "Staking/Earn 接口")
public class StakingController {

    private final StakingService stakingService;

    public StakingController(StakingService stakingService) {
        this.stakingService = stakingService;
    }

    @Operation(summary = "质押产品列表")
    @GetMapping("/products")
    public Result<List<StakingProduct>> products() {
        return Result.success(stakingService.listProducts());
    }

    @Operation(summary = "质押")
    @PostMapping("/stake")
    public Result<StakingPosition> stake(@Valid @RequestBody StakingRequest req) {
        return Result.success(stakingService.stake(req));
    }

    @Operation(summary = "赎回")
    @PostMapping("/redeem")
    public Result<StakingPosition> redeem(@Valid @RequestBody StakingRequest req) {
        return Result.success(stakingService.redeem(req));
    }

    @Operation(summary = "我的持仓")
    @GetMapping("/positions")
    public Result<List<StakingPosition>> positions(@RequestParam("userId") Long userId) {
        return Result.success(stakingService.listPositions(userId));
    }

    @Operation(summary = "收益流水分页")
    @GetMapping("/interests")
    public Result<Page<StakingInterest>> interests(@RequestParam("userId") Long userId,
                                                   @RequestParam(value = "page", defaultValue = "1") int page,
                                                   @RequestParam(value = "size", defaultValue = "20") int size) {
        return Result.success(stakingService.pageInterests(userId, page, size));
    }
}
