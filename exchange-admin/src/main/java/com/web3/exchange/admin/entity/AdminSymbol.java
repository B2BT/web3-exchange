package com.web3.exchange.admin.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import com.web3.exchange.common.entity.base.BaseEntity;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.experimental.Accessors;

/**
 * 交易对表（t_symbol）——管理平台交易对管理用轻量实体。
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Accessors(chain = true)
@TableName("t_symbol")
public class AdminSymbol extends BaseEntity {
    /** 交易对符号:BTC/USDT */
    private String symbol;
    /** 基础币(被交易资产,如BTC) */
    private String baseCoin;
    /** 计价币(用于标价,如USDT) */
    private String quoteCoin;
    /** 基础币ID(关联t_coin) */
    private Long baseCoinId;
    /** 计价币ID(关联t_coin) */
    private Long quoteCoinId;
    /** 价格精度(小数位数) */
    private Integer pricePrecision;
    /** 数量精度(小数位数) */
    private Integer amountPrecision;
    /** 最小价格变动单位(计价币最小单位) */
    private Long priceTick;
    /** 最小下单数量(基础币最小单位) */
    private Long minAmount;
    /** 单笔最大下单数量(基础币最小单位) */
    private Long maxAmount;
    /** 最小下单名义值(计价币最小单位) */
    private Long minNotional;
    /** 吃单费率(基点) */
    private Integer takerFeeRate;
    /** 挂单费率(基点) */
    private Integer makerFeeRate;
    /** 排序 */
    private Integer sort;
    /** 状态:0=停牌,1=交易中 */
    private Integer status;
}
