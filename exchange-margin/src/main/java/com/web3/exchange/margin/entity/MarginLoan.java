package com.web3.exchange.margin.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.web3.exchange.common.entity.base.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;

/**
 * 借币记录表（t_margin_loan）。
 * <p>status:0=借出中,1=已还清。rate_daily 为日利率基点(10000=100%)。</p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Accessors(chain = true)
@TableName("t_margin_loan")
public class MarginLoan extends BaseEntity {
    /** 用户ID */
    private Long userId;
    /** 币种符号 */
    private String symbol;
    /** 幂等号 */
    private String requestId;
    /** 借入本金(最小单位) */
    private Long amount;
    /** 日利率(基点,10000=100%) */
    private Long rateDaily;
    /** 剩余本金 */
    private Long principalRemain;
    /** 该笔累计利息 */
    private Long interestAccrued;
    /** 状态:0=借出中,1=已还清 */
    private Integer status;
    /** 借出时间 */
    private LocalDateTime openTime;
    /** 还清时间 */
    private LocalDateTime repayTime;
}
