package com.web3.exchange.futures.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 合约下单请求。
 */
@Data
public class PlaceFuturesOrderDTO {
    @NotNull(message = "用户不能为空")
    private Long userId;
    @NotNull(message = "交易对不能为空")
    private String symbol;
    /** 1开多 2开空 3平多 4平空 */
    @NotNull(message = "方向不能为空")
    private Integer side;
    /** 1限价 2市价 */
    @NotNull(message = "类型不能为空")
    private Integer orderType;
    /** 限价(人读值,市价可空) */
    private String price;
    /** 数量(人读值) */
    @NotNull(message = "数量不能为空")
    private String quantity;
    /** 杠杆(1-100) */
    private Integer leverage;
    /** 1逐仓 2全仓 */
    private Integer marginMode;
}
