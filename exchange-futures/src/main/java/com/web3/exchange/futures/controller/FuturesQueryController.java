package com.web3.exchange.futures.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.web3.exchange.common.model.Result;
import com.web3.exchange.futures.entity.FuturesAccount;
import com.web3.exchange.futures.entity.FuturesPosition;
import com.web3.exchange.futures.mapper.FuturesPositionMapper;
import com.web3.exchange.futures.service.FuturesAccountService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.List;

/**
 * 合约查询 + 测试入金接口。
 */
@RestController
@RequestMapping("/api/futures")
@RequiredArgsConstructor
public class FuturesQueryController {

    private final FuturesAccountService accountService;
    private final FuturesPositionMapper positionMapper;

    /** 合约账户。 */
    @GetMapping("/account")
    public Result<FuturesAccount> account(@RequestParam("userId") Long userId, @RequestParam(value = "coin", defaultValue = "USDT") String coin) {
        return Result.success(accountService.getOrCreate(userId, coin));
    }

    /** 持仓列表。 */
    @GetMapping("/position")
    public Result<List<FuturesPosition>> positions(@RequestParam("userId") Long userId) {
        List<FuturesPosition> list = positionMapper.selectList(
                new LambdaQueryWrapper<FuturesPosition>().eq(FuturesPosition::getUserId, userId)
                        .eq(FuturesPosition::getStatus, 0));
        return Result.success(list);
    }

    /** 测试入金（演示用：给合约账户加 USDT）。 */
    @PostMapping("/deposit")
    public Result<FuturesAccount> deposit(@RequestParam("userId") Long userId,
                                          @RequestParam(value = "coin", defaultValue = "USDT") String coin,
                                          @RequestParam("amount") String amount) {
        long min = new BigDecimal(amount).movePointRight(8).setScale(0, BigDecimal.ROUND_HALF_UP).longValue();
        return Result.success(accountService.deposit(userId, coin, min));
    }
}
