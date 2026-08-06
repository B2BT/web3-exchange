# 我的挂单 / 历史订单页契约（Phase 1.4）

> 作者：PM · exchange-order 域
> 目标：把订单查询从"按单号查单个"升级为"按用户分页列表"，前端订单页完整化

## 一、后端接口（exchange-order）

### `GET /api/order/list`
请求：`?userId=<id>&status=&page=1&size=20`
- `status` 可选：`0=NEW(待成交) 1=PARTIAL(部分成交) 2=FILLED(已完成) 3=CANCELLED(已取消) 4=REJECTED(已拒绝)`
- 传 `status` 按状态过滤；不传查全部。`page` 默认1, `size` 默认20, 上限100。
响应：`Result<PageData<OrderVO>>`（复用 exchange-common 的 PageData）
```json
{ "code":200, "data":{ "total":58, "current":1, "size":20, "pages":3,
  "records":[ { "id":"...", "orderNo":"...", "symbol":"BTC/USDT", "side":1, "orderType":1,
    "price":930000000000, "quantity":100000, "remaining":100000, "filledAmount":0,
    "avgPrice":0, "status":0, "timeInForce":0, "triggerType":0, "triggerStatus":0,
    "createTime":"2026-08-06 10:00:00" } ] } }
```
- 实现：OrderMapper 新增 `selectByUserId(userId,status,limit,offset)` + count（MyBatis-Plus lambdaQuery on user_id + createTime DESC，status 可选），转 PageData<OrderVO>。
- 安全：userId 校验必须等于 JWT 的用户（网关已注入 X-User-Id，可校验；或信任前端传参——因当前无严格鉴权，按现有 /get 同款处理）。

## 二、前端（Order.vue 完整化）
- 当前 Order.vue 是按 orderNo 查单个。改为**分页订单列表**：
- **两个 tab**：
  - **当前委托**：status 0,1（待成交/部分成交）——显示"可撤单"操作（调 /api/order/cancel）
  - **历史订单**：status 2,3,4（已完成/已取消/已拒绝）
- 表格列：时间 / 交易对 / 方向(买绿卖红) / 类型(限价/市价) / 价格 / 数量 / 已成交 / 状态(中文) / 操作(撤单仅当前委托)
- 状态中文：0待成交 1部分成交 2已完成 3已取消 4已拒绝
- 撤单：调现有 cancel API，成功后刷新当前委托列表。
- 分页：el-pagination（page/size 可调），切 tab/翻页重拉；加载态/空态；保持深色主题。
- 保留按单号查询入口（可选，如顶部输入框 + 查询按钮，调 /api/order/get）。
- api/order.ts 新增 `listOrders(params:{userId,status?,page,size})` → GET /order/list。

## 三、验收
- 接口按 userId/status 分页返回订单（createTime DESC）。
- 前端当前委托/历史订单两 tab 分页正确，撤单刷新；状态/方向着色正确。
- 不破坏下单流程。
- `mvn -pl exchange-order -am compile` BUILD SUCCESS；`vue-tsc` 0 错误 + build 通过。
