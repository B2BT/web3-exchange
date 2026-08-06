package com.web3.exchange.admin.service.impl;

import com.web3.exchange.admin.mapper.AssetAccountMapper;
import com.web3.exchange.admin.service.AdminAssetService;
import com.web3.exchange.admin.vo.AssetSummaryVO;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 后台资产汇总服务实现：聚合 t_wallet_account，按币种输出全站总可用/冻结。
 */
@Service
public class AdminAssetServiceImpl implements AdminAssetService {

    private final AssetAccountMapper assetAccountMapper;

    public AdminAssetServiceImpl(AssetAccountMapper assetAccountMapper) {
        this.assetAccountMapper = assetAccountMapper;
    }

    @Override
    public List<AssetSummaryVO> summary() {
        return assetAccountMapper.sumBySymbol();
    }
}
