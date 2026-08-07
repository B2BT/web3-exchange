package com.web3.exchange.market.external.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

/**
 * CoinGecko /coins/markets 返回单币条目的精简 DTO。
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class CoinGeckoMarket {
    private String id;
    private String symbol;
    private String name;
    /** 当前价（法币，如 USD） */
    @JsonProperty("current_price")
    private Double currentPrice;
    /** 24h 最高 */
    @JsonProperty("high_24h")
    private Double high24h;
    /** 24h 最低 */
    @JsonProperty("low_24h")
    private Double low24h;
    /** 24h 涨跌幅（百分比，如 1.5 = 涨1.5%） */
    @JsonProperty("price_change_percentage_24h")
    private Double priceChangePercentage24h;
    /** 24h 交易量（法币） */
    @JsonProperty("total_volume")
    private Double totalVolume;
}
