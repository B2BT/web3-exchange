package com.web3.exchange.chain.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.web3.exchange.chain.dto.AuditRequest;
import com.web3.exchange.chain.dto.WithdrawApplyRequest;
import com.web3.exchange.chain.dto.WithdrawVO;

/**
 * 提现服务：申请落单 → 审核冻结 → 离线签名广播 → 回执确认扣减/失败回滚。
 */
public interface WithdrawService {

    /** 申请提现（幂等落单 status=0）。 */
    WithdrawVO apply(WithdrawApplyRequest req);

    /** 审核：approve=true → 冻结 + 签名广播；approve=false → status=4 拒绝。 */
    WithdrawVO audit(Long withdrawId, AuditRequest req);

    /** 触发上链：将 status=2 的单签名广播（供补偿/重试）。 */
    void send(Long withdrawId);

    /** 查询回执：成功 → transfer 扣减 status=3；失败 → unfreeze 回滚 status=5。 */
    void confirm(Long withdrawId);

    /** 扫描所有 status=2 且已上链的待确认提现（定时任务）。 */
    void confirmPending();

    /** 提现详情。 */
    WithdrawVO getWithdraw(Long withdrawId);

    /** 冷钱包多签：提交一个离线签名（N 审 1 签），达到阈值后广播。 */
    WithdrawVO submitColdSignature(Long withdrawId, String signedRawHex);

    /** 冷钱包：待签名提现清单（status=6 的待离线签名大额提现）。 */
    java.util.List<WithdrawVO> listColdPending();

    /** 用户提现记录分页。 */
    Page<WithdrawVO> pageByUser(Long userId, int page, int size);
}
