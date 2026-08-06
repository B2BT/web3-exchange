package com.web3.exchange.order.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 订单视图（对外返回）。金额一律最小单位 long。
 */
@Data
@Schema(description = "订单视图")
public class OrderVO implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    private Long id;
    private String orderNo;
    private String clientOid;
    private Long userId;
    private String symbol;
    private String baseCoin;
    private String quoteCoin;
    private Integer side;
    private Integer orderType;
    private Long price;
    private Long quantity;
    private Long quoteAmount;
    private Long remaining;
    private Long filledAmount;
    private Long filledQuoteAmount;
    private Long avgPrice;
    private Integer tradeCount;
    private Long fee;
    private Long freezeQuoteAmount;
    private Long freezeBaseAmount;
    private Integer status;
    private String remark;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime cancelTime;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime filledTime;
}
