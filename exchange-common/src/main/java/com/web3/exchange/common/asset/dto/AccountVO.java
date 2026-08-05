package com.web3.exchange.common.asset.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 钱包账户视图（跨模块复用，供 Feign/外部返回钱包账户信息）。
 * <p><b>金额单位 = 该币种最小单位</b>（由 t_coin.decimals 定义），而非业务精度小数；
 * 保持三余额不变式 available + frozen == total。version 为乐观锁版本，仅供查询/校验。</p>
 */
@Data
@Schema(description = "钱包账户视图")
public class AccountVO implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(description = "账户ID")
    private Long accountId;
    @Schema(description = "用户ID")
    private Long userId;
    @Schema(description = "币种ID")
    private Long coinId;
    @Schema(description = "币种符号")
    private String symbol;
    @Schema(description = "可用余额(最小单位)")
    private Long available;
    @Schema(description = "冻结余额(最小单位)")
    private Long frozen;
    @Schema(description = "总余额(最小单位)")
    private Long total;
    @Schema(description = "账户状态:0=禁用,1=正常,2=冻结")
    private Integer status;
    @Schema(description = "乐观锁版本(查询用)")
    private Integer version;
}
