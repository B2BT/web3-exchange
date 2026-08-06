package com.web3.exchange.order.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.web3.exchange.common.entity.base.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

/**
 * 币种表（t_coin）——order 域只读视图，仅映射精度换算所需字段。
 * <p>
 * 金额口径复用资产域：decimals 为该币种「最小单位位数」（USDT=6、BTC=8、ETH=18）。
 * 撮合/冻结的名义额换算需用计价币(quote)的 decimals，本实体用于在 order 域直接查询
 * t_coin（与 asset 同库 web3_exchange），避免跨服务查询精度。
 * </p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Accessors(chain = true)
@TableName("t_coin")
public class Coin extends BaseEntity {
    /** 币种符号:BTC/ETH/USDT */
    private String symbol;
    /** 精度（最小单位位数） */
    private Integer decimals;
}
