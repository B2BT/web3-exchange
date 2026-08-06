package com.web3.exchange.order.service;

import com.web3.exchange.common.exception.ServiceException;
import com.web3.exchange.order.entity.Coin;
import com.web3.exchange.order.mapper.CoinMapper;
import org.springframework.stereotype.Service;

/**
 * 币种精度服务（order 域）——直接查 t_coin（与 asset 同库），供金额换算取计价币/基础币 decimals。
 * <p>与资产域口径一致：USDT=6、BTC=8、ETH=18。</p>
 */
@Service
public class OrderCoinService {

    private final CoinMapper coinMapper;

    public OrderCoinService(CoinMapper coinMapper) {
        this.coinMapper = coinMapper;
    }

    /**
     * 查询币种精度 decimals；币种不存在或未配置精度抛业务异常。
     */
    public int requireDecimals(String symbol) {
        Coin c = coinMapper.selectBySymbol(symbol);
        if (c == null || c.getDecimals() == null) {
            throw new ServiceException("币种不存在或未配置精度: " + symbol);
        }
        return c.getDecimals();
    }
}
