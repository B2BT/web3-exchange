# 永续合约系统契约 — Phase 3.5

> 作者：PM · 落点：独立模块 `exchange-futures`（端口 8117）
> 定位：对标主流交易所永续合约（USDT 本位永续）。这是收入核心，也是全系统最复杂模块。
> 架构决策：**复用撮合思想（OrderBook + price/time priority），但独立合约订单簿与保证金体系**，因为合约是双向持仓（多/空）以仓位为单位，与现货订单结构、核算逻辑差异大。

## 一、核心概念
- **交易对**：如 `BTC-USDT-SWAP`（USDT 本位永续）。无到期日，可长期持有。
- **方向**：开多（buy/long）、开空（sell/short）、平多、平空。合约天然支持**双向持仓**。
- **杠杆**：1x-100x 可调。保证金 = 仓位名义价值 / 杠杆。
- **保证金模式**：逐仓（isolated，固定保证金，亏损达到后强平）为主，全仓（cross，共享账户余额）可选。
- **标记价格（Mark Price）**：以现货价为基础 + 资金费基差平滑，防止极端插针被操纵强平。**强平用标记价判断，不用最新成交价。**
- **资金费率（Funding Rate）**：每 8 小时结算一次，多头向空头支付（或反之），使合约价锚定现货价。典型 0.01%-0.1%。
- **维持保证金率（MMR）**：维持仓位所需最低保证金比例。触发 → 强平（部分/全部减仓）。

## 二、关键公式
- 名义价值 = 数量 × 标记价
- 初始保证金 = 名义价值 / 杠杆
- 逐仓账户权益 = 初始保证金 + 未实现盈亏 - 已用
- 强平触发：账户权益 < 维持保证金（= 名义价值 × MMR）
- 未实现盈亏（多）= (标记价 - 开仓价) × 数量
- 未实现盈亏（空）= (开仓价 - 标记价) × 数量

## 三、里程碑（M1-M6）
| 里程碑 | 内容 | 交付物 |
|--------|------|--------|
| **M1 合约域基建** | 建表+实体+模块骨架+价格源(标记价=现货ticker加权)+资金费率定时器 | 表：t_swap_contract(交易对)、t_funding_settle、t_mark_price |
| **M2 合约撮合** | 独立合约订单簿，开多/开空/平多/平空，买卖双向撮合（复刻现货 MatchingEngine 思想） | 引擎+接口 |
| **M3 保证金与仓位** | 逐仓保证金核算、仓位开立/增减/平仓、未实现/已实现盈亏 | 表：t_futures_position、t_futures_account |
| **M4 资金费率结算** | 每8小时 funding 定时结算、仓间转移 | 定时任务 |
| **M5 强平引擎** | 标记价盯市、维持保证金触发检测、逐仓强平处置 | 定时任务+接口 |
| **M6 前端合约页** | 合约交易页(方向/杠杆/保证金模式)、持仓面板、资金费率展示 | 页面 |

## 四、接口规划
- 公开：`/api/futures/contracts`（合约列表+资金费率）、`/api/futures/mark/{symbol}`（标记价）
- 需鉴权：`/api/futures/order`（开平仓）、`/api/futures/position`（持仓）、`/api/futures/account`（合约账户）、`/api/futures/close`（平仓）
- admin：`/api/admin/futures/*`（杠杆倍率、资金费率上限等参数）

## 五、表设计
- `t_swap_contract`：symbol、base、quote、price_decimals、qty_decimals、max_leverage、mmr、funding_interval_hours、status
- `t_futures_account`：user_id、coin(USDT)、margin_balance、available_balance、position_margin、unrealized_pnl、updated_at
- `t_futures_position`：user_id、symbol、side(1多/2空)、size(数量)、entry_price、leverage、isolated_margin、liq_price、unrealized_pnl、status
- `t_funding_settle`：symbol、rate、mark_price、funding_time、base_price
- `t_mark_price`：symbol、mark_price、update_time

## 六、风险与安全
- **强平判定用标记价**（非成交价），防插针
- 资金费率以标记价结算，防价差套利操纵
- 杠杆/保证金上限由 admin 配置
- 所有盈亏金额用最小单位 Long 存储，避免浮点

## 七、TODO（P3.5 完成后）
- 全仓保证金模式、逐仓强平的部分减仓、保险基金、风险限额（position tiers）
- 合约深度图/资金费率历史、风险警示（ADR/强平预警）
