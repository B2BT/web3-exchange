# 成交历史持久化 + 多副本 HPA

> 第二梯队·可用性第 7 项。补两块：① 永续合约成交明细落库（此前只有现货 t_trade，期货成交只内存）；② k8s 无状态服务 CPU 自动扩缩容（HPA）。

## ① 期货成交明细持久化

### 问题
- 现货成交已有 `t_trade`（TradeMapper + 事务消息）；**期货成交（FuturesFill）纯内存**，逐笔成交历史未落库，无法查询/审计/重建行情。

### 实现
- 新表 `t_futures_fill`（order_no / user_id / counter_user_id / symbol / side / price / quantity / notional / fee / trade_role / create_time，user+symbol 索引）
- `FuturesFillEntity` + `FuturesFillMapper`（MyBatis-Plus）
- `FuturesTradeServiceImpl.applyFills`：逐笔成交持久化（含 taker/maker 双方、对手方、名义金额）

### 验证
```
A挂买单(99998889) + B挂卖单(99998890) 交叉 → 成交
t_futures_fill 落 4 条：taker B + maker A + 对家 + 恢复挂单成交
```
- 成交明细、对手方、撮合簿恢复挂单参与撮合 全部验证通过 ✅

## ② HPA 自动扩缩容

### 实现
- `k8s/base/hpa.yaml`：gateway / market / asset / auth 四个**无状态**服务，CPU 利用率 60% 自动扩缩（min 2 / max 4）
- **撮合引擎（order / futures）有内存订单簿**（已持久化到 DB 可重建），但多副本并行撮合需状态分区/同步 → 保持单副本（生产进一步演进见 docs/orderbook-persistence.md）
- Deployment 已有 resources.requests.cpu(250m)，HPA 可直接使用

### 验证
- `kubectl kustomize k8s/base` → 生成 4 个 `HorizontalPodAutoscaler` 资源 ✅
- 注：kind 集群需先装 **metrics-server** 才能采集 CPU 触发 HPA（README/部署文档说明）

## 相关文档
- `docs/orderbook-persistence.md` — 撮合簿持久化（前项）
- `docs/production-gap.md` — 生产差距总览
