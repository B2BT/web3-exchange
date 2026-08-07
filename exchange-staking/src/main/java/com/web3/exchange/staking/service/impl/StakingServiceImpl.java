package com.web3.exchange.staking.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.web3.exchange.common.asset.dto.FreezeRequest;
import com.web3.exchange.common.asset.dto.UnfreezeRequest;
import com.web3.exchange.common.exception.BusinessException;
import com.web3.exchange.common.exception.NotFoundException;
import com.web3.exchange.common.model.Result;
import com.web3.exchange.staking.dto.StakingRequest;
import com.web3.exchange.staking.entity.StakingInterest;
import com.web3.exchange.staking.entity.StakingPosition;
import com.web3.exchange.staking.entity.StakingProduct;
import com.web3.exchange.staking.feign.AssetClient;
import com.web3.exchange.staking.mapper.StakingInterestMapper;
import com.web3.exchange.staking.mapper.StakingPositionMapper;
import com.web3.exchange.staking.mapper.StakingProductMapper;
import com.web3.exchange.staking.service.StakingService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 质押服务实现。
 * <p>质押：asset freeze 锁现货，本金入持仓；赎回：到期解锁本金（收益已每日 credit）。
 * 金额 Long 最小单位，requestId 幂等。</p>
 */
@Slf4j
@Service
public class StakingServiceImpl extends ServiceImpl<StakingPositionMapper, StakingPosition>
        implements StakingService {

    private final StakingProductMapper productMapper;
    private final StakingInterestMapper interestMapper;
    private final AssetClient assetClient;

    public StakingServiceImpl(StakingProductMapper productMapper,
                              StakingInterestMapper interestMapper,
                              AssetClient assetClient) {
        this.productMapper = productMapper;
        this.interestMapper = interestMapper;
        this.assetClient = assetClient;
    }

    @Override
    public List<StakingProduct> listProducts() {
        return productMapper.selectList(new LambdaQueryWrapper<StakingProduct>()
                .eq(StakingProduct::getStatus, 1)
                .orderByAsc(StakingProduct::getId));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public StakingPosition stake(StakingRequest req) {
        StakingProduct product = requireProduct(req.getProductCode());
        if (req.getAmount() == null || req.getAmount() <= 0) {
            throw new BusinessException("质押金额必须大于 0");
        }
        if (req.getAmount() < product.getMinAmount()) {
            throw new BusinessException("低于最小质押额 " + product.getMinAmount());
        }
        long id = IdWorker.getId();
        // 1. 锁定现货
        FreezeRequest fr = new FreezeRequest();
        fr.setRequestId("STK_STAKE:" + id);
        fr.setUserId(req.getUserId());
        fr.setSymbol(product.getSymbol());
        fr.setAmount(req.getAmount());
        fr.setBizType("FREEZE");
        fr.setRefNo(req.getProductCode());
        fr.setRemark("质押:" + product.getName());
        Result<?> r = assetClient.freeze(fr);
        if (r == null || !r.isSuccess()) {
            throw new BusinessException("质押失败（现货冻结失败）: " + (r == null ? "null" : r.getMessage()));
        }
        // 2. 建持仓
        LocalDateTime now = LocalDateTime.now();
        StakingPosition pos = new StakingPosition()
                .setUserId(req.getUserId())
                .setProductCode(product.getProductCode())
                .setSymbol(product.getSymbol())
                .setAmount(req.getAmount())
                .setAccruedInterest(0L)
                .setTotalInterest(0L)
                .setStatus(0)
                .setStartTime(now);
        if (product.getType() == 1 && product.getLockDays() != null && product.getLockDays() > 0) {
            pos.setLockEndTime(now.plusDays(product.getLockDays()));
        }
        pos.setId(id);
        this.save(pos);
        log.info("[staking] 质押 user={} product={} amount={}", req.getUserId(), product.getProductCode(), req.getAmount());
        return pos;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public StakingPosition redeem(StakingRequest req) {
        StakingPosition pos = this.getOne(new LambdaQueryWrapper<StakingPosition>()
                .eq(StakingPosition::getUserId, req.getUserId())
                .eq(StakingPosition::getProductCode, req.getProductCode())
                .eq(StakingPosition::getStatus, 0)
                .orderByAsc(StakingPosition::getStartTime)
                .last("limit 1"), false);
        if (pos == null) {
            throw new NotFoundException("无质押中的持仓");
        }
        StakingProduct product = requireProduct(req.getProductCode());
        // 锁仓产品须到期
        if (product.getType() == 1 && pos.getLockEndTime() != null
                && LocalDateTime.now().isBefore(pos.getLockEndTime())) {
            throw new BusinessException("锁仓未到期，无法赎回");
        }
        long id = IdWorker.getId();
        // 退回本金（收益已每日 credit）
        UnfreezeRequest ur = new UnfreezeRequest();
        ur.setRequestId("STK_REDEEM:" + id);
        ur.setUserId(req.getUserId());
        ur.setSymbol(product.getSymbol());
        ur.setAmount(pos.getAmount());
        ur.setBizType("UNFREEZE");
        ur.setRemark("质押赎回:" + product.getName());
        Result<?> r = assetClient.unfreeze(ur);
        if (r == null || !r.isSuccess()) {
            throw new BusinessException("赎回失败（现货解冻失败）: " + (r == null ? "null" : r.getMessage()));
        }
        pos.setStatus(1);
        pos.setRedeemTime(LocalDateTime.now());
        this.updateById(pos);
        log.info("[staking] 赎回 user={} product={} amount={}", req.getUserId(), product.getProductCode(), pos.getAmount());
        return pos;
    }

    @Override
    public List<StakingPosition> listPositions(Long userId) {
        return this.list(new LambdaQueryWrapper<StakingPosition>()
                .eq(StakingPosition::getUserId, userId)
                .orderByDesc(StakingPosition::getId));
    }

    @Override
    public Page<StakingInterest> pageInterests(Long userId, int page, int size) {
        return interestMapper.selectPage(new Page<>(Math.max(page, 1), Math.min(Math.max(size, 1), 100)),
                new LambdaQueryWrapper<StakingInterest>()
                        .eq(StakingInterest::getUserId, userId)
                        .orderByDesc(StakingInterest::getId));
    }

    private StakingProduct requireProduct(String productCode) {
        StakingProduct p = productMapper.selectOne(new LambdaQueryWrapper<StakingProduct>()
                .eq(StakingProduct::getProductCode, productCode)
                .eq(StakingProduct::getStatus, 1)
                .last("limit 1"), false);
        if (p == null) {
            throw new NotFoundException("产品不存在或已下架");
        }
        return p;
    }
}
