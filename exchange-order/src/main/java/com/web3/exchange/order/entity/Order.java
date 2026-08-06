package com.web3.exchange.order.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.web3.exchange.common.entity.base.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;

/**
 * 订单表（t_order）——订单主实体。
 * <p>
 * 金额一律 Long 最小单位：price/quote_amount 为计价币，quantity/remaining/filled_amount 为基础币。
 * 状态机（单向，不可逆）：NEW(0)→PARTIAL_FILLED(1)→FILLED(2)；NEW/PARTIAL→CANCELLED(3)；NEW→REJECTED(4)。
 * 状态变更用 MyBatis-Plus 乐观锁（@Version version）+ WHERE status 并发防护，避免重复成交/重复撤单。
 * freeze_* 记录本单在 asset 的冻结明细，供成交过户/撤单解冻的尾差计算。
 * </p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Accessors(chain = true)
@TableName("t_order")
public class Order extends BaseEntity {
    /** 业务订单号（全局唯一，幂等/冻结 requestId 基） */
    private String orderNo;
    /** 客户端订单号（客户端幂等，防重复下单） */
    private String clientOid;
    /** 用户ID */
    private Long userId;
    /** 交易对（如 BTC/USDT） */
    private String symbol;
    /** 基础币（冗余，资金操作用） */
    private String baseCoin;
    /** 计价币（冗余，资金操作用） */
    private String quoteCoin;
    /** 方向：1=BUY 买入 2=SELL 卖出 */
    private Integer side;
    /** 类型：1=GTC 限价 2=MARKET 市价 */
    private Integer orderType;
    /** 时间策略：0=GTC 1=IOC 2=FOK 3=PostOnly */
    private Integer timeInForce;
    /** 条件单类型：0=非条件单 1=止盈 2=止损 */
    private Integer triggerType;
    /** 触发价（计价币最小单位；条件单必填，普通单为0） */
    private Long triggerPrice;
    /** 触发状态：0=待触发 1=已触发(激活为普通单) 2=已取消 */
    private Integer triggerStatus;
    /** OCO 关联组号（同组两单一个触发/成交另一个自动取消） */
    private String ocoGroup;
    /** 限价（计价币最小单位；市价为 0） */
    private Long price;
    /** 下单数量（基础币最小单位；市价买单为 0，见 quoteAmount） */
    private Long quantity;
    /** 市价买单预算额（计价币最小单位；限价/市价卖单为 0） */
    private Long quoteAmount;
    /** 剩余未成交数量（基础币最小单位） */
    private Long remaining;
    /** 已成交数量（基础币最小单位） */
    private Long filledAmount;
    /** 已成交名义值 = Σ(price×qty)（计价币最小单位） */
    private Long filledQuoteAmount;
    /** 平均成交价（加权，计价币最小单位） */
    private Long avgPrice;
    /** 成交笔数 */
    private Integer tradeCount;
    /** 累计手续费（计价币最小单位，本阶段 0） */
    private Long fee;
    /** 冻结幂等号（= orderNo，下单时给 asset freeze 用） */
    private String freezeRequestId;
    /** 已冻结计价币金额（买单 = price×qty 或市价预算） */
    private Long freezeQuoteAmount;
    /** 已冻结基础币数量（卖单 = quantity） */
    private Long freezeBaseAmount;
    /** 状态：0=NEW 1=PARTIAL_FILLED 2=FILLED 3=CANCELLED 4=REJECTED */
    private Integer status;
    /** 撤单时间 */
    private LocalDateTime cancelTime;
    /** 全部成交时间 */
    private LocalDateTime filledTime;
    /** 备注（拒绝/失败原因） */
    private String remark;
}
