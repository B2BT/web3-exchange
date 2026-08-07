package com.web3.exchange.staking.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.web3.exchange.common.entity.base.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

/**
 * 质押收益流水表（t_staking_interest）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Accessors(chain = true)
@TableName("t_staking_interest")
public class StakingInterest extends BaseEntity {
    /** 用户ID */
    private Long userId;
    /** 持仓ID */
    private Long positionId;
    /** 币种符号 */
    private String symbol;
    /** 本次结算收益(最小单位) */
    private Long amount;
    /** 结算日期(YYYYMMDD) */
    private String settleDate;
    /** 幂等号 */
    private String requestId;
    /** 备注 */
    private String remark;
}
