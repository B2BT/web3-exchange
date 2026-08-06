package com.web3.exchange.chain.feign;

import com.web3.exchange.common.asset.dto.AccountVO;
import com.web3.exchange.common.asset.dto.CreditRequest;
import com.web3.exchange.common.asset.dto.FreezeRequest;
import com.web3.exchange.common.asset.dto.LedgerVO;
import com.web3.exchange.common.asset.dto.TransferRequest;
import com.web3.exchange.common.asset.dto.UnfreezeRequest;
import com.web3.exchange.common.model.Result;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

/**
 * 资产域内部接口客户端（Feign，对接 exchange-asset 的 /internal/asset/**）。
 * <p>链上域<b>绝不直接改余额</b>，一律经此调用 asset 的 credit(充值入账)/freeze(提现冻结)/
 * unfreeze(失败回滚)/transfer(成功扣减)。requestId 全部由 chain 派生（见 docs/chain-domain.md §5.5），
 * asset 幂等兜底。</p>
 */
@FeignClient(name = "exchange-asset", path = "/internal/asset")
public interface AssetClient {

    /** 幂等开户：确保用户该币种账户存在。 */
    @PostMapping("/account/open")
    Result<AccountVO> open(@RequestParam("userId") Long userId, @RequestParam("symbol") String symbol);

    /** 查询单个账户余额。 */
    @GetMapping("/account/balance")
    Result<AccountVO> getBalance(@RequestParam("userId") Long userId, @RequestParam("symbol") String symbol);

    /** 充值入账：链上确认后给用户可用余额加钱。 */
    @PostMapping("/credit")
    Result<LedgerVO> credit(@RequestBody CreditRequest req);

    /** 冻结（提现审核通过后锁定可用余额）。 */
    @PostMapping("/freeze")
    Result<LedgerVO> freeze(@RequestBody FreezeRequest req);

    /** 解冻（提现失败回滚）。 */
    @PostMapping("/unfreeze")
    Result<LedgerVO> unfreeze(@RequestBody UnfreezeRequest req);

    /** 过户（提现成功：用户冻结 → 平台热钱包账户可用）。 */
    @PostMapping("/transfer")
    Result<LedgerVO> transfer(@RequestBody TransferRequest req);
}
