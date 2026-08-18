package com.web3.exchange.market.external;

import com.web3.exchange.market.market.MarketAggregator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.web3j.abi.FunctionEncoder;
import org.web3j.abi.FunctionReturnDecoder;
import org.web3j.abi.TypeReference;
import org.web3j.abi.datatypes.Function;
import org.web3j.abi.datatypes.Type;
import org.web3j.abi.datatypes.generated.Uint112;
import org.web3j.abi.datatypes.generated.Uint256;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.core.DefaultBlockParameterName;
import org.web3j.protocol.core.methods.request.Transaction;
import org.web3j.protocol.core.methods.response.EthCall;
import org.web3j.protocol.http.HttpService;

import java.math.BigInteger;
import java.util.List;
import java.util.Map;

/**
 * DeFi 链上价格源——调 Uniswap V2 Pair 合约 getReserves() 读储备，按恒定乘积算价。
 * <p>作为 Binance WS / CoinGecko 之外的链上兜底价格源：当中心化行情不可用时，
 * 用链上 DEX 流动性池储备计算真实去中心化价格。</p>
 *
 * <p><b>Uniswap V2 Pair</b>：getReserves() 返回 (reserve0, reserve1, blockTimestampLast)。
 * 若 token0 为计价币(USDC 等)、token1 为标的币，则价格 = reserve1 / reserve0（1 个 token1 值多少 token0）。</p>
 *
 * <p>配置示例（application.yml）：</p>
 * <pre>
 * server-settings.external-price:
 *   defi-enabled: false          # 默认关闭（中心化源优先）
 *   defi-rpc-url: https://rpc.example.com  # EVM RPC
 *   defi-pairs:                  # symbol → Uniswap V2 Pair 地址 + token0 是否计价币
 *     ETH/USDT:
 *       pair: 0x0d4a11d5EEaaC28EC3F61d100daF4d40471f1852
 *       quoteIsToken0: true      # token0=USDC计价, token1=ETH
 * </pre>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DefiPriceSource {

    private final MarketAggregator aggregator;

    @Value("${server-settings.external-price.quantity:100000000}")
    private long quantity;

    @Value("${server-settings.external-price.defi-enabled:false}")
    private boolean defiEnabled;

    @Value("${server-settings.external-price.defi-rpc-url:}")
    private String defiRpcUrl;

    /** symbol → Uniswap V2 Pair 配置（application.yml 注入） */
    @Value("#{${server-settings.external-price.defi-pairs: {}}}")
    private Map<String, Map<String, String>> defiPairs;

    /** 按需创建 Web3j（懒加载，避免无链环境启动失败） */
    private volatile Web3j web3j;

    /** 拉取并注入一次 DeFi 链上价格。返回更新的交易对数。 */
    public int fetchAndInject() {
        if (!defiEnabled || defiRpcUrl == null || defiRpcUrl.isBlank()) {
            return 0;
        }
        if (defiPairs == null || defiPairs.isEmpty()) {
            return 0;
        }
        int updated = 0;
        try {
            Web3j w3j = getWeb3j();
            for (Map.Entry<String, Map<String, String>> e : defiPairs.entrySet()) {
                String symbol = e.getKey();
                Map<String, String> cfg = e.getValue();
                String pair = cfg.get("pair");
                boolean quoteIsToken0 = Boolean.parseBoolean(cfg.getOrDefault("quoteIsToken0", "false"));
                // 两个代币各自的 decimals（Uniswap getReserves 返回原始精度，需归一化对齐量纲）
                int quoteDec = Integer.parseInt(cfg.getOrDefault("quoteDecimals", "18"));
                int baseDec = Integer.parseInt(cfg.getOrDefault("baseDecimals", "18"));
                if (pair == null || pair.isBlank()) continue;
                try {
                    Long priceMin = fetchPairPrice(w3j, pair, quoteIsToken0, quoteDec, baseDec);
                    if (priceMin == null || priceMin <= 0) continue;
                    // 注入一笔外部成交，更新该交易对最新价/K线（DeFi 链上价格作为兜底）
                    aggregator.applyExternalTrade(symbol, priceMin, quantity);
                    updated++;
                    log.info("[defiprice] {} DeFi价格 {} 最小单位", symbol, priceMin);
                } catch (Exception ex) {
                    log.warn("[defiprice] {} 读价失败: {}", symbol, ex.getMessage());
                }
            }
        } catch (Exception e) {
            log.warn("[defiprice] DeFi 价格源异常: {}", e.getMessage());
        }
        return updated;
    }

    /** 调 Uniswap V2 Pair getReserves() 读储备，算 1 个标的币 = 多少个计价币（转最小单位）。 */
    private Long fetchPairPrice(Web3j w3j, String pairAddress, boolean quoteIsToken0,
                                int quoteDec, int baseDec) throws Exception {
        // getReserves() → (reserve0 uint112, reserve1 uint112, blockTimestampLast uint32)
        Function fn = new Function("getReserves",
                List.of(),
                List.of(new TypeReference<Uint112>() {}, new TypeReference<Uint112>() {}, new TypeReference<Uint256>() {}));
        String data = FunctionEncoder.encode(fn);
        EthCall call = w3j.ethCall(
                        Transaction.createEthCallTransaction(
                                "0x0000000000000000000000000000000000000000", pairAddress, data),
                        DefaultBlockParameterName.LATEST)
                .send();
        if (call.hasError()) {
            log.warn("[defiprice] getReserves 错误: {}", call.getError().getMessage());
            return null;
        }
        List<Type> result = FunctionReturnDecoder.decode(call.getValue(), fn.getOutputParameters());
        if (result == null || result.size() < 2) {
            return null;
        }
        BigInteger reserve0 = (BigInteger) result.get(0).getValue();
        BigInteger reserve1 = (BigInteger) result.get(1).getValue();
        if (reserve0.signum() <= 0 || reserve1.signum() <= 0) {
            return null;
        }
        // 归一化量纲：把两种代币储备都换算成各自的"币数量"（÷10^decimals），
        // 再算 price = quoteAmount / baseAmount，最后转 quoteDecimals 位最小单位。
        // 为避免浮点，用整数运算：priceMin = (reserveQ * 10^quoteDec * 10^baseDec) / (reserveB * 10^quoteDec)
        //    = reserveQ * 10^baseDec / reserveB（quoteDec 抵消）
        BigInteger quoteReserve = quoteIsToken0 ? reserve0 : reserve1;
        BigInteger baseReserve = quoteIsToken0 ? reserve1 : reserve0;
        // 统一到 base 币的最小单位：每 1 base 币的 quote 币最小单位数
        // priceMin = quoteReserve * 10^baseDec / baseReserve，结果即"1 base币 = 多少 quote币最小单位"
        BigInteger priceMin = quoteReserve
                .multiply(BigInteger.TEN.pow(baseDec))
                .divide(baseReserve);
        return priceMin.longValue();
    }

    private Web3j getWeb3j() {
        if (web3j == null) {
            synchronized (this) {
                if (web3j == null) {
                    web3j = Web3j.build(new HttpService(defiRpcUrl));
                }
            }
        }
        return web3j;
    }
}
