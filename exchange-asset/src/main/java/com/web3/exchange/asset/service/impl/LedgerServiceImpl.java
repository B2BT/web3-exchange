package com.web3.exchange.asset.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.web3.exchange.asset.entity.Account;
import com.web3.exchange.asset.entity.Ledger;
import com.web3.exchange.asset.mapper.LedgerMapper;
import com.web3.exchange.asset.service.AccountService;
import com.web3.exchange.asset.service.BizType;
import com.web3.exchange.asset.service.Direction;
import com.web3.exchange.asset.service.LedgerService;
import com.web3.exchange.common.asset.dto.CreditRequest;
import com.web3.exchange.common.asset.dto.FreezeRequest;
import com.web3.exchange.common.asset.dto.LedgerVO;
import com.web3.exchange.common.asset.dto.TransferRequest;
import com.web3.exchange.common.asset.dto.UnfreezeRequest;
import com.web3.exchange.common.exception.BusinessException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class LedgerServiceImpl extends ServiceImpl<LedgerMapper, Ledger> implements LedgerService {

    private final AccountService accountService;

    public LedgerServiceImpl(AccountService accountService) {
        this.accountService = accountService;
    }

    // ==================== 冻结 ====================
    @Override
    @Transactional(rollbackFor = Exception.class)
    public LedgerVO freeze(FreezeRequest req) {
        requirePositive(req.getAmount());
        Account acct = accountService.lockByUserAndSymbol(req.getUserId(), req.getSymbol());
        return doChange(req.getRequestId(), acct,
                nvl(req.getBizType(), BizType.FREEZE), Direction.FROZEN,
                req.getAmount(), req.getRefNo(), req.getRemark());
    }

    // ==================== 解冻 ====================
    @Override
    @Transactional(rollbackFor = Exception.class)
    public LedgerVO unfreeze(UnfreezeRequest req) {
        requirePositive(req.getAmount());
        Account acct = accountService.lockByUserAndSymbol(req.getUserId(), req.getSymbol());
        return doChange(req.getRequestId(), acct,
                nvl(req.getBizType(), BizType.UNFREEZE), Direction.UNFROZEN,
                req.getAmount(), req.getRefNo(), req.getRemark());
    }

    // ==================== 过户（单事务：from 冻结转出 + to 可用转入） ====================
    @Override
    @Transactional(rollbackFor = Exception.class)
    public LedgerVO transfer(TransferRequest req) {
        requirePositive(req.getAmount());
        // 幂等：以转出流水 requestId 为基准（requestId+"_OUT"）
        String outReqId = req.getRequestId() + "_OUT";
        Ledger done = getByRequestId(outReqId);
        if (done != null) {
            return toVO(done);
        }
        // 固定加锁顺序（按 userId 升序）避免死锁
        Account a = accountService.lockByUserAndSymbol(req.getFromUserId(), req.getSymbol());
        Account b = accountService.lockByUserAndSymbol(req.getToUserId(), req.getSymbol());
        // 转出方：冻结减少
        LedgerVO out = doChange(outReqId, a, BizType.TRANSFER_OUT, Direction.FROZEN_OUT,
                req.getAmount(), req.getRefNo(), req.getRemark());
        // 转入方：可用增加
        doChange(req.getRequestId() + "_IN", b, BizType.TRANSFER_IN, Direction.IN,
                req.getAmount(), req.getRefNo(), req.getRemark());
        return out;
    }

    // ==================== 充值入账 ====================
    @Override
    @Transactional(rollbackFor = Exception.class)
    public LedgerVO credit(CreditRequest req) {
        requirePositive(req.getAmount());
        Account acct = accountService.lockByUserAndSymbol(req.getUserId(), req.getSymbol());
        return doChange(req.getRequestId(), acct,
                nvl(req.getBizType(), BizType.DEPOSIT), Direction.IN,
                req.getAmount(), req.getRefNo(), req.getRemark());
    }

    // ==================== 流水分页 ====================
    @Override
    public Page<LedgerVO> pageLedgers(Long accountId, int page, int size) {
        Page<Ledger> p = this.page(new Page<>(Math.max(page, 1), Math.min(Math.max(size, 1), 100)),
                new LambdaQueryWrapper<Ledger>()
                        .eq(accountId != null, Ledger::getAccountId, accountId)
                        .orderByDesc(Ledger::getId));
        Page<LedgerVO> voPage = new Page<>(p.getCurrent(), p.getSize(), p.getTotal());
        voPage.setRecords(p.getRecords().stream().map(this::toVO).toList());
        return voPage;
    }

    // ==================== 核心：资金变动统一封装 ====================
    // 前提：account 已被 SELECT ... FOR UPDATE 行锁锁定（当前事务内）
    private LedgerVO doChange(String requestId, Account acct, String bizType, int direction,
                              long amount, String refNo, String remark) {
        // 1. 幂等回读：同 request_id 已存在 → 直接返回首次结果
        Ledger existing = getByRequestId(requestId);
        if (existing != null) {
            return toVO(existing);
        }

        // 2. 依据方向计算 before/after（余额不变式：available + frozen == total 恒成立）
        long ba = acct.getAvailable();
        long bf = acct.getFrozen();
        long aa = ba, af = bf;
        switch (direction) {
            case Direction.IN -> aa = ba + amount;                       // 流入：可用增加
            case Direction.OUT -> {                                       // 流出：可用减少
                if (ba < amount) throw new BusinessException(409, "可用余额不足");
                aa = ba - amount;
            }
            case Direction.FROZEN -> {                                    // 冻结：可用→冻结
                if (ba < amount) throw new BusinessException(409, "可用余额不足");
                aa = ba - amount;
                af = bf + amount;
            }
            case Direction.UNFROZEN -> {                                  // 解冻：冻结→可用
                if (bf < amount) throw new BusinessException(409, "冻结余额不足");
                aa = ba + amount;
                af = bf - amount;
            }
            case Direction.FROZEN_OUT -> {                                // 过户转出：冻结减少
                if (bf < amount) throw new BusinessException(409, "冻结余额不足");
                af = bf - amount;
            }
            default -> throw new BusinessException("未知资金方向: " + direction);
        }
        long at = aa + af; // 总余额不变式

        // 3. 写流水（append-only），唯一索引 request_id 兜底幂等
        Ledger ledger = new Ledger()
                .setRequestId(requestId)
                .setUserId(acct.getUserId())
                .setAccountId(acct.getId())
                .setCoinId(acct.getCoinId())
                .setSymbol(acct.getSymbol())
                .setBizType(bizType)
                .setDirection(direction)
                .setAmount(amount)
                .setBeforeAvailable(ba)
                .setAfterAvailable(aa)
                .setBeforeFrozen(bf)
                .setAfterFrozen(af)
                .setRefNo(refNo)
                .setStatus(1)
                .setRemark(remark);
        try {
            this.save(ledger);
        } catch (DuplicateKeyException e) {
            // 唯一索引冲突 → 幂等命中，回读既有流水返回
            Ledger dup = getByRequestId(requestId);
            if (dup != null) {
                return toVO(dup);
            }
            throw new BusinessException("流水写入冲突，请重试");
        }

        // 4. 更新账户余额（行锁已串行化 + version 乐观锁兜底）
        LambdaUpdateWrapper<Account> uw = new LambdaUpdateWrapper<>();
        uw.eq(Account::getId, acct.getId())
                .set(Account::getAvailable, aa)
                .set(Account::getFrozen, af)
                .set(Account::getTotal, at)
                .set(Account::getVersion, acct.getVersion() + 1)
                .eq(Account::getVersion, acct.getVersion());
        boolean updated = accountService.update(uw);
        if (!updated) {
            // 乐观锁冲突 → 抛异常整体回滚（含上面已写的流水）
            throw new BusinessException(409, "余额更新冲突，请重试");
        }
        return toVO(ledger);
    }

    private Ledger getByRequestId(String requestId) {
        return this.getOne(new LambdaQueryWrapper<Ledger>()
                .eq(Ledger::getRequestId, requestId)
                .last("limit 1"), false);
    }

    private void requirePositive(Long amount) {
        if (amount == null || amount <= 0) {
            throw new BusinessException("金额必须大于0");
        }
    }

    private String nvl(String s, String def) {
        return (s == null || s.isBlank()) ? def : s;
    }

    @Override
    public LedgerVO toVO(Ledger l) {
        LedgerVO vo = new LedgerVO();
        vo.setId(l.getId());
        vo.setRequestId(l.getRequestId());
        vo.setUserId(l.getUserId());
        vo.setAccountId(l.getAccountId());
        vo.setCoinId(l.getCoinId());
        vo.setSymbol(l.getSymbol());
        vo.setBizType(l.getBizType());
        vo.setDirection(l.getDirection());
        vo.setAmount(l.getAmount());
        vo.setBeforeAvailable(l.getBeforeAvailable());
        vo.setAfterAvailable(l.getAfterAvailable());
        vo.setBeforeFrozen(l.getBeforeFrozen());
        vo.setAfterFrozen(l.getAfterFrozen());
        vo.setRefNo(l.getRefNo());
        vo.setStatus(l.getStatus());
        vo.setRemark(l.getRemark());
        vo.setCreateTime(l.getCreateTime());
        return vo;
    }
}
