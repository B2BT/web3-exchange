package com.web3.exchange.market.external;

import com.web3.exchange.market.external.dto.CoinGeckoMarket;
import com.web3.exchange.market.market.MarketAggregator;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * CoinGecko 真实行情拉取服务。
 * <p>定时从 CoinGecko 公开 API 拉取主流币对真实价格，注入 MarketAggregator，
 * 使系统的 ticker/K线显示真实市场行情而非内部模拟成交。</p>
 *
 * <p><b>币 → 交易对映射</b>：btc→BTC/USDT，eth→ETH/USDT，tether→USDT/USDT(≈1)。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CoinGeckoPriceSource {

    private static final String URL =
            "https://api.coingecko.com/api/v3/coins/markets?vs_currency=usd&ids=%s&per_page=10&page=1";

    /** 交易对 → CoinGecko 币 id */
    private static final Map<String, String> SYMBOL_TO_ID = new HashMap<>() {{
        put("BTC/USDT", "bitcoin");
        put("ETH/USDT", "ethereum");
    }};

    private final MarketAggregator aggregator;

    @Value("${server-settings.external-price.quote-decimals:8}")
    private int quoteDecimals;

    @Value("${server-settings.external-price.quantity:100000000}")
    private long quantity;

    private final RestTemplate restTemplate = new RestTemplate();
    private final ObjectMapper mapper = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    /** 拉取并注入一次真实行情。返回更新的交易对数。 */
    public int fetchAndInject() {
        int updated = 0;
        try {
            String ids = String.join(",", SYMBOL_TO_ID.values());
            String json = restTemplate.getForObject(String.format(URL, ids), String.class);
            List<CoinGeckoMarket> list = mapper.readValue(
                    json, mapper.getTypeFactory().constructCollectionType(List.class, CoinGeckoMarket.class));
            if (list == null || list.isEmpty()) {
                log.warn("[extprice] CoinGecko 返回空");
                return 0;
            }
            Map<String, CoinGeckoMarket> byId = new HashMap<>();
            for (CoinGeckoMarket m : list) byId.put(m.getId(), m);
            for (Map.Entry<String, String> e : SYMBOL_TO_ID.entrySet()) {
                CoinGeckoMarket m = byId.get(e.getValue());
                if (m == null || m.getCurrentPrice() == null || m.getCurrentPrice() <= 0) continue;
                String symbol = e.getKey();
                long priceMin = toMinUnit(m.getCurrentPrice());
                // 注入外部成交，更新该交易对 K线/ticker
                aggregator.applyExternalTrade(symbol, priceMin, quantity);
                updated++;
            }
        } catch (Exception e) {
            log.warn("[extprice] 拉取 CoinGecko 失败: {}", e.getMessage());
        }
        return updated;
    }

    /** 法币价 → 计价币最小单位 Long（quoteDecimals 位小数）。 */
    private long toMinUnit(double price) {
        double factor = Math.pow(10, quoteDecimals);
        return (long) Math.round(price * factor);
    }
}
