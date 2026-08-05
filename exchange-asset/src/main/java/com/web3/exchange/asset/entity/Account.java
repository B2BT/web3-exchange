package com.web3.exchange.asset.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.web3.exchange.common.entity.base.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

/**
 * 钱包账户表
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Accessors(chain = true)
@TableName("t_wallet_account")
public class Account extends BaseEntity {
    /** 用户ID */
    private Long userId;
    /** 币种ID(关联t_coin) */
    private Long coinId;
    /** 币种符号(冗余,便于查询) */
    private String symbol;
    /** 可用余额(最小单位) */
    private Long available;
    /** 冻结余额(最小单位) */
    private Long frozen;
    /** 总余额=available+frozen(最小单位) */
    private Long total;
    /** 账户状态:0=禁用,1=正常,2=冻结 */
    private Integer status;
}
