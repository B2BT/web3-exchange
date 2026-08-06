package com.web3.exchange.admin.mapper;

import com.web3.exchange.admin.vo.AssetSummaryVO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 资产账户聚合 Mapper——对既有 t_wallet_account 做全站按币种汇总（无需新表）。
 */
@Mapper
public interface AssetAccountMapper {

    /**
     * 各币种全站总可用/冻结余额。
     * 表为 t_wallet_account（合约文档原写 t_asset_account，实库为 t_wallet_account，见 sql/asset.sql）。
     */
    @Select("SELECT symbol, SUM(available) AS totalAvailable, SUM(frozen) AS totalFrozen " +
            "FROM t_wallet_account WHERE is_deleted = 0 GROUP BY symbol ORDER BY symbol")
    List<AssetSummaryVO> sumBySymbol();
}
