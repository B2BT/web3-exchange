package com.web3.exchange.chain.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 提现申请请求。
 */
@Data
@Schema(description = "提现申请请求")
public class WithdrawApplyRequest implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    @NotNull
    @Schema(description = "用户ID", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long userId;
    @NotBlank
    @Schema(description = "币种符号", requiredMode = Schema.RequiredMode.REQUIRED)
    private String symbol;
    @NotBlank
    @Schema(description = "链编码", requiredMode = Schema.RequiredMode.REQUIRED)
    private String chainCode;
    @NotBlank
    @Schema(description = "提现目标地址", requiredMode = Schema.RequiredMode.REQUIRED)
    private String toAddress;
    @NotNull
    @Positive
    @Schema(description = "提现金额(最小单位)", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long amount;
    @Schema(description = "NFT代币ID(ERC-721/1155提现必填; ERC-20为空)")
    private String tokenId;
    @Schema(description = "反钓鱼码(已设置则须正确,风控校验)")
    private String antiPhishing;
}
