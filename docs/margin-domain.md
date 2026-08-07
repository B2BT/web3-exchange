# 杠杆现货（Margin）契约 — Phase 2.2

> 作者：PM · 对标 Binance Margin / OKX 杠杆
> 定位：在现货基础上提供**杠杆账户**，支持借币/还币/抵押/强制平仓 + 日利率计息
> 落地：新增 `exchange-margin` 模块（端口 **8110**），复用 asset 资金契约 + order 撮合

## 一、目标功能（MVP 范围）
- **杠杆账户**：每用户每币种一个杠杆账户（独立于现货账户），含抵押、借入、利息。
- **借币**：用户以抵押（quote 计价）借入 base 或 quote，放大本金下单。
- **还币**：归还本金 + 应付利息。
- **利率**：日利率（按 365 天，T+1 起息），定时任务累计。
- **强制平仓**：当抵押价值 / 借入负债 < 维持保证金率时触发强平，用抵押回购负债。
- **资金划转**：现货账户 ⇄ 杠杆账户（抵押入金/出金）。

## 二、后端设计（exchange-margin 模块，端口 8110）

### 2.1 新表
```
t_margin_account   -- 杠杆账户
  user_id, symbol,
  collateral      -- 抵押(quote计价, 最小单位)
  borrowed        -- 借入本金(最小单位)
  interest_accrued-- 未还利息(最小单位)
  status          -- 0=禁用 1=正常
  uk_user_symbol(user_id,symbol)

t_margin_loan     -- 借币记录
  user_id, symbol, amount(借入本金), rate_daily(日利率),
  principal_remain(剩余本金), interest_accrued(该笔利息),
  status(0=借出中 1=已还清), open_time, repay_time
  uk_idempotent(请求幂等)

t_margin_interest_rate  -- 币种日利率配置
  symbol, rate_daily(如 0.0001 = 万分之一/日), status
```

### 2.2 接口（/api/margin/**，需登录）
| 方法 | 接口 | 说明 |
|------|------|------|
| POST | /api/margin/account/open | {userId,symbol} 幂等开户 |
| POST | /api/margin/transfer-in | 现货 available → 杠杆 collateral（抵押入金） |
| POST | /api/margin/transfer-out | 杠杆 collateral → 现货 available（出金，须有富余抵押） |
| POST | /api/margin/borrow | {userId,symbol,amount} 借入（按 rate_daily 计息） |
| POST | /api/margin/repay | {userId,symbol,amount} 还本金+利息 |
| GET  | /api/margin/account | {userId,symbol} 杠杆账户详情（collateral/borrowed/interest/可借） |
| GET  | /api/margin/loans | {userId} 借币记录分页 |

### 2.3 计息与强平（@Scheduled）
- **计息**：每整点扫描 `status=0 且 principal_remain>0` 的借单，`interest = principal_remain * rate_daily / 24` 累计到该笔与账户。
- **强平**：每 5 分钟扫账户，若 `collateral / borrowed < maintenance_ratio(如 1.2)`，触发强平——用抵押折价（95%）回购借入负债，剩余抵押退回。

### 2.4 资金衔接
- 抵押入金：asset `freeze` 现货 → 本模块 collateral 增加（用平台中转，requestId=`MARGIN_IN:{id}`）。
- 借币/还币/强平仅改本模块表（杠杆是独立资金，不强碰 asset 现货），本 MVP 简化：借入金额由平台资金池（配置平台用户）垫付。
- 金额 Long 最小单位，requestId 幂等。

## 三、前端（/margin 页，深色主题）
- 杠杆账户卡片：抵押 / 借入 / 利息 / 可借 / 风险率
- 借币表单（币种、金额）、还币表单、抵押入金/出金
- 借币记录表
- 资产页与 margin 页互相跳转

## 四、测试（api_test.py 增 margin 用例）
- 开户幂等、抵押入金/出金、借币、还币（本金+利息）、账户详情、风险率计算
- 强平：mock 低抵押 → 触发强平断言
- 报告 docs/test-reports/

## 五、里程碑拆解
- M1：表 + 开户 + 抵押入金/出金 + 账户查询（资金闭环）
- M2：借币/还币 + 计息任务
- M3：强平任务 + 前端 /margin 页
- M4：测试 + 报告
