package com.web3.exchange.market.external;

import com.web3.exchange.market.external.dto.CoinGeckoMarket;
import com.web3.exchange.market.market.MarketAggregator;
import com.web3.exchange.market.market.model.Ticker;
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

    /** 交易对 → CoinGecko 币 id（主流币，一次请求批量拉取） */
    private static final Map<String, String> SYMBOL_TO_ID = new HashMap<>() {{
        put("BTC/USDT", "bitcoin");
        put("ETH/USDT", "ethereum");
        put("BNB/USDT", "binancecoin");
        put("XRP/USDT", "ripple");
        put("SOL/USDT", "solana");
        put("ADA/USDT", "cardano");
        put("DOGE/USDT", "dogecoin");
        put("USDT/USDT", "tether");
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
                // 覆盖权威 24h ticker（真实 high/low/volume/change）
                aggregator.updateExternalTicker(buildTicker(symbol, m));
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

    /** 用 CoinGecko 真实 24h 数据构建权威 ticker 快照。 */
    private Ticker buildTicker(String symbol, CoinGeckoMarket m) {
        Ticker t = new Ticker();
        t.setSymbol(symbol);
        long last = toMinUnit(m.getCurrentPrice());
        t.setLastPrice(last);
        // 涨跌幅：CoinGecko 返回百分比，转基点(10000=100%)
        double chg = m.getPriceChangePercentage24h() == null ? 0 : m.getPriceChangePercentage24h();
        t.setChange24h((long) Math.round(chg * 100));
        // 24h 最高/最低/量（真实值）
        t.setHigh24h(m.getHigh24h() == null ? last : toMinUnit(m.getHigh24h()));
        t.setLow24h(m.getLow24h() == null ? last : toMinUnit(m.getLow24h()));
        // 24h 成交额(法币)作为 quoteVolume；volume 无直接值，按现价反推近似
        double qv = m.getTotalVolume() == null ? 0 : m.getTotalVolume();
        t.setQuoteVolume24h((long) Math.round(qv * Math.pow(10, quoteDecimals)));
        long approxVol = last > 0 ? (long) Math.round(qv / m.getCurrentPrice()) : 0;
        t.setVolume24h(approxVol);
        return t;
    }
}
