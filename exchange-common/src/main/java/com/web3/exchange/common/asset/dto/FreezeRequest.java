package com.web3.exchange.common.asset.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 冻结请求（order 服务下单时调用 asset 冻结）。
 * <p>requestId 由<b>调用方生成</b>且语义确定（同一订单重复发起时不变），asset 靠其幂等
 * 去重——重复请求直接返回首次结果，不重复扣减。amount 为最小单位整数。</p>
 */
@Data
@Schema(description = "冻结请求")
public class FreezeRequest implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    @NotBlank
    @Schema(description = "幂等号(order 下单号)", requiredMode = Schema.RequiredMode.REQUIRED)
    private String requestId;
    @NotNull
    @Schema(description = "用户ID", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long userId;
    @NotBlank
    @Schema(description = "币种符号", requiredMode = Schema.RequiredMode.REQUIRED)
    private String symbol;
    @NotNull
    @Schema(description = "冻结金额(最小单位)", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long amount;
    @NotBlank
    @Schema(description = "业务类型,建议固定 FREEZE", requiredMode = Schema.RequiredMode.REQUIRED)
    private String bizType;
    @Schema(description = "业务单号(orderId)")
    private String refNo;
    @Schema(description = "备注")
    private String remark;
}
