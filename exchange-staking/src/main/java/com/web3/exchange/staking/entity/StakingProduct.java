package com.web3.exchange.staking.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.web3.exchange.common.entity.base.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

/**
 * 质押产品表（t_staking_product）。
 * <p>type:0=活期,1=锁仓；annualRateBp 年化利率基点(10000=100%)；lockDays 锁仓天数(活期=0)。</p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Accessors(chain = true)
@TableName("t_staking_product")
public class StakingProduct extends BaseEntity {
    /** 产品编码 */
    private String productCode;
    /** 产品名称 */
    private String name;
    /** 类型:0=活期,1=锁仓 */
    private Integer type;
    /** 币种符号 */
    private String symbol;
    /** 年化利率(基点) */
    private Integer annualRateBp;
    /** 最小质押额(最小单位) */
    private Long minAmount;
    /** 锁仓天数(活期为0) */
    private Integer lockDays;
    /** 状态:0=下架,1=上架 */
    private Integer status;
}
