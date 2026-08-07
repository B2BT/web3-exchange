package com.web3.exchange.futures.feign;

import com.web3.exchange.common.model.Result;
import com.web3.exchange.futures.dto.SpotTickerVO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

/**
 * 现货行情内部客户端（Feign，对接 exchange-market 的公开 /api/market/ticker）。
 * <p>合约标记价以现货价为锚，防止插针操纵。本模块绝不直接改现货行情，只读。</p>
 */
@FeignClient(name = "exchange-market", path = "/api/market")
public interface MarketClient {

    /** 单交易对 ticker（现货价，如 BTC/USDT）。 */
    @GetMapping("/ticker/{symbol:.+}")
    Result<SpotTickerVO> ticker(@PathVariable("symbol") String symbol);
}
