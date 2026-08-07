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
 * 永续合约订单。
 * <p>side：1=开多 2=开空 3=平多 4=平空。开多/平空=买方，开空/平多=卖方。</p>
 */
@Data
@TableName("t_futures_order")
public class FuturesOrder implements Serializable {
    @TableId(type = IdType.ASSIGN_ID)
    private Long id;
    private String orderNo;
    private Long userId;
    private String symbol;
    /** 1开多 2开空 3平多 4平空 */
    private Integer side;
    /** 1限价 2市价 */
    private Integer orderType;
    /** 限价(最小单位,市价为0) */
    private Long price;
    /** 数量(最小单位) */
    private Long quantity;
    /** 剩余未成交量 */
    private Long remaining;
    /** 已成交量 */
    private Long filled;
    /** 成交均价 */
    private Long avgPrice;
    private Integer leverage;
    /** 1逐仓 2全仓 */
    private Integer marginMode;
    /** 0挂单 1部分 2完全 3取消 4拒绝 */
    private Integer status;
    @Version
    private Integer version;
    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;
    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
