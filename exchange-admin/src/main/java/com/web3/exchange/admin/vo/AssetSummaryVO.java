package com.web3.exchange.admin.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 资产汇总 VO：某币种全站总可用/冻结余额（聚合 t_wallet_account）。
 */
@Data
@Schema(description = "资产汇总视图")
public class AssetSummaryVO implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(description = "币种符号")
    private String symbol;
    @Schema(description = "全站总可用余额(最小单位)")
    private Long totalAvailable;
    @Schema(description = "全站总冻结余额(最小单位)")
    private Long totalFrozen;
}
