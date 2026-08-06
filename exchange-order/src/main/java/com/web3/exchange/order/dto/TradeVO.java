package com.web3.exchange.order.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 成交视图（对外返回）。金额一律最小单位 long。
 * <p>雪花 Long id（&gt;2^53 前端 JS 精度丢失）序列化为字符串；price/quantity/quoteAmount 等金额字段保留 number。</p>
 */
@Data
@Schema(description = "成交视图")
public class TradeVO implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;
    private String tradeNo;
    private String symbol;
    private Long price;
    private Long quantity;
    private Long quoteAmount;
    private String takerOrderNo;
    private String makerOrderNo;
    @JsonSerialize(using = ToStringSerializer.class)
    private Long takerOrderId;
    @JsonSerialize(using = ToStringSerializer.class)
    private Long makerOrderId;
    @JsonSerialize(using = ToStringSerializer.class)
    private Long takerUserId;
    @JsonSerialize(using = ToStringSerializer.class)
    private Long makerUserId;
    private Integer takerSide;
    @JsonSerialize(using = ToStringSerializer.class)
    private Long buyUserId;
    @JsonSerialize(using = ToStringSerializer.class)
    private Long sellUserId;
    private Integer settleStatus;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime tradeTime;
}
