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
 * 资金费率结算记录。
 */
@Data
@TableName("t_funding_settle")
public class FundingSettle implements Serializable {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private String symbol;
    /** 资金费率(基点) */
    private Integer rate;
    /** 结算时标记价(最小单位) */
    private Long markPrice;
    /** 基础价/现货价 */
    private Long basePrice;
    private LocalDateTime fundingTime;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
}
