package com.web3.exchange.common.asset.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 解冻请求（order 服务撤单时调用 asset 解冻）。
 * <p>requestId 由<b>调用方生成</b>且语义确定，asset 靠其幂等去重；amount 为最小单位整数，
 * 不得超过当前冻结余额，否则资产服务抛 409。</p>
 */
@Data
@Schema(description = "解冻请求")
public class UnfreezeRequest implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    @NotBlank
    @Schema(description = "幂等号(order 撤单号)", requiredMode = Schema.RequiredMode.REQUIRED)
    private String requestId;
    @NotNull
    @Schema(description = "用户ID", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long userId;
    @NotBlank
    @Schema(description = "币种符号", requiredMode = Schema.RequiredMode.REQUIRED)
    private String symbol;
    @NotNull
    @Schema(description = "解冻金额(最小单位,<=冻结余额)", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long amount;
    @NotBlank
    @Schema(description = "业务类型,建议固定 UNFREEZE", requiredMode = Schema.RequiredMode.REQUIRED)
    private String bizType;
    @Schema(description = "业务单号")
    private String refNo;
    @Schema(description = "备注")
    private String remark;
}
