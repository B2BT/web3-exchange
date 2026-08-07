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
 * 永续合约账户（每用户每币种一条，初始 USDT）。
 */
@Data
@TableName("t_futures_account")
public class FuturesAccount implements Serializable {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private Long userId;
    private String coin;
    /** 账户余额(最小单位) */
    private Long marginBalance;
    /** 可用余额 */
    private Long availableBalance;
    /** 占用保证金 */
    private Long positionMargin;
    /** 未实现盈亏 */
    private Long unrealizedPnl;
    /** 累计已实现盈亏 */
    private Long realizedPnl;
    @Version
    private Integer version;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
