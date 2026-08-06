# 进阶订单类型契约（Phase 1.2）

> 作者：PM · 在 exchange-order 域扩展订单类型
> 范围：①撮合策略 Post-Only/IOC/FOK ②条件单 Stop-Limit（止盈止损）③OCO
> 原则：金额/价格一律 Long 最小单位；不破坏现有 限价/市价 下单 E2E

## 一、订单实体扩展（t_order 新增列，sql 新建 `sql/advanced_order.sql`）

新增字段（`Order` 实体 + 建表 DDL，兼容旧数据默认值）：
- `time_in_force` TINYINT 默认 0：`0=GTC(长期有效) 1=IOC(立即成交或取消剩余) 2=FOK(全部成交否则取消) 3=PostOnly(只挂单不吃单)`
- `trigger_type` TINYINT 默认 0：`0=非条件单 1=止盈(涨到触发) 2=止损(跌到触发)`
- `trigger_price` BIGINT 默认 0：触发价（计价币最小单位；条件单必填）
- `trigger_status` TINYINT 默认 0：`0=待触发 1=已触发(激活为普通单) 2=已取消`
- `oco_group` VARCHAR(64) 默认空：OCO 关联组号（同组两单一个触发/成交另一个自动取消）

> 说明：条件单下单时**不入撮合盘口**，status=NEW + trigger_status=0；由行情触发任务激活。

## 二、撮合策略（Post-Only / IOC / FOK）

在 `MatchingEngine.doMatch` 入口按 taker.timeInForce 处理：

- **GTC（默认）**：现有行为（可部分成交、剩余挂单）。
- **PostOnly**：若 taker 会立即与盘口成交（买单遇到价格≤N的卖单 / 卖单遇到价格≥N的买单），则**整单拒绝**（不成交、不挂单），返回原因「PostOnly订单可能立即成交」。撮合前先检测：`(isBuy && bestAsk!=null && bestAsk<=taker.price) || (!isBuy && bestBid!=null && bestBid>=taker.price)` → 拒绝。
- **IOC**：只撮合当前可成交部分，**剩余作废不挂单**（即使限价也如此）。即 doMatch 结束后，taker.remaining>0 时设为「取消剩余」终态，不入簿。
- **FOK**：若不能**全部**成交（现有盘口可满足 total quantity），整单取消；否则全成交。实现：先评估盘口可成交总量，不足则整单拒；满足则照常撮合（自然全成交）。

撮合入口：`MatchingEngine.match` 接收 taker（含 timeInForce），`doMatch` 内策略处理；下单校验时也前置校验（如 PostOnly 无价、FOK 无对手单等）。

## 三、条件单 Stop-Limit（止盈止损）+ 行情触发

### 下单
- `PlaceOrderRequest` 扩展：`timeInForce`、`triggerType`、`triggerPrice`（可选）。
- 若 `triggerType>0`：校验 triggerPrice>0；orderType 仍是 1限价 或 2市价（触发后按此类型撮合）；**status=NEW + trigger_status=0 落库，不入盘口、不冻结？**（建议触发时才冻结，或下单即冻结 triggerPrice 对应的名义额——采用下单即冻结，触发后直接撮合，避免触发时冻结失败）。
- 限价条件单同时校验：市价条件单 trigger 后按市价撮合。

### 行情触发任务（新 @Scheduled）
- 每个交易对，从 market 拿最新价（可经 market 的 ticker REST：`GET /api/market/ticker/list`，取 lastPrice）。简化：轮询市场最新成交价（order 域自身 t_trade 最新价 或 调 market）。
- 触发条件（**用最新成交价/最新价**）：
  - 止盈(triggerType=1)：`latestPrice >= triggerPrice` → 激活
  - 止损(triggerType=2)：`latestPrice <= triggerPrice` → 激活
- 激活：`trigger_status 0→1`，把条件单作为普通单提交撮合（复用 placeOrder 的撮合/冻结链路，但不再重复冻结——若已冻结则直接用冻结额度）。撮合后更新订单终态。
- 激活失败（资金不足等）→ 标记 trigger_status=2(取消) + remark。

### 撮合策略与条件单的关系
- 条件单激活后按其 timeInForce（默认 GTC）撮合。

## 四、OCO（One-Cancels-Other）
- 下单 OCO：一次提交两个条件单（一止盈一止损），同 `oco_group`。
- 触发/成交一个 → 同组另一个自动 `trigger_status=2` 取消（并解冻）。
- 实现：触发任务激活某单时，按 oco_group 更新同组其余单为取消。

## 五、接口
- `POST /api/order/place`：请求体扩展上述字段（timeInForce/triggerType/triggerPrice/ocoGroup）。
- `POST /api/order/cancel`：可取消条件单（未触发）。
- `GET /api/order/get`：返回含新字段的 OrderVO。
- （可选）`GET /api/order/triggered`：查用户已触发/待触发的条件单。

## 六、前端
- 下单面板「订单类型」扩展：
  - **时间策略**：GTC / IOC / FOK / PostOnly（限价单显示）
  - **条件单**：勾选「止盈/止损」，输入触发价（triggerPrice）+ 触发后限价/市价
- `PlaceOrderRequest` 类型扩展字段；Trade.vue 表单加时间策略选择 + 条件单开关/触发价输入。
- 提交时把新字段传给后端；展示下单结果（含条件单待触发提示）。

## 七、验收
- PostOnly 交叉价下单被拒；IOC 部分成交剩余作废；FOK 全成交/全取消。
- 止盈/止损条件单：下单后不入盘口；最新价触发后激活撮合成交；OCO 一个触发另一个取消。
- 现有 限价/市价 GTC 下单行为不变。
- `mvn -pl exchange-order -am compile` BUILD SUCCESS；`vue-tsc` 0 错误 + build 通过。

## 八、实施拆分（PM 分批派工）
- **批次A（本批）**：撮合策略 PostOnly/IOC/FOK + 订单实体/接口扩展 + 前端下单面板时间策略 + 编译验证。
- **批次B（下批）**：条件单 Stop-Limit 触发任务 + OCO + 前端条件单表单 + 联调。
