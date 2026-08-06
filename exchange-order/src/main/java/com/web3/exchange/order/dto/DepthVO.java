package com.web3.exchange.order.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

import java.io.Serial;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * 深度盘口视图（对外返回）。金额一律 Long 最小单位，不做除法。
 * <p>数据源：MatchingEngine 内存盘口（books ConcurrentHashMap）聚合。</p>
 */
@Data
@Schema(description = "深度盘口视图")
public class DepthVO implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(description = "交易对")
    private String symbol;

    /** 买盘：按价格降序，各取前 limit 档 */
    @Schema(description = "买盘（价格降序）")
    private List<DepthLevel> bids = new ArrayList<>();

    /** 卖盘：按价格升序，各取前 limit 档 */
    @Schema(description = "卖盘（价格升序）")
    private List<DepthLevel> asks = new ArrayList<>();

    public DepthVO() {
    }

    public DepthVO(String symbol, List<DepthLevel> bids, List<DepthLevel> asks) {
        this.symbol = symbol;
        this.bids = bids;
        this.asks = asks;
    }
}
