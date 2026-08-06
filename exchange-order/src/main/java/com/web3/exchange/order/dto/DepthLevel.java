package com.web3.exchange.order.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 单个价格档（深度盘口一行）。
 * <p>price = 计价币最小单位（Long），quantity = 基础币最小单位（Long），
 * 均为金额/数量，序列化保留 number，不做除法。</p>
 */
@Data
@Schema(description = "深度盘口单档")
public class DepthLevel implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(description = "价格（计价币最小单位）")
    private Long price;

    @Schema(description = "数量（基础币最小单位）")
    private Long quantity;

    public DepthLevel() {
    }

    public DepthLevel(Long price, Long quantity) {
        this.price = price;
        this.quantity = quantity;
    }
}
