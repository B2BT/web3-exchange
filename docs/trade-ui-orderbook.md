# 交易页完整化：深度盘口 + 最近成交 + 下单增强

> 契约文档（PM 定稿）· 前后端并行实施的接口约定与改造要求

## 一、后端新增接口（exchange-order）

### 1. 深度盘口 `GET /api/order/depth`
请求：`?symbol=BTC/USDT&limit=20`（limit 默认 20，最大 50）

响应（`Result<DepthVO>`）：
```json
{
  "code": 200,
  "data": {
    "symbol": "BTC/USDT",
    "bids": [ {"price": 930000000000, "quantity": 500000000}, {"price": 929000000000, "quantity": 800000000} ],
    "asks": [ {"price": 931000000000, "quantity": 300000000}, {"price": 932000000000, "quantity": 400000000} ]
  }
}
```
- `bids` 买盘按价格**降序**，`asks` 卖盘按价格**升序**；各取前 `limit` 档。
- `price` 计价币最小单位（Long），`quantity` 基础币最小单位（Long）——**同一价格档内所有活跃挂单的 remaining 求和**。
- 数据源：`MatchingEngine` 内存盘口（`books` ConcurrentHashMap）→ 遍历 `OrderBook.bids/asks`（TreeMap<price, PriorityQueue<Order>>）每档累加 `Order.remaining`。

### 2. 最近成交 `GET /api/order/recent-trades`
请求：`?symbol=BTC/USDT&limit=50`

响应（`Result<List<TradeVO>>`，按 tradeTime 降序取前 limit）：
```json
{
  "code": 200,
  "data": [
    { "id": "...", "price": 930000000000, "quantity": 100000000, "quoteAmount": 93000000000000000,
      "side": 1, "tradeTime": "2026-08-06 10:00:00", "tradeNo": "...", "symbol": "BTC/USDT" }
  ]
}
```
- `side`：1=主动买（taker 买，用 takerSide），2=主动卖。前端用其着红/绿色。
- 数据源：`t_trade` 表，`TradeMapper` 新增 `selectRecentBySymbol(symbol, limit)`（`ORDER BY trade_time DESC LIMIT #{limit}`）。

### 实现要点（/dev）
- 新增 `OrderBook.depth(int limit)`：聚合每价格档 remaining 求和，返回有序 List；`MatchingEngine.depth(symbol, limit)` 加锁后调用。
- `TradeMapper` 加 `selectRecentBySymbol`；`OrderService` 加 `getDepth` + `listRecentTrades`；`OrderController` 加 `/depth`、`/recent-trades`。
- 新增 `DepthVO`（symbol + List<DepthLevel>bids/asks，DepthLevel={price,quantity}）。
- 保持金额 Long 最小单位；`TradeVO.side` 复用 `takerSide`。这两个接口为公开只读行情，无需 JWT 依赖（可公开，网关已放行 order 公开路由除外——需确认网关白名单，若 /api/order/** 需 token，则前端带 token 调用即可）。
- 编译：`export JAVA_HOME=temurin-17`；`mvn -pl exchange-order -am compile`。

## 二、前端改造（Trade.vue + api/order.ts）

### 新增 API（src/api/order.ts）
```ts
depth(params: { symbol: string; limit?: number })   // GET /order/depth
recentTrades(params: { symbol: string; limit?: number }) // GET /order/recent-trades
```

### 界面布局（主流交易所交易界面）
宽屏（≥1200px）**三栏**：`下单面板 | 深度盘口 | 最近成交`；窄屏堆叠。
保留深色 Web3 主题（套 `.g-card` 玻璃卡片、等宽数字 `tabular-nums`）。

### 下单面板增强
- **可用余额**：买→显示计价币（USDT）可用；卖→显示基础币（BTC）可用。调 `asset accounts`（userId）取可用余额。
- **快捷比例**：25% / 50% / 75% / 100% 按钮——买入按计价币可用×比例填入 quoteAmount 或 price×qty；卖出按基础币可用×比例填入 quantity。
- **最新价**：显示当前 ticker lastPrice（调 `/market/ticker/list` 或从盘口 best bid/ask 推导），限价默认价可用最新价填充。
- **数量⇄金额联动**：输入数量自动算金额（price×qty），输入金额反算数量（除 price，保留精度）。
- 买/卖 tab + 限价/市价逻辑、`toLong/formatLong` 精度换算**保持不变**（勿破坏现有下单 E2E）。

### 深度盘口组件
- 上方卖盘（asks 降序显示，红色数字），下方买盘（bids 升序显示，绿色数字）；或标准样式：中间最新价，卖盘在上、买盘在下。
- 每档显示价格 + 数量；价格用等宽字体，涨绿跌红。
- 点击某档价格 → 自动填入下单面板限价（价格联动）。

### 最近成交组件
- 表格/列表：时间、价格（涨绿跌红）、数量。按 tradeTime 降序，最新在上。
- 数据为空显示空态。

### 验收
- 下单面板：可用余额正确、快捷比例正确填入、价格联动（点盘口/最新价）生效。
- 盘口/成交随成交刷新（下拉刷新或切换交易对重新拉取）。
- `vue-tsc` 源码 0 错误、`npm run build` 通过；深色主题一致；登录/下单功能不破坏。

## 三、演示数据
为让盘口/成交有内容，测试账号撮合多笔限价单（不同价格）形成挂单簿 + 多笔成交；前端登录可见盘口深度、成交记录。
