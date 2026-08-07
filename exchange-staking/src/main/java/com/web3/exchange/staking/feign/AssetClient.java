package com.web3.exchange.staking.feign;

import com.web3.exchange.common.asset.dto.CreditRequest;
import com.web3.exchange.common.asset.dto.FreezeRequest;
import com.web3.exchange.common.asset.dto.LedgerVO;
import com.web3.exchange.common.asset.dto.UnfreezeRequest;
import com.web3.exchange.common.model.Result;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

/**
 * 调用 exchange-asset 内部资金接口（质押 freeze / 赎回 unfreeze / 收益 credit）。
 */
@FeignClient(name = "exchange-asset", path = "/internal/asset")
public interface AssetClient {

    @PostMapping("/freeze")
    Result<LedgerVO> freeze(@RequestBody FreezeRequest req);

    @PostMapping("/unfreeze")
    Result<LedgerVO> unfreeze(@RequestBody UnfreezeRequest req);

    @PostMapping("/credit")
    Result<LedgerVO> credit(@RequestBody CreditRequest req);
}
