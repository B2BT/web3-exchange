package com.web3.exchange.common.asset.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 充值入账请求（chain → asset）
 */
@Data
@Schema(description = "充值入账请求")
public class CreditRequest implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    @NotBlank
    @Schema(description = "幂等号(可由 depositId 派生)", requiredMode = Schema.RequiredMode.REQUIRED)
    private String requestId;
    @NotNull
    @Schema(description = "用户ID", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long userId;
    @NotBlank
    @Schema(description = "币种符号", requiredMode = Schema.RequiredMode.REQUIRED)
    private String symbol;
    @NotNull
    @Schema(description = "入账金额(最小单位,已扣链上手续费)", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long amount;
    @NotBlank
    @Schema(description = "业务类型,建议固定 DEPOSIT", requiredMode = Schema.RequiredMode.REQUIRED)
    private String bizType;
    @Schema(description = "业务单号(depositId)")
    private String refNo;
    @Schema(description = "备注")
    private String remark;
}
