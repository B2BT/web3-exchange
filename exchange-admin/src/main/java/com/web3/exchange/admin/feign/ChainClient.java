package com.web3.exchange.admin.feign;

import com.web3.exchange.admin.vo.WithdrawVO;
import com.web3.exchange.common.model.Result;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.Map;

/**
 * 链上域内部接口客户端（Feign，对接 exchange-chain 的 /internal/chain/**）。
 * <p>提现审核复用 chain 域 WithdrawService.audit 的核心逻辑（冻结上链/拒绝/回滚），
 * 本域仅做状态查询与鉴权，不重复实现资产冻结/广播。</p>
 */
@FeignClient(name = "exchange-chain", contextId = "adminChainClient", path = "/internal/chain")
public interface ChainClient {

    /**
     * 提现审核。body 字段与 chain 域 AuditRequest 对齐：{approve, remark}。
     * 返回体反序列化为本域 WithdrawVO（字段同名）。
     */
    @PostMapping("/withdraw/audit")
    Result<WithdrawVO> audit(@RequestParam("withdrawId") Long withdrawId,
                             @RequestBody Map<String, Object> body);
}
