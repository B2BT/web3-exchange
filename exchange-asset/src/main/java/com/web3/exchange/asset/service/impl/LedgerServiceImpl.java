package com.web3.exchange.asset.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.web3.exchange.asset.entity.Account;
import com.web3.exchange.asset.entity.Ledger;
import com.web3.exchange.asset.mapper.LedgerMapper;
import com.web3.exchange.asset.mq.producer.AssetEventProducer;
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

/**
 * 资金流水服务实现——<b>资产域资金变动的唯一入口</b>。
 * <p>
 * 冻结/解冻/过户/充值入账都收敛到 {@link #doChange} 统一完成，保证「写流水 + 更新余额」
 * 在同一本地事务内原子执行，并叠加行锁、乐观锁、幂等三重保障，确保资金
 * 不多、不少、不重复扣减（资金安全的核心）。
 * </p>
 * <p>
 * 对外提供的资金操作（freeze/unfreeze/transfer/credit）均要求调用方先经
 * {@code AccountService.lockByUserAndSymbol} 以 SELECT ... FOR UPDATE 行锁锁定账户，
 * 将同一账户的并发资金操作串行化；transfer 会按 userId 升序锁定双方账户以防死锁。
 * </p>
 */
@Service
public class LedgerServiceImpl extends ServiceImpl<LedgerMapper, Ledger> implements LedgerService {

    private final AccountService accountService;

    private final AssetEventProducer assetEventProducer;

    public LedgerServiceImpl(AccountService accountService, AssetEventProducer assetEventProducer) {
        this.accountService = accountService;
        this.assetEventProducer = assetEventProducer;
    }

    // ==================== 冻结 ====================

    /**
     * 冻结：把用户可用余额转入冻结余额（下单锁仓，防止「一边花一边用」）。
     * 先以行锁锁定账户，再交由 {@link #doChange} 按方向 FROZEN 执行，
     * 可用余额不足则抛 409；同一 requestId 重复请求返回首次结果。
     */
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

    /**
     * 解冻：把冻结余额释放回可用余额（撤单时退回锁仓）。
     * 先以行锁锁定账户，再交由 {@link #doChange} 按方向 UNFROZEN 执行，
     * 冻结余额不足则抛 409；同一 requestId 重复请求返回首次结果。
     */
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

    /**
     * 过户（成交结算）：把转出方的<b>冻结余额</b>划给转入方的<b>可用余额</b>。
     * 整个流程在同一事务内完成，分两步各写一条流水：
     * from 按 FROZEN_OUT 冻结减少（写 TRANSFER_OUT），to 按 IN 可用增加（写 TRANSFER_IN），
     * 任一步失败整体回滚，保证双边账实一致。
     * <p>并发/幂等设计：先按 userId 升序加锁双方账户，避免并发互转死锁；
     * 以「requestId + {@code _OUT}」作为转出流水的幂等键，命中即视为整笔过户已完成并原样返回。</p>
     */
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

    /**
     * 充值入账：链上到账确认后给用户可用余额加钱。
     * 由 chain 服务扫描确认后调用；先以行锁锁定账户，再交由 {@link #doChange}
     * 按方向 IN（写 DEPOSIT 流水）执行；requestId 由 depositId 派生，
     * 唯一索引兜底防止同一笔充值重复入账。
     */
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

    /**
     * 分页查询资产流水（供对账/审计）。支持按账户过滤，size 限界 1~100。
     */
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

    /**
     * 资金变动核心方法——所有资金操作（冻结/解冻/过户/入账）最终都汇聚到这里，
     * 保证「写流水 + 更新余额」原子完成，并满足幂等与并发安全。完整流程：
     * <ol>
     *   <li><b>幂等回读</b>：先按 requestId 查流水，已存在则直接返回首次结果（不重复执行）；</li>
     *   <li><b>算 before/after</b>：按资金方向 {@code Direction} 计算变动前后余额，
     *       余额不足抛 409 业务错误；始终维护不变式 available + frozen == total；</li>
     *   <li><b>写流水</b>：向 t_asset_ledger 追加一条不可变流水（append-only），
     *       request_id 唯一索引兜底幂等——若并发撞唯一索引则回读既有流水返回；</li>
     *   <li><b>更新余额</b>：以乐观锁（version）条件更新账户行，冲突则抛异常整体回滚
     *       （连同已写入的流水一起回滚），保证账实一致。</li>
     * </ol>
     * <p>
     * 前提：入参 account 必须已在<b>当前事务内</b>被 SELECT ... FOR UPDATE 行锁锁定
     * （由调用方在进入本方法前完成），从而将同一账户的资金操作串行化，杜绝并发超扣。
     * </p>
     *
     * @param requestId 幂等请求号（调用方生成，同一业务重复发起时保持不变）
     * @param acct      已被行锁锁定的账户
     * @param bizType   业务类型（见 {@link BizType}）
     * @param direction 资金方向（见 {@link Direction}）
     * @param amount    变动金额（最小单位，恒正）
     * @param refNo     业务单号（orderId/withdrawId/depositId）
     * @param remark    备注
     * @return 本次资金变动的流水视图
     */
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

        // ===== 资金变动成功：发送 ASSET-CHANGE 事件（骨架，批次B）=====
        // 仅作事件通知补充，不参与资金主流程；发送失败由 producer 内部降级为仅记录日志，
        // 绝不抛异常回滚资金事务。注意：本方法处于事务内，发送发生在提交前；
        // 如需严格「提交后才可被消费」，后续可升级为 RocketMQ 事务消息（见 docs/mq-topics.md）。
        assetEventProducer.publishAssetChange(toVO(ledger));

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
