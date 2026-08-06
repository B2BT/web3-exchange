package com.web3.exchange.admin.vo;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 管理平台全站订单 VO（与 order 域 OrderVO 同款字段）。
 * <p>金额一律 Long 最小单位；雪花 id 序列化为 String 防 JS 精度丢失。</p>
 */
@Data
@Schema(description = "后台订单视图")
public class OrderVO implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(description = "订单ID")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;
    @Schema(description = "业务订单号")
    private String orderNo;
    @Schema(description = "客户端订单号")
    private String clientOid;
    @Schema(description = "用户ID")
    @JsonSerialize(using = ToStringSerializer.class)
    private Long userId;
    @Schema(description = "交易对")
    private String symbol;
    @Schema(description = "基础币")
    private String baseCoin;
    @Schema(description = "计价币")
    private String quoteCoin;
    @Schema(description = "方向:1=BUY 2=SELL")
    private Integer side;
    @Schema(description = "类型:1=GTC 2=MARKET")
    private Integer orderType;
    @Schema(description = "时间策略")
    private Integer timeInForce;
    @Schema(description = "条件单类型")
    private Integer triggerType;
    @Schema(description = "触发价")
    private Long triggerPrice;
    @Schema(description = "触发状态")
    private Integer triggerStatus;
    @Schema(description = "OCO组号")
    private String ocoGroup;
    @Schema(description = "限价")
    private Long price;
    @Schema(description = "下单数量")
    private Long quantity;
    @Schema(description = "市价买单预算额")
    private Long quoteAmount;
    @Schema(description = "剩余未成交数量")
    private Long remaining;
    @Schema(description = "已成交数量")
    private Long filledAmount;
    @Schema(description = "已成交名义值")
    private Long filledQuoteAmount;
    @Schema(description = "平均成交价")
    private Long avgPrice;
    @Schema(description = "成交笔数")
    private Integer tradeCount;
    @Schema(description = "累计手续费")
    private Long fee;
    @Schema(description = "已冻结计价币金额")
    private Long freezeQuoteAmount;
    @Schema(description = "已冻结基础币数量")
    private Long freezeBaseAmount;
    @Schema(description = "状态:0=NEW 1=PARTIAL 2=FILLED 3=CANCELLED 4=REJECTED")
    private Integer status;
    @Schema(description = "备注")
    private String remark;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime createTime;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime cancelTime;
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private LocalDateTime filledTime;
}
