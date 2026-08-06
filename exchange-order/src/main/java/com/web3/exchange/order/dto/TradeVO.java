package com.web3.exchange.order.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 成交视图（对外返回）。金额一律最小单位 long。
 */
@Data
@Schema(description = "成交视图")
public class TradeVO implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    private Long id;
    private String tradeNo;
    private String symbol;
    private Long price;
    private Long quantity;
    private Long quoteAmount;
    private String takerOrderNo;
    private String makerOrderNo;
    private Long takerOrderId;
    private Long makerOrderId;
    private Long takerUserId;
    private Long makerUserId;
    private Integer takerSide;
    private Long buyUserId;
    private Long sellUserId;
    private Integer settleStatus;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime tradeTime;
}
