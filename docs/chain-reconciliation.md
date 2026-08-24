# 对账扩展：链上钱包余额核对

> 在内部账户对账（ReconciliationService，见 docs/reconciliation.md）之上，增加「链上钱包 → 内部账本」核对：
> 用 web3j 查热钱包在链上的原生币/代币余额，与内部账本对比，发现链上资金泄漏/差异。

## 实现

- `ChainBalanceReconciler`（exchange-chain/reconcil）
  - 用 `ChainRegistry.get(chainCode)` 取 Web3j，`walletService.getCredentials()` 取热钱包地址
  - `eth_getBalance` 查热钱包原生币余额（ETH）
- 接口：`GET /api/chain/reconcile/chain/{chainCode}`

## 验证（实测）

```
GET /api/chain/reconcile/chain/ETH
→ 链 ETH 热钱包 0xf39fd6... 原生余额 = 1 ETH（真实查 mock-rpc eth_getBalance）
```

## 生产进一步

1. **ERC-20 代币余额**：bancall balanceOf 合约读余额
2. **冷热钱包全部地址**核对（热钱包多地址合计 vs 账本）
3. **自动对账任务**：定期跑 + 偏差告警（接 Prometheus）
4. **跨链**：BTC/TRON 等多链统一余额聚合

## 相关
- `docs/reconciliation.md`（内部账户对账）
- 内存对账引擎：`AssetApiController /reconcile`