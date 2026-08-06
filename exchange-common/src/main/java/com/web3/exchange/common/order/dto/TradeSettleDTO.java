package com.web3.exchange.common.order.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;

/**
 * 撮合成交结算指令（ORDER-TRADE 事务消息体）——order 生产、asset 消费。
 * <p>
 * 该 DTO 放在 {@code exchange-common} 供 order（生产者）与 asset（消费者）跨模块复用，
 * 避免 asset 反向依赖 order。消息体序列化为 JSON，{@code KEYS = tradeNo} 作为消费幂等键。
 * </p>
 * <p>
 * 币种流向固定（见 docs/order-domain.md §6.2）：计价币过户 Q（买方→卖方，金额=quoteAmount）
 * + 基础币过户 B（卖方→买方，金额=quantity）；过户幂等号 = {@code tradeNo:Q} / {@code tradeNo:B}。
 * </p>
 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
@Schema(description = "撮合成交结算指令（ORDER-TRADE 消息体）")
public class TradeSettleDTO implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    /** 成交单号（全局唯一，消息 KEYS / 消费幂等键） */
    @Schema(description = "成交单号")
    private String tradeNo;

    /** 交易对，如 BTC/USDT */
    @Schema(description = "交易对")
    private String symbol;

    /** 基础币（被交易资产，如 BTC） */
    @Schema(description = "基础币")
    private String baseCoin;

    /** 计价币（用于标价，如 USDT） */
    @Schema(description = "计价币")
    private String quoteCoin;

    /** 成交价（计价币最小单位） */
    @Schema(description = "成交价")
    private Long price;

    /** 成交量（基础币最小单位） */
    @Schema(description = "成交量")
    private Long quantity;

    /** 成交名义值 = price×quantity（计价币最小单位） */
    @Schema(description = "成交名义值")
    private Long quoteAmount;

    /** 买方用户ID */
    @Schema(description = "买方用户ID")
    private Long buyUserId;

    /** 卖方用户ID */
    @Schema(description = "卖方用户ID")
    private Long sellUserId;

    /** 吃单订单号 */
    @Schema(description = "吃单订单号")
    private String takerOrderNo;

    /** 挂单订单号 */
    @Schema(description = "挂单订单号")
    private String makerOrderNo;
}
