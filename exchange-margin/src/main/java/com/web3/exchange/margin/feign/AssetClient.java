package com.web3.exchange.margin.feign;

import com.web3.exchange.common.asset.dto.FreezeRequest;
import com.web3.exchange.common.asset.dto.LedgerVO;
import com.web3.exchange.common.asset.dto.UnfreezeRequest;
import com.web3.exchange.common.model.Result;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

/**
 * 调用 exchange-asset 内部资金接口（抵押入金/出金）。
 */
@FeignClient(name = "exchange-asset", path = "/internal/asset")
public interface AssetClient {

    @PostMapping("/freeze")
    Result<LedgerVO> freeze(@RequestBody FreezeRequest req);

    @PostMapping("/unfreeze")
    Result<LedgerVO> unfreeze(@RequestBody UnfreezeRequest req);
}
