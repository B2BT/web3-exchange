package com.web3.exchange.staking.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.web3.exchange.common.entity.base.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

import java.time.LocalDateTime;

/**
 * 用户质押持仓表（t_staking_position）。
 * <p>status:0=质押中,1=已赎回；accruedInterest 累计未结收益，totalInterest 累计已结收益。</p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Accessors(chain = true)
@TableName("t_staking_position")
public class StakingPosition extends BaseEntity {
    /** 用户ID */
    private Long userId;
    /** 产品编码 */
    private String productCode;
    /** 币种符号 */
    private String symbol;
    /** 质押本金(最小单位) */
    private Long amount;
    /** 累计未结收益 */
    private Long accruedInterest;
    /** 累计已结收益 */
    private Long totalInterest;
    /** 状态:0=质押中,1=已赎回 */
    private Integer status;
    /** 质押时间 */
    private LocalDateTime startTime;
    /** 锁仓到期时间(活期为null) */
    private LocalDateTime lockEndTime;
    /** 赎回时间 */
    private LocalDateTime redeemTime;
}
