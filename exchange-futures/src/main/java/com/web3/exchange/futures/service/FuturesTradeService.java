package com.web3.exchange.futures.service;

import com.web3.exchange.futures.dto.PlaceFuturesOrderDTO;
import com.web3.exchange.futures.entity.FuturesOrder;

/**
 * 合约交易服务：下单、撮合、持仓与保证金核算。
 */
public interface FuturesTradeService {

    /**
     * 下单（开/平仓）。撮合后更新持仓与账户。
     *
     * @return 落库的订单
     */
    FuturesOrder placeOrder(Long userId, PlaceFuturesOrderDTO dto);

    /** 撤单。 */
    boolean cancel(Long userId, String symbol, String orderNo);
}
