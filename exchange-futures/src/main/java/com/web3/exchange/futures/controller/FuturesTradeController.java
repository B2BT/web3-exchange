package com.web3.exchange.futures.controller;

import com.web3.exchange.common.model.Result;
import com.web3.exchange.futures.dto.PlaceFuturesOrderDTO;
import com.web3.exchange.futures.entity.FuturesOrder;
import com.web3.exchange.futures.service.FuturesTradeService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 合约交易接口（需鉴权）：下单/撤单。
 */
@RestController
@RequestMapping("/api/futures")
@RequiredArgsConstructor
public class FuturesTradeController {

    private final FuturesTradeService tradeService;

    /** 下单（开/平仓）。 */
    @PostMapping("/order")
    public Result<FuturesOrder> place(@RequestBody @Valid PlaceFuturesOrderDTO dto) {
        return Result.success(tradeService.placeOrder(dto.getUserId(), dto));
    }

    /** 撤单。 */
    @PostMapping("/cancel")
    public Result<Boolean> cancel(@RequestParam("userId") Long userId,
                                  @RequestParam("symbol") String symbol,
                                  @RequestParam("orderNo") String orderNo) {
        return Result.success(tradeService.cancel(userId, symbol, orderNo));
    }
}
