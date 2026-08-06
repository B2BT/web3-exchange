package com.web3.exchange.order.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 下单请求（对外 REST /api/order/place）。
 * <p>金额一律最小单位 long。限价单：price/quantity 必填；市价买单：quoteAmount 必填；市价卖单：quantity 必填。</p>
 */
@Data
@Schema(description = "下单请求")
public class PlaceOrderRequest implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    @NotNull
    @Schema(description = "用户ID")
    private Long userId;
    @NotNull
    @Schema(description = "交易对，如 BTC/USDT")
    private String symbol;
    @NotNull
    @Schema(description = "方向：1=BUY 2=SELL")
    private Integer side;
    @NotNull
    @Schema(description = "类型：1=GTC限价 2=MARKET市价")
    private Integer orderType;
    @Schema(description = "限价（计价币最小单位；市价为0）")
    private Long price;
    @Schema(description = "数量（基础币最小单位；市价买单为0）")
    private Long quantity;
    @Schema(description = "市价买单预算额（计价币最小单位）")
    private Long quoteAmount;
    @Schema(description = "时间策略：0=GTC 1=IOC 2=FOK 3=PostOnly（默认0=GTC）")
    private Integer timeInForce;
    @Schema(description = "条件单类型：0=非条件单 1=止盈 2=止损（批次B生效）")
    private Integer triggerType;
    @Schema(description = "触发价（计价币最小单位；条件单必填）")
    private Long triggerPrice;
    @Schema(description = "OCO 关联组号（批次B生效）")
    private String ocoGroup;
    @Schema(description = "客户端订单号（客户端幂等）")
    private String clientOid;
}
