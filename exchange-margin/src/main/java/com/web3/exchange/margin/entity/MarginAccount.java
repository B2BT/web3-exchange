package com.web3.exchange.margin.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.web3.exchange.common.entity.base.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

/**
 * 杠杆账户表（t_margin_account）——每用户每币种的杠杆资金载体。
 * <p>collateral=抵押、borrowed=借入本金、interest_accrued=未还利息，均最小单位 Long。</p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Accessors(chain = true)
@TableName("t_margin_account")
public class MarginAccount extends BaseEntity {
    /** 用户ID */
    private Long userId;
    /** 币种符号 */
    private String symbol;
    /** 抵押(最小单位) */
    private Long collateral;
    /** 借入本金(最小单位) */
    private Long borrowed;
    /** 未还利息(最小单位) */
    private Long interestAccrued;
    /** 状态:0=禁用,1=正常 */
    private Integer status;
}
