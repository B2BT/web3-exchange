package com.web3.exchange.admin.service;

import com.web3.exchange.admin.vo.AssetSummaryVO;

import java.util.List;

/**
 * 后台资产汇总服务：各币种全站总可用/冻结。
 */
public interface AdminAssetService {

    /** 聚合 t_wallet_account，按币种汇总。 */
    List<AssetSummaryVO> summary();
}
