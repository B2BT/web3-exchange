package com.web3.exchange.futures.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 合约标记价格（每交易对一条，定时刷新）。
 */
@Data
@TableName("t_mark_price")
public class MarkPrice implements Serializable {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private String symbol;
    /** 标记价(最小单位) */
    private Long markPrice;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
