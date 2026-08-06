package com.web3.exchange.admin.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.web3.exchange.admin.dto.AuditRequest;
import com.web3.exchange.admin.vo.WithdrawVO;

/**
 * 后台提现管理服务：提现申请分页 + 审核（Feign 调 chain 域复用审核逻辑）。
 */
public interface AdminWithdrawService {

    /** 提现申请分页，支持 status 过滤。 */
    Page<WithdrawVO> pageWithdraws(int page, int size, Integer status);

    /** 审核：approved=true 冻结上链 / false 拒绝。 */
    WithdrawVO audit(Long id, AuditRequest req);
}
