package com.web3.exchange.common.asset.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 过户请求（成交结算：买卖双方同币种划转）
 */
@Data
@Schema(description = "过户请求")
public class TransferRequest implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    @NotBlank
    @Schema(description = "幂等号(t_trade.id)", requiredMode = Schema.RequiredMode.REQUIRED)
    private String requestId;
    @NotNull
    @Schema(description = "转出方用户ID(冻结余额转出)", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long fromUserId;
    @NotNull
    @Schema(description = "转入方用户ID", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long toUserId;
    @NotBlank
    @Schema(description = "过户币种", requiredMode = Schema.RequiredMode.REQUIRED)
    private String symbol;
    @NotNull
    @Schema(description = "过户金额(最小单位)", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long amount;
    @NotBlank
    @Schema(description = "业务类型:TRANSFER_OUT/TRANSFER_IN(由服务生成)", requiredMode = Schema.RequiredMode.REQUIRED)
    private String bizType;
    @Schema(description = "业务单号(orderId)")
    private String refNo;
    @Schema(description = "备注")
    private String remark;
}
