package com.web3.exchange.order.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.web3.exchange.common.entity.base.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;

/**
 * 成交表（t_trade）——一笔撮合产生的成交记录。
 * <p>
 * 币种流向固定：买单方付计价币（quote）、收基础币（base）；卖单方反之。
 * 每笔成交需 2 笔 asset 过户（计价币 Q + 基础币 B），幂等号 = tradeNo:Q / tradeNo:B。
 * settle_status：0=待结算 1=已结算 2=结算失败待补偿（定时幂等重试）。
 * </p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Accessors(chain = true)
@TableName("t_trade")
public class Trade extends BaseEntity {
    /** 成交单号（全局唯一） */
    private String tradeNo;
    /** 交易对 */
    private String symbol;
    /** 成交价（计价币最小单位） */
    private Long price;
    /** 成交量（基础币最小单位） */
    private Long quantity;
    /** 成交名义值 = price×quantity（计价币最小单位） */
    private Long quoteAmount;
    /** 吃单订单号 */
    private String takerOrderNo;
    /** 挂单订单号 */
    private String makerOrderNo;
    /** 吃单订单ID */
    private Long takerOrderId;
    /** 挂单订单ID */
    private Long makerOrderId;
    /** 吃单用户ID */
    private Long takerUserId;
    /** 挂单用户ID */
    private Long makerUserId;
    /** 吃单方向：1=BUY 2=SELL */
    private Integer takerSide;
    /** 买方用户ID（冗余） */
    private Long buyUserId;
    /** 卖方用户ID（冗余） */
    private Long sellUserId;
    /** 吃单手续费（计价币最小单位，本阶段 0） */
    private Long takerFee;
    /** 挂单手续费（计价币最小单位，本阶段 0） */
    private Long makerFee;
    /** 结算状态：0=待结算 1=已结算 2=结算失败待补偿 */
    private Integer settleStatus;
    /** 计价币过户幂等号（tradeNo:Q） */
    private String settleQuoteRequestId;
    /** 基础币过户幂等号（tradeNo:B） */
    private String settleBaseRequestId;
    /** 成交时间 */
    private LocalDateTime tradeTime;
}
