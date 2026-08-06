# WebSocket 实时行情推送契约

> 作者：PM · MVP 范围：ticker + K线 实时推送（market 域自有数据，无跨域依赖）
> 后续增强：盘口/最近成交实时（需 market 聚合 order 数据）

## 一、连接
- 端点：`ws://<host>/api/market/ws`（经网关 8080 代理；也支持直连 `8106/ws`）
- 协议：文本帧，JSON。首帧不鉴权（公开行情），无需 token。

## 二、客户端 → 服务端（订阅消息）
```json
{ "op": "subscribe",   "channel": "ticker", "symbol": "BTC/USDT" }
{ "op": "subscribe",   "channel": "kline",  "symbol": "BTC/USDT", "period": "1m" }
{ "op": "unsubscribe", "channel": "ticker", "symbol": "BTC/USDT" }
{ "op": "ping" }   // 服务端回 pong
```
- `channel` ∈ { `ticker`, `kline` }
- 同一条连接可订阅多个 channel；重复订阅幂等。

## 三、服务端 → 客户端（推送）
### ticker（约 1s 一次，仅在有人订阅该 symbol 时推）
```json
{ "channel": "ticker", "symbol": "BTC/USDT", "data": { "lastPrice":930000000000, "change24h":120, "high24h":955000000000, "low24h":925000000000, "volume24h":3000000000, "quoteVolume24h":2790000000000 } }
```
`data` 即现有 `TickerVO` 字段（Long 最小单位）。

### kline（约 1s 推一次最新一根，仅订阅时推）
```json
{ "channel": "kline", "symbol": "BTC/USDT", "period": "1m", "data": { "openTime":1728000000000, "open":930000000000, "high":931000000000, "low":929000000000, "close":930500000000, "volume":100000000, "quoteVolume":93000000000000 } }
```
`data` 即现有 `KlineVO`（含 interval/openTime/open/high/low/close/volume/quoteVolume）。

### 订阅确认 / 错误
```json
{ "channel": "subscribed", "channelName": "ticker:BTC/USDT" }
{ "channel": "error", "message": "不支持的 channel 或参数" }
```

## 四、后端实现（exchange-market）
- 用 **Spring WebSocket**（`spring-boot-starter-websocket`）：`WebSocketConfigurer` 注册 `/ws`，`TextWebSocketHandler` 解析订阅 JSON，维护 `Map<Session, Set<String>>`（session→订阅 key 如 `ticker:BTC/USDT`）。
- **定时推送**：一个 `@Scheduled(fixedRate=1000)` 任务，遍历活跃订阅，从 `MarketAggregator`（已有 getTicker/getKlines）取数据，写回对应 session（`session.isOpen()` 校验，异常关闭清理）。
- 序列化：用项目 ObjectMapper（Long 序列化为 number，价格/金额保留 number；仅 id 类转 String，本处无 id）。
- 心跳：读 `ping` 回 `pong`；session 空闲超时（如 60s）主动关闭。
- gateway：加一条 ws 路由 `/api/market/ws` → `lb://exchange-market`（Spring Cloud Gateway 原生支持 WebSocket 转发）。若配置复杂，前端可直连 `ws://127.0.0.1:8106/ws` 兜底。

## 五、前端实现
- 新建 `src/api/marketWs.ts`：封装 WebSocket 连接 + 订阅/取消 + 事件回调（`onTicker(symbol,data)` / `onKline(symbol,period,data)`），断线自动重连（指数退避）。
- **行情页 Market.vue**：ticker 卡片 + K线图 改为 ws 实时更新（订阅当前 symbol 的 ticker + kline），替换/叠加现有 3s 轮询。
- **交易页 Trade.vue**：最新价实时（订阅 ticker），更新下单面板最新价。
- 页面卸载时取消订阅、关闭连接；切交易对重新订阅。
- 不破坏现有 REST 逻辑（ws 失败自动回退轮询）。

## 六、验收
- 行情页 K线/ticker 无需刷新实时跳动；交易页最新价实时。
- 订阅/取消正确，断开重连生效；服务端无异常日志。
- `vue-tsc` 0 错误 + `build` 通过；market 编译 `mvn -pl exchange-market -am compile` BUILD SUCCESS。
