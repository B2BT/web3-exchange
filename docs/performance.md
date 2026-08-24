# 高并发性能压测报告

> 高并发架构「阶段 A」验证：撮合按币对分实例 + 读路径缓存 + wrk 压测。
> 环境：Mac 本机，后端服务本地运行，wrk 4.2.0。

## 一、撮合按币对分实例

**现状（已天然支持）**：现货 `MatchingEngine` 与合约 `FuturesMatchingEngine` 均用
`ConcurrentHashMap<String, OrderBook>`，**每个交易对一个独立订单簿 + 每对 ReentrantLock 串行化**，
不同币对互不阻塞，天然支持按币对扩展实例。

- 现货：`MatchingEngine` (exchange-order)
- 合约：`FuturesMatchingEngine` (exchange-futures)

## 二、读路径缓存（本次新增）

`OrderService` 加 Caffeine 缓存：
| 接口 | 缓存 | 原因 |
|------|------|------|
| `GET /api/order/depth` | 300ms | 盘口高频轮询，避免重复内存聚合 |
| `GET /api/order/recent-trades` | 2s | DB 读，避免高频查询打 DB |

（Caffeine 已存在于 exchange-order pom）

## 三、wrk 压测结果（200 并发 / 8-10s，8 线程）

| 接口 | 方式 | 吞吐(req/s) | 平均延迟 |
|------|------|------------|---------|
| `order/depth` | 缓存 300ms | **21,772** | 12.6ms |
| `order/recent-trades` | 缓存 2s | **9,931** | 28.0ms |
| `market/ticker/list` | 聚合(网关) | 9,479 | 23.1ms |
| `asset/balance` | **未缓存(DB直查)** | 1,648 | 102.6ms |
| `asset/balance` | **缓存 2s(Caffeine)** | ***31,738*** | 6.93ms |
| `order/place`(下单) | **写路径**(冻结+撮合+落库+MQ) | **870** | 149ms |

### 结论
- **读路径缓存显著有效**：depth 未缓存对照 1.6k → 缓存后 2.1万（~13x）；asset balance 1.6k → **3.1万（~19x）**，延迟 102→7ms
- **读快写慢是常态**：下单写路径 870 req/s（完整冻结+撮合+事务+MQ），与读 3 万形成对比——优化写需下单写吞吐（事件驱动/批量）

## 四、资产账户读缓存（本次新增）

`AccountServiceImpl` 加 Caffeine 2s 缓存（getBalance / listByUser），资金变动 `doChange` 后主动 `invalidate(userId)` 保证实时一致：
- `getBalance` / `listByUser`：Caffeine 2s
- 失效：`LedgerService.doChange`（所有资金变动唯一入口）事务内更新后调用

## 五、瓶颈与建议

### 当前瓶颈
1. **下单写路径** 870 req/s（受限于全链路事务：Feign 冻结 + 撮合 + 落库 + MQ）
2. 未做分库分表（单 MySQL）

### 建议（阶段 B/C）
- asset 账户读已缓存 ✅（下一步：行情/账户走 Redis 分布式缓存 + 写穿）
- **下单写路径优化**：每交易对专用线程 + 事件驱动撮合，减少 Feign 往返
- 行情 WebSocket 增量/合并推送


## 六、归档
- 代码：`OrderService` 读缓存（exchange-order）、`AccountServiceImpl` 读缓存（exchange-asset）
- 相关 `docs/production-gap.md`、`docs/performance.md`
