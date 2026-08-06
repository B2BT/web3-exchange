package com.web3.exchange.notify.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.web3.exchange.common.entity.base.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;

/**
 * 站内通知表（t_notification）——用户的可查询、可标记已读的站内信（inbox）。
 * <p>
 * 数据来源：通知域消费者（AssetChangeNotifyConsumer / OrderTradeNotifyConsumer）将
 * ASSET-CHANGE / ORDER-TRADE 事件映射为一条通知记录。幂等由
 * {@code uk_user_type_bizref(user_id, type, biz_ref)} 唯一索引兜底：同一用户同一类型
 * 同一业务单号只落一条通知，重复事件 insert 撞唯一索引后捕获跳过。
 * </p>
 * <p>
 * type 取值：DEPOSIT_CONFIRMED（充值到账）/ WITHDRAW_SUCCESS（提现成功）/ TRADE_FILLED（订单成交）。
 * bizRef：充值/提现取源事件 refNo；成交拆为 tradeNo:BUY / tradeNo:SELL。
 * amount 为 Long 最小单位原始值，展示层换算。
 * </p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Accessors(chain = true)
@TableName("t_notification")
public class Notification extends BaseEntity {
    /** 接收用户ID */
    private Long userId;
    /** 通知类型:DEPOSIT_CONFIRMED/WITHDRAW_SUCCESS/TRADE_FILLED */
    private String type;
    /** 标题 */
    private String title;
    /** 内容(含业务详情) */
    private String content;
    /** 源事件业务类型:DEPOSIT/WITHDRAW/TRADE */
    private String bizType;
    /** 关联业务单号(幂等键组分):depositId/withdrawId/tradeNo:BUY|:SELL */
    private String bizRef;
    /** 关联币种/交易对(冗余检索) */
    private String symbol;
    /** 关联金额(最小单位,冗余展示) */
    private Long amount;
    /** 已读状态:0=未读,1=已读 */
    private Integer isRead;
    /** 已读时间 */
    private LocalDateTime readTime;
    /** 通知渠道:INBOX站内信(本期仅此) */
    private String channel;
}
