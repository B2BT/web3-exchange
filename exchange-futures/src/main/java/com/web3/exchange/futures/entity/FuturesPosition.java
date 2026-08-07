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
 * 永续合约持仓（逐仓，双向持仓）。
 */
@Data
@TableName("t_futures_position")
public class FuturesPosition implements Serializable {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private Long userId;
    private String symbol;
    /** 1多 2空 */
    private Integer side;
    /** 持仓数量(最小单位) */
    private Long size;
    /** 开仓均价(最小单位) */
    private Long entryPrice;
    /** 当前杠杆 */
    private Integer leverage;
    /** 逐仓保证金 */
    private Long isolatedMargin;
    /** 强平价格(最小单位) */
    private Long liqPrice;
    /** 未实现盈亏 */
    private Long unrealizedPnl;
    /** 该仓累计已实现盈亏 */
    private Long realizedPnl;
    /** 0持仓中 1已平仓 */
    private Integer status;
    @Version
    private Integer version;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
