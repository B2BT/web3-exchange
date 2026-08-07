package com.web3.exchange.futures.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.web3.exchange.common.model.Result;
import com.web3.exchange.futures.entity.SwapContract;
import com.web3.exchange.futures.mapper.SwapContractMapper;
import com.web3.exchange.futures.service.MarkPriceService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 合约公开接口（M1）：合约列表 + 标记价。无需鉴权。
 */
@RestController
@RequestMapping("/api/futures")
@RequiredArgsConstructor
public class FuturesPublicController {

    private final SwapContractMapper contractMapper;
    private final MarkPriceService markPriceService;

    /** 上架合约列表。 */
    @GetMapping("/contracts")
    public Result<List<SwapContract>> contracts() {
        List<SwapContract> list = contractMapper.selectList(
                new LambdaQueryWrapper<SwapContract>().eq(SwapContract::getStatus, 0));
        return Result.success(list);
    }

    /** 某交易对标记价（最小单位 Long）。 */
    @GetMapping("/mark/{symbol:.+}")
    public Result<Long> markPrice(@PathVariable("symbol") String symbol) {
        return Result.success(markPriceService.getMarkPrice(symbol));
    }
}
