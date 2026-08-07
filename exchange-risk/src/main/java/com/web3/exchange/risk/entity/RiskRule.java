package com.web3.exchange.risk.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.web3.exchange.common.entity.base.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

/**
 * 风控规则表（t_risk_rule）。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Accessors(chain = true)
@TableName("t_risk_rule")
public class RiskRule extends BaseEntity {
    /** 规则编码 */
    private String ruleCode;
    /** 规则名称 */
    private String name;
    /** 类型:ORDER_SLIPPAGE/ORDER_AMOUNT/ORDER_DAILY */
    private String ruleType;
    /** 作用域:GLOBAL/USER */
    private String scope;
    /** 交易对(可空=全部) */
    private String symbol;
    /** 阈值(滑点bps/金额最小单位) */
    private Long threshold;
    /** 状态:0=停用,1=启用 */
    private Integer status;
}
