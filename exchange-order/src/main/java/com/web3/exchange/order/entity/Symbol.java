package com.web3.exchange.order.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.web3.exchange.common.entity.base.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

/**
 * 交易对表（t_symbol）——可交易对的配置驱动实体。
 * <p>
 * 撮合引擎只对 status=1（交易中）的交易对开放；价格/数量精度、最小下单、费率均由本表驱动，
 * 业务代码不写死。price 精度（price_precision）与币种精度（t_coin.decimals）分离：
 * 本表 price_tick 为最小价格变动单位（计价币最小单位），限价须为其整数倍。
 * </p>
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Accessors(chain = true)
@TableName("t_symbol")
public class Symbol extends BaseEntity {
    /** 交易对符号，如 BTC/USDT（唯一） */
    private String symbol;
    /** 基础币（被交易资产，如 BTC） */
    private String baseCoin;
    /** 计价币（用于标价，如 USDT） */
    private String quoteCoin;
    /** 基础币ID（关联 t_coin，可选冗余） */
    private Long baseCoinId;
    /** 计价币ID（关联 t_coin，可选冗余） */
    private Long quoteCoinId;
    /** 价格精度（小数位数） */
    private Integer pricePrecision;
    /** 数量精度（小数位数） */
    private Integer amountPrecision;
    /** 最小价格变动单位（计价币最小单位），限价须为其整数倍 */
    private Long priceTick;
    /** 最小下单数量（基础币最小单位） */
    private Long minAmount;
    /** 单笔最大下单数量（基础币最小单位） */
    private Long maxAmount;
    /** 最小下单名义值 = price×quantity（计价币最小单位） */
    private Long minNotional;
    /** 吃单费率（基点 bp，10=0.1%；本阶段默认 0） */
    private Integer takerFeeRate;
    /** 挂单费率（基点 bp；本阶段默认 0） */
    private Integer makerFeeRate;
    /** 排序 */
    private Integer sort;
    /** 状态：0=停牌（禁止交易），1=交易中 */
    private Integer status;
}
