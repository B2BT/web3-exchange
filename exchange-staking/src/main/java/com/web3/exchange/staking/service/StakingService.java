package com.web3.exchange.staking.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.IService;
import com.web3.exchange.staking.dto.StakingRequest;
import com.web3.exchange.staking.entity.StakingInterest;
import com.web3.exchange.staking.entity.StakingPosition;
import com.web3.exchange.staking.entity.StakingProduct;

import java.util.List;

/**
 * 质押服务。
 */
public interface StakingService extends IService<StakingPosition> {

    /** 产品列表（上架） */
    List<StakingProduct> listProducts();

    /** 质押：asset freeze 锁现货 */
    StakingPosition stake(StakingRequest req);

    /** 赎回：到期解锁，本金退回（收益已每日 credit） */
    StakingPosition redeem(StakingRequest req);

    /** 我的持仓 */
    List<StakingPosition> listPositions(Long userId);

    /** 收益流水分页 */
    Page<StakingInterest> pageInterests(Long userId, int page, int size);
}
