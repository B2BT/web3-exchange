package com.web3.exchange.order.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 撤单请求。
 */
@Data
@Schema(description = "撤单请求")
public class CancelOrderRequest implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    @NotNull
    @Schema(description = "用户ID")
    private Long userId;
    @NotBlank
    @Schema(description = "订单号")
    private String orderNo;
}
