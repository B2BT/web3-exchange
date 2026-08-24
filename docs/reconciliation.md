# 资金对账引擎（账实核对）

> 第三梯队·业务健壮性 / 资金安全。定期核对账户余额与账本流水的账实一致性，
> 自动发现资金偏差（总账不平 / 账户余额与流水分录不一致），是 CEX 审计与资金安全的最后防线之一。

## 对账规则

对每个启用账户执行两类核对：

1. **内部不变式**：`total == available + frozen`（余额三步恒等式）
   - 违反 → 账户内部数据损坏，报不平衡

2. **账实一致**：账户当前 `available/frozen` == 该账户**最新一笔记账流水**的 `after_available/after_frozen`
   - 违反 → 某笔资金操作后的状态未正确落到账户（可能漏更新/重复扣减）

## 实现

- `ReconciliationService`（exchange-asset/service/impl）
  - `runReconcile()`：遍历账户核对，返回报告（核对数 + 不平衡明细）
  - `@Scheduled(fixedRate=300_000)`：每 5 分钟自动对账，发现不平衡打 ERROR 日志
- `@EnableScheduling` 加到 `AssetApplication`
- 接口：`GET /api/asset/reconcile`（手动触发，返回报告）

## 验证

```
GET /api/asset/reconcile
→ 核对 68 个账户，不平衡 0
```
说明当前全部账户账实一致：余额三步恒等式成立、账户值与最新流水吻合，
资金体系（冻结/解冻/过户/入账）无偏差。

## 生产进一步（可选）

1. **跨系统对账**：交易所内部余额 vs 链上链下持仓（冷/热钱包链上余额核对）
2. **每日清零/清算**：对账报告入库落审计，与清算日结联动
3. **对账差异告警**：接监控告警（Prometheus）触发，超阈值自动熔断提现

## 相关文档
- `docs/production-gap.md` — 生产差距总览
- `docs/performance.md` — 性能（读缓存/写优化）
