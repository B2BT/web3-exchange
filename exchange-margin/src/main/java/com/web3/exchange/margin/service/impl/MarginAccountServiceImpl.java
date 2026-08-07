package com.web3.exchange.margin.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.web3.exchange.common.asset.dto.FreezeRequest;
import com.web3.exchange.common.asset.dto.UnfreezeRequest;
import com.web3.exchange.common.exception.BusinessException;
import com.web3.exchange.common.exception.NotFoundException;
import com.web3.exchange.common.model.Result;
import com.web3.exchange.margin.dto.MarginAccountVO;
import com.web3.exchange.margin.dto.MarginBorrowRequest;
import com.web3.exchange.margin.dto.MarginTransferRequest;
import com.web3.exchange.margin.entity.MarginAccount;
import com.web3.exchange.margin.entity.MarginInterestRate;
import com.web3.exchange.margin.entity.MarginLoan;
import com.web3.exchange.margin.feign.AssetClient;
import com.web3.exchange.margin.mapper.MarginAccountMapper;
import com.web3.exchange.margin.mapper.MarginInterestRateMapper;
import com.web3.exchange.margin.mapper.MarginLoanMapper;
import com.web3.exchange.margin.service.MarginAccountService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * 杠杆账户服务实现。
 * <p>资金铁律：杠杆是独立资金池，抵押入金/出金与现货账户通过 asset freeze/unfreeze 衔接；
 * 借币/还币仅改本模块表。金额 Long 最小单位，requestId 幂等。</p>
 */
@Slf4j
@Service
public class MarginAccountServiceImpl extends ServiceImpl<MarginAccountMapper, MarginAccount>
        implements MarginAccountService {

    private final MarginLoanMapper loanMapper;
    private final MarginInterestRateMapper rateMapper;
    private final AssetClient assetClient;

    public MarginAccountServiceImpl(MarginLoanMapper loanMapper,
                                    MarginInterestRateMapper rateMapper,
                                    AssetClient assetClient) {
        this.loanMapper = loanMapper;
        this.rateMapper = rateMapper;
        this.assetClient = assetClient;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public MarginAccountVO open(Long userId, String symbol) {
        MarginAccount exist = getByUserSymbol(userId, symbol);
        if (exist != null) {
            return toVO(exist);
        }
        MarginAccount acc = new MarginAccount()
                .setUserId(userId).setSymbol(symbol)
                .setCollateral(0L).setBorrowed(0L).setInterestAccrued(0L)
                .setStatus(1);
        acc.setId(IdWorker.getId());
        try {
            this.save(acc);
            log.info("[margin] 开户 user={} symbol={}", userId, symbol);
        } catch (DuplicateKeyException e) {
            MarginAccount again = getByUserSymbol(userId, symbol);
            if (again != null) return toVO(again);
            throw e;
        }
        return toVO(acc);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public MarginAccountVO transferIn(MarginTransferRequest req) {
        MarginAccount acc = require(userIdOf(req), req.getSymbol());
        // 现货 available → 杠杆 collateral：先冻结现货（锁资金），再增加抵押
        FreezeRequest fr = new FreezeRequest();
        fr.setRequestId("MG_IN:" + IdWorker.getId());
        fr.setUserId(req.getUserId());
        fr.setSymbol(req.getSymbol());
        fr.setAmount(req.getAmount());
        fr.setBizType("FREEZE");
        fr.setRemark("杠杆抵押入金");
        Result<?> r = assetClient.freeze(fr);
        if (r == null || !r.isSuccess()) {
            throw new BusinessException("抵押入金失败（现货冻结失败）: " + (r == null ? "null" : r.getMessage()));
        }
        acc.setCollateral(acc.getCollateral() + req.getAmount());
        this.updateById(acc);
        log.info("[margin] 抵押入金 user={} symbol={} amount={} collateral={}",
                req.getUserId(), req.getSymbol(), req.getAmount(), acc.getCollateral());
        return toVO(acc);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public MarginAccountVO transferOut(MarginTransferRequest req) {
        MarginAccount acc = require(req.getUserId(), req.getSymbol());
        if (req.getAmount() > acc.getCollateral()) {
            throw new BusinessException("抵押不足，无法出金");
        }
        // 校验出金后仍有富余抵押（保留已借负债的维持保证金）
        if (acc.getBorrowed() > 0) {
            long remain = acc.getCollateral() - req.getAmount();
            int maint = maintenanceRatioOf(req.getSymbol());
            if (remain * 100 < acc.getBorrowed() * maint) {
                throw new BusinessException("出金后抵押将低于维持保证金率");
            }
        }
        UnfreezeRequest ur = new UnfreezeRequest();
        ur.setRequestId("MG_OUT:" + IdWorker.getId());
        ur.setUserId(req.getUserId());
        ur.setSymbol(req.getSymbol());
        ur.setAmount(req.getAmount());
        ur.setBizType("UNFREEZE");
        ur.setRemark("杠杆抵押出金");
        Result<?> r = assetClient.unfreeze(ur);
        if (r == null || !r.isSuccess()) {
            throw new BusinessException("抵押出金失败（现货解冻失败）: " + (r == null ? "null" : r.getMessage()));
        }
        acc.setCollateral(acc.getCollateral() - req.getAmount());
        this.updateById(acc);
        log.info("[margin] 抵押出金 user={} symbol={} amount={} collateral={}",
                req.getUserId(), req.getSymbol(), req.getAmount(), acc.getCollateral());
        return toVO(acc);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public MarginAccountVO borrow(MarginBorrowRequest req) {
        MarginAccount acc = require(req.getUserId(), req.getSymbol());
        if (req.getAmount() <= 0) {
            throw new BusinessException("借入金额必须大于 0");
        }
        MarginInterestRate rate = rateOf(req.getSymbol());
        // 可借上限 = 抵押*100 / 维持保证金率 - 已借
        long maxBorrow = maxBorrow(acc, rate.getMaintenanceRatio());
        if (req.getAmount() > maxBorrow) {
            throw new BusinessException("超出可借额度，当前最多可借 " + maxBorrow);
        }
        long loanId = IdWorker.getId();
        MarginLoan loan = new MarginLoan()
                .setUserId(req.getUserId())
                .setSymbol(req.getSymbol())
                .setRequestId("MG_BORROW:" + loanId)
                .setAmount(req.getAmount())
                .setRateDaily((long) rate.getRateDailyBp())
                .setPrincipalRemain(req.getAmount())
                .setInterestAccrued(0L)
                .setStatus(0)
                .setOpenTime(LocalDateTime.now());
        loan.setId(loanId);
        loanMapper.insert(loan);

        acc.setBorrowed(acc.getBorrowed() + req.getAmount());
        this.updateById(acc);
        log.info("[margin] 借币 user={} symbol={} amount={} borrowed={}",
                req.getUserId(), req.getSymbol(), req.getAmount(), acc.getBorrowed());
        return toVO(acc);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public MarginAccountVO repay(MarginBorrowRequest req) {
        MarginAccount acc = require(req.getUserId(), req.getSymbol());
        if (req.getAmount() <= 0) {
            throw new BusinessException("还币金额必须大于 0");
        }
        // 取用户该币种未还借单（按时间，先借先还）
        MarginLoan loan = loanMapper.selectOne(new LambdaQueryWrapper<MarginLoan>()
                .eq(MarginLoan::getUserId, req.getUserId())
                .eq(MarginLoan::getSymbol, req.getSymbol())
                .eq(MarginLoan::getStatus, 0)
                .orderByAsc(MarginLoan::getOpenTime)
                .last("limit 1"));
        if (loan == null) {
            throw new BusinessException("无未还借单");
        }
        long totalOwe = loan.getPrincipalRemain() + loan.getInterestAccrued();
        long pay = Math.min(req.getAmount(), totalOwe);
        // 本金 + 利息按比例或优先本金（MVP：优先本金，利息次之）
        long payPrincipal = Math.min(pay, loan.getPrincipalRemain());
        long payInterest = pay - payPrincipal;
        loan.setPrincipalRemain(loan.getPrincipalRemain() - payPrincipal);
        loan.setInterestAccrued(loan.getInterestAccrued() - payInterest);
        if (loan.getPrincipalRemain() <= 0) {
            loan.setStatus(1);
            loan.setRepayTime(LocalDateTime.now());
            loan.setInterestAccrued(0L);
        }
        loanMapper.updateById(loan);

        acc.setBorrowed(Math.max(0, acc.getBorrowed() - payPrincipal));
        acc.setInterestAccrued(Math.max(0, acc.getInterestAccrued() - payInterest));
        this.updateById(acc);
        log.info("[margin] 还币 user={} symbol={} pay={} principal={} interest={}",
                req.getUserId(), req.getSymbol(), pay, payPrincipal, payInterest);
        return toVO(acc);
    }

    @Override
    public MarginAccountVO getAccount(Long userId, String symbol) {
        return toVO(require(userId, symbol));
    }

    @Override
    public Page<MarginLoan> pageLoans(Long userId, int page, int size) {
        return loanMapper.selectPage(new Page<>(Math.max(page, 1), Math.min(Math.max(size, 1), 100)),
                new LambdaQueryWrapper<MarginLoan>()
                        .eq(MarginLoan::getUserId, userId)
                        .orderByDesc(MarginLoan::getId));
    }

    // ==================== 内部工具 ====================

    private Long userIdOf(MarginTransferRequest req) {
        return req.getUserId();
    }

    private MarginAccount require(Long userId, String symbol) {
        MarginAccount acc = getByUserSymbol(userId, symbol);
        if (acc == null) {
            throw new NotFoundException("杠杆账户不存在，请先开户");
        }
        return acc;
    }

    private MarginAccount getByUserSymbol(Long userId, String symbol) {
        return getOne(new LambdaQueryWrapper<MarginAccount>()
                .eq(MarginAccount::getUserId, userId)
                .eq(MarginAccount::getSymbol, symbol)
                .last("limit 1"), false);
    }

    private MarginInterestRate rateOf(String symbol) {
        MarginInterestRate r = rateMapper.selectOne(new LambdaQueryWrapper<MarginInterestRate>()
                .eq(MarginInterestRate::getSymbol, symbol)
                .last("limit 1"), false);
        if (r == null) {
            throw new BusinessException("该币种未配置杠杆利率");
        }
        return r;
    }

    private int maintenanceRatioOf(String symbol) {
        return rateOf(symbol).getMaintenanceRatio();
    }

    private long maxBorrow(MarginAccount acc, int maintRatio) {
        if (maintRatio <= 0) return 0;
        long byCollateral = acc.getCollateral() * 100L / maintRatio;
        return Math.max(0, byCollateral - acc.getBorrowed() - acc.getInterestAccrued());
    }

    private MarginAccountVO toVO(MarginAccount acc) {
        MarginAccountVO vo = new MarginAccountVO();
        vo.setId(acc.getId());
        vo.setUserId(acc.getUserId());
        vo.setSymbol(acc.getSymbol());
        vo.setCollateral(acc.getCollateral());
        vo.setBorrowed(acc.getBorrowed());
        vo.setInterestAccrued(acc.getInterestAccrued());
        if (acc.getBorrowed() > 0) {
            vo.setRiskRate(acc.getCollateral() * 100L / acc.getBorrowed());
        }
        vo.setStatus(acc.getStatus());
        return vo;
    }
}
