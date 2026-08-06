package com.web3.exchange.order.feign;

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
 * <p>
 * 订单域<b>绝不直接改余额</b>，一律经此调用 asset 的 freeze/transfer/unfreeze 完成资金闭环。
 * requestId 全部由 order 派生并保证确定性（见 docs/order-domain.md §6.4），asset 幂等兜底。
 * </p>
 */
@FeignClient(name = "exchange-asset", path = "/internal/asset")
public interface AssetClient {

    /** 幂等开户：确保用户该币种账户存在。 */
    @PostMapping("/account/open")
    Result<AccountVO> open(@RequestParam("userId") Long userId, @RequestParam("symbol") String symbol);

    /** 查询单个账户余额。 */
    @GetMapping("/account/balance")
    Result<AccountVO> getBalance(@RequestParam("userId") Long userId, @RequestParam("symbol") String symbol);

    /** 冻结（下单锁仓）。 */
    @PostMapping("/freeze")
    Result<LedgerVO> freeze(@RequestBody FreezeRequest req);

    /** 解冻（撤单退锁）。 */
    @PostMapping("/unfreeze")
    Result<LedgerVO> unfreeze(@RequestBody UnfreezeRequest req);

    /** 过户（成交结算，计价币/基础币各一笔）。 */
    @PostMapping("/transfer")
    Result<LedgerVO> transfer(@RequestBody TransferRequest req);

    /** 充值入账（测试/对账用）。 */
    @PostMapping("/credit")
    Result<LedgerVO> credit(@RequestBody CreditRequest req);
}
