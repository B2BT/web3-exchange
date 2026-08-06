package com.web3.exchange.admin.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.web3.exchange.common.entity.base.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;

/**
 * 订单表（t_order）——管理平台全站订单查询用实体。
 * <p>金额一律 Long 最小单位；字段对齐既有表结构，VO 同款字段见 OrderVO。</p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@TableName("t_order")
public class Order extends BaseEntity {
    /** 业务订单号（全局唯一） */
    private String orderNo;
    /** 客户端订单号 */
    private String clientOid;
    /** 用户ID */
    private Long userId;
    /** 交易对（如 BTC/USDT） */
    private String symbol;
    /** 基础币 */
    private String baseCoin;
    /** 计价币 */
    private String quoteCoin;
    /** 方向：1=BUY 2=SELL */
    private Integer side;
    /** 类型：1=GTC 2=MARKET */
    private Integer orderType;
    /** 时间策略：0=GTC 1=IOC 2=FOK 3=PostOnly */
    private Integer timeInForce;
    /** 条件单类型：0=非条件单 1=止盈 2=止损 */
    private Integer triggerType;
    /** 触发价 */
    private Long triggerPrice;
    /** 触发状态 */
    private Integer triggerStatus;
    /** OCO 关联组号 */
    private String ocoGroup;
    /** 限价 */
    private Long price;
    /** 下单数量 */
    private Long quantity;
    /** 市价买单预算额 */
    private Long quoteAmount;
    /** 剩余未成交数量 */
    private Long remaining;
    /** 已成交数量 */
    private Long filledAmount;
    /** 已成交名义值 */
    private Long filledQuoteAmount;
    /** 平均成交价 */
    private Long avgPrice;
    /** 成交笔数 */
    private Integer tradeCount;
    /** 累计手续费 */
    private Long fee;
    /** 已冻结计价币金额 */
    private Long freezeQuoteAmount;
    /** 已冻结基础币数量 */
    private Long freezeBaseAmount;
    /** 状态：0=NEW 1=PARTIAL_FILLED 2=FILLED 3=CANCELLED 4=REJECTED */
    private Integer status;
    /** 备注 */
    private String remark;
    /** 撤单时间 */
    private LocalDateTime cancelTime;
    /** 全部成交时间 */
    private LocalDateTime filledTime;
}
