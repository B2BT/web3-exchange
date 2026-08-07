# Staking / Earn 契约 — Phase 2.3

> 作者：PM · 对标 Binance Earn / OKX 赚币
> 定位：在现货基础上提供**质押理财**：活期/锁仓产品、质押/赎回、年化收益定时结算
> 落地：新增 `exchange-staking` 模块（端口 **8112**），复用 asset 资金契约（freeze 质押锁仓 / credit 收益入账）

## 一、目标功能（MVP 范围）
- **质押产品**：活期（随时赎回）与锁仓（到期可赎），配置年化利率。
- **质押**：用户将现货可用余额冻结质押，换取本金 + 按日计息的收益。
- **赎回**：活期随时赎（本金+已结收益），锁仓到期可赎。
- **收益结算**：定时任务按日利率累计收益，每日结算入账（credit）。
- **记录**：质押记录 + 收益流水查询。

## 二、后端设计（exchange-staking 模块，端口 8112）

### 2.1 新表
```
t_staking_product   -- 质押产品
  product_code, name, type(0=活期 1=锁仓), symbol,
  annual_rate_bp    -- 年化利率(基点,10000=100%; 500=5%)
  min_amount, lock_days(锁仓天数,活期为0), status(0=下架 1=上架)

t_staking_position  -- 用户质押持仓
  user_id, product_code, symbol, amount(质押本金),
  accrued_interest(累计未结收益), total_interest(累计已结收益),
  status(0=质押中 1=已赎回), start_time, lock_end_time, redeem_time
  uk_user_product(user_id, product_code)

t_staking_interest  -- 收益结算流水
  user_id, position_id, symbol, amount(本次结算收益), settle_date, remark
```

### 2.2 接口（/api/staking/**，需登录）
| 方法 | 接口 | 说明 |
|------|------|------|
| GET  | /api/staking/products | 产品列表 |
| POST | /api/staking/stake | {userId, productCode, amount} 质押（asset freeze 锁仓） |
| POST | /api/staking/redeem | {userId, productCode, amount} 赎回（本金+已结收益 → asset credit/unfreeze） |
| GET  | /api/staking/positions | {userId} 我的持仓 |
| GET  | /api/staking/interests | {userId} 收益流水分页 |

### 2.3 收益结算（@Scheduled 每日）
- 每持仓：`daily_interest = amount * annual_rate_bp / 10000 / 365` 累计到 `accrued_interest`。
- 每日结算任务把 `accrued_interest` 结转：写 `t_staking_interest`，调 asset `credit` 入账（requestId=`STK_INT:{positionId}:{date}` 幂等），`accrued_interest→0`、`total_interest+=`。

### 2.4 资金衔接
- 质押：asset `freeze`（requestId=`STK_STAKE:{id}`）锁用户现货。
- 赎回：本金 → asset `unfreeze`；已结收益已在每日结算入账（credit），赎回时仅退回本金。
- 金额 Long 最小单位，requestId 幂等。

## 三、前端（/staking 页，深色主题）
- 产品列表（活期/锁仓、年化、最小额）
- 质押表单、赎回表单
- 我的持仓表 + 收益流水表

## 四、测试（api_test.py 增 staking 用例）
- 产品列表、质押（现货减少）、收益结算入账、赎回（本金退回）、持仓/收益查询
- 报告 docs/test-reports/
