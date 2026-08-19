# 撮合簿持久化（重启重建）

> 第二梯队·可用性第 6 项（内存态数据持久化）。修复"内存撮合簿重启即丢失"问题：
> 期货撮合簿从"纯内存"升级为"DB 为权威源 + 启动重建"，与现货订单簿 OrderBookRecovery 同模式。

## 问题

期货撮合（`FuturesMatchingEngine`）盘口（bids/asks）是**纯内存 TreeMap**，进程重启即清空。
之前"重启期货链"需清表 + 重跑，丢失未成交挂单。

## 方案

合约定单已落库（`t_futures_order`），DB 即权威源。新增 `FuturesOrderBookRecovery`，
在应用就绪（`ApplicationReadyEvent`）后：

```
t_futures_order (status IN (0,1) AND remaining > 0)
   └─ 逐单 → FuturesMatchingEngine.restore(symbol, order) → 重建读、卖盘
```

- 恢复对象：**活跃限价单**（status 0=待成交/1=部分成交 且 remaining>0）
- 市价单一次性撮合不落簿，无需恢复
- `restoreMaker` 直接挂单不撮合，按 remaining 入对应价档；幂等（orderNo 去重）

## 改动

| 文件 | 说明 |
|------|------|
| `FuturesMatchingEngine` | 加 `restore(symbol, order)` 公开方法 + 内部 `OrderBook.restoreMaker()` |
| `FuturesOrderBookRecovery` | 启动重建监听器（查询活跃挂单 → 逐单 restore） |

## 验证

- ✅ DB 有 2 条活跃挂单（status=0, remaining=100, BTC-USDT-SWAP）
- ✅ 重启 futures → 日志 `[futures] 启动重建订单簿：恢复 2 个活跃挂单（1 个交易对）`
- ✅ 撮合簿重建完成，恢复的挂单可在重启后参与撮合

## 现货侧（已存在）

`exchange-order` 的 `OrderBookRecovery` 已实现同款 DB 重建（docs/order-domain.md §5.6）。
本次为期货补齐，全站撮合簿现均可从 DB 恢复，"重启即丢"问题消除。

## 生产进一步（可选）

1. **成交历史**：t_futures_fill 持久化后，重启可重建行情/标记价（当前持仓/账户已落库）
2. **WAL**：若需高性能 + 低 DB 依赖，可加 Redis/MQ 快照 + WAL；当前 DB 重建模式已满足演示
3. **多副本**：撮合引擎单实例有状态，多副本需分区/状态同步（见 k8s HPA 演进）
