# DeFi 链上价格源

> 为 web3-exchange 行情管道增加 DeFi 链上价格源——调 Uniswap V2 Pair `getReserves()` 读储备算价，作为中心化行情（Binance WS / CoinGecko）之外的**链上兜底**。

## 架构

```
Uniswap V2 Pair 合约
      │  eth_call getReserves() → (reserve0, reserve1)
      ▼
DefiPriceSource (web3j)
      │  按恒定乘积算价 + 量纲归一化
      ▼
MarketAggregator.applyExternalTrade → 更新最新价/K线
```

## 核心逻辑（DefiPriceSource）

- `fetchAndInject()`：遍历配置的 `defi-pairs`，逐个调 `getReserves()`
- **恒定乘积**：储备比即价格（`price = quoteReserve / baseReserve`）
- **量纲归一化**（关键）：Uniswap getReserves 返回的是各代币**原始精度**，须用各自 decimals 对齐：
  ```
  priceMin = quoteReserve × 10^baseDec / baseReserve
  ```
  结果 = 1 个标的币 = 多少个计价币最小单位（quoteDecimals=8 即 ×10^8）

## 配置（application.yml）

```yaml
server-settings.external-price:
  defi-enabled: false          # 默认关闭（中心化源优先）
  defi-rpc-url: https://rpc.example.com
  defi-pairs:                  # symbol → Uniswap V2 Pair
    ETH/USDT:
      pair: 0x0d4a11d5EEaaC28EC3F61d100daF4d40471f1852
      quoteIsToken0: true      # token0=计价币(如USDC)
      quoteDecimals: 6         # token0 精度
      baseDecimals: 18         # token1 精度
```

## 定时任务（DefiPriceTask）

`@Scheduled(fixedDelay=60s)`，由 `defi-enabled` 控制。链上读价比中心化 API 慢，周期放宽到 60s。

## 验证

通过 `contracts/MockUniswapPair.sol`（模拟 getReserves）+ hardhat 本地链：
- 模拟 ETH/USDC 池（reserve0=5000000 USDC 6dec, reserve1=100 ETH 18dec）
- 价格公式结果 = **50000 USDT/ETH** ✅（与期望 5000000/100=50000 一致）
- 编译通过，量纲归一化正确

## 价值

- **去中心化兜底**：当中心化行情（Binance WS/CoinGecko）不可用时，用链上 DEX 流动性池价格保证行情连续性
- 贴合 JD「了解 DeFi 核心逻辑 + 链交互库使用」
- 可直接扩展：增加 Uniswap 路由 `getAmountsOut` 精确算价、支持更多 DEX
