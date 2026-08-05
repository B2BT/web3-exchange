package com.web3.exchange.asset.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.web3.exchange.common.entity.base.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

/**
 * 钱包账户表（t_wallet_account）——用户在某币种下的资金载体。
 * <p>
 * 业务角色：每个用户对每个币种仅有一个账户（由唯一索引 uk_user_symbol 保证），
 * 账户承载该币种的全部资金。金额一律以「该币种最小单位」的整数（Long）存储，
 * 精度由 t_coin.decimals 定义，避免浮点误差。
 * </p>
 * <p>
 * 资金不变式（铁律）：<b>available + frozen == total</b> 永远成立。
 * 所有资金变动只改 available 与 frozen，total 由二者算出、从不单独设置；
 * 余额的更新必须经 LedgerService.doChange（行锁 + 乐观锁 + 幂等）统一完成，
 * 应用层禁止直接 UPDATE 本表余额。
 * </p>
 * <p>
 * 账户状态 status：0=禁用，1=正常，2=冻结（风控层面整体冻结，区别于按币种的金额冻结）。
 * 本实体继承 BaseEntity，version 为乐观锁版本号，余额更新必带（防跨实例并发错账）。
 * </p>
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
