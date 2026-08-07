package com.web3.exchange.admin.dto;

import lombok.Data;

/**
 * 交易对创建/编辑请求。
 */
@Data
public class SymbolRequest {
    /** 交易对ID(编辑时必填) */
    private Long id;
    /** 交易对符号:BTC/USDT */
    private String symbol;
    /** 基础币 */
    private String baseCoin;
    /** 计价币 */
    private String quoteCoin;
    /** 价格精度 */
    private Integer pricePrecision;
    /** 数量精度 */
    private Integer amountPrecision;
    /** 最小价格变动单位 */
    private Long priceTick;
    /** 最小下单数量 */
    private Long minAmount;
    /** 最大下单数量 */
    private Long maxAmount;
    /** 最小名义值 */
    private Long minNotional;
    /** 吃单费率(基点) */
    private Integer takerFeeRate;
    /** 挂单费率(基点) */
    private Integer makerFeeRate;
    /** 排序 */
    private Integer sort;
}
