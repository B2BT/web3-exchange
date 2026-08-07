package com.web3.exchange.futures.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 永续合约交易对。
 */
@Data
@TableName("t_swap_contract")
public class SwapContract implements Serializable {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    /** 交易对，如 BTC-USDT-SWAP */
    private String symbol;
    private String base;
    private String quote;
    /** 价格精度(最小单位位数) */
    private Integer priceDecimals;
    /** 数量精度 */
    private Integer qtyDecimals;
    /** 最大杠杆 */
    private Integer maxLeverage;
    /** 维持保证金率(基点) */
    private Integer mmr;
    /** 初始保证金率(基点) */
    private Integer imr;
    /** 资金费率结算周期(小时) */
    private Integer fundingIntervalHours;
    /** 资金费率上限(基点/期) */
    private Integer maxFundingRate;
    /** 0上架 1下架 */
    private Integer status;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
