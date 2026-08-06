package com.web3.exchange.admin.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.web3.exchange.admin.dto.AuditRequest;
import com.web3.exchange.admin.entity.Withdraw;
import com.web3.exchange.admin.feign.ChainClient;
import com.web3.exchange.admin.mapper.WithdrawMapper;
import com.web3.exchange.admin.service.AdminWithdrawService;
import com.web3.exchange.admin.vo.WithdrawVO;
import com.web3.exchange.common.exception.ServiceException;
import com.web3.exchange.common.model.Result;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

/**
 * 后台提现管理服务实现。
 * <p>查询直接读 t_withdraw（同库）；审核经 Feign 调 exchange-chain 内部接口，
 * 复用 chain 域 WithdrawService.audit 的「冻结上链 / 拒绝 / 失败回滚」核心逻辑。</p>
 */
@Slf4j
@Service
public class AdminWithdrawServiceImpl implements AdminWithdrawService {

    private final WithdrawMapper withdrawMapper;
    private final ChainClient chainClient;

    public AdminWithdrawServiceImpl(WithdrawMapper withdrawMapper, ChainClient chainClient) {
        this.withdrawMapper = withdrawMapper;
        this.chainClient = chainClient;
    }

    @Override
    public Page<WithdrawVO> pageWithdraws(int page, int size, Integer status) {
        LambdaQueryWrapper<Withdraw> qw = new LambdaQueryWrapper<>();
        if (status != null) {
            qw.eq(Withdraw::getStatus, status);
        }
        qw.orderByDesc(Withdraw::getId);
        Page<Withdraw> p = withdrawMapper.selectPage(new Page<>(page, size), qw);
        Page<WithdrawVO> voPage = new Page<>(p.getCurrent(), p.getSize(), p.getTotal());
        voPage.setRecords(p.getRecords().stream().map(this::toVO).toList());
        return voPage;
    }

    @Override
    public WithdrawVO audit(Long id, AuditRequest req) {
        // 组装 chain 域 AuditRequest 兼容 body：{approve, remark}
        Map<String, Object> body = new HashMap<>();
        body.put("approve", Boolean.TRUE.equals(req.getApproved()));
        body.put("remark", req.getRemark());
        Result<WithdrawVO> r = chainClient.audit(id, body);
        if (r == null || !r.isSuccess()) {
            String msg = r == null ? "审核调用失败" : r.getMessage();
            throw new ServiceException(msg);
        }
        return r.getData();
    }

    private WithdrawVO toVO(Withdraw w) {
        WithdrawVO vo = new WithdrawVO();
        vo.setId(w.getId());
        vo.setRequestId(w.getRequestId());
        vo.setUserId(w.getUserId());
        vo.setSymbol(w.getSymbol());
        vo.setChainCode(w.getChainCode());
        vo.setToAddress(w.getToAddress());
        vo.setAmount(w.getAmount());
        vo.setFee(w.getFee());
        vo.setRealAmount(w.getRealAmount());
        vo.setStatus(w.getStatus());
        vo.setAuditBy(w.getAuditBy());
        vo.setAuditTime(w.getAuditTime());
        vo.setAuditRemark(w.getAuditRemark());
        vo.setFreezeLedgerId(w.getFreezeLedgerId());
        vo.setTxHash(w.getTxHash());
        vo.setFailReason(w.getFailReason());
        vo.setCreateTime(w.getCreateTime());
        return vo;
    }
}
