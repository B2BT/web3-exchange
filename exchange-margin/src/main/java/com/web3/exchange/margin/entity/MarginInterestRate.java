package com.web3.exchange.margin.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.web3.exchange.common.entity.base.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

/**
 * 杠杆利率配置表（t_margin_interest_rate）——币种日利率与维持保证金率。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Accessors(chain = true)
@TableName("t_margin_interest_rate")
public class MarginInterestRate extends BaseEntity {
    /** 币种符号 */
    private String symbol;
    /** 日利率(基点,10000=100%;10=0.1%/日) */
    private Integer rateDailyBp;
    /** 维持保证金率(百分数,120=120%) */
    private Integer maintenanceRatio;
    /** 状态:0=禁用,1=正常 */
    private Integer status;
}
