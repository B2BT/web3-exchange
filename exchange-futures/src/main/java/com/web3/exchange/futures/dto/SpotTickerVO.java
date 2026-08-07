package com.web3.exchange.futures.dto;

import lombok.Data;

import java.io.Serializable;

/**
 * 现货行情 ticker（仅取合约标记价所需字段）。
 */
@Data
public class SpotTickerVO implements Serializable {
    private String symbol;
    /** 最新价（最小单位 Long） */
    private String lastPrice;
    private String change24h;
    private String volume24h;
    private String high24h;
    private String low24h;
}
