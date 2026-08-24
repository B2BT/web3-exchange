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

## 三、wrk 压测结果（200 并发 / 10s，8 线程）

| 接口 | 方式 | 吞吐(req/s) | 平均延迟 |
|------|------|------------|---------|
| `order/depth` | 缓存 300ms | **21,772** | 12.6ms |
| `order/recent-trades` | 缓存 2s | **9,931** | 28.0ms |
| `market/ticker/list` | 聚合(网关) | 9,479 | 23.1ms |
| `asset/account` | **未缓存(DB直查)** | ***1,648*** | 102.6ms |

### 结论
- **读路径缓存显著有效**：encache depth 是未缓存对照的 **~13 倍**，recent-trades **~6 倍**
- 未缓存 DB 直查(account)吞吐仅 1.6k，是明显瓶颈 → **下一步应对账户/资产读也加缓存**

## 四、瓶颈与建议

### 当前瓶颈
1. **DB 直查读接口**（asset account / order 分页等）未缓存 → 吞吐最低
2. 未做分库分表（单 MySQL）

### 建议（阶段 B）
- asset 账户读加 Caffeine + Redis（写穿缓存）
- 行情推送 WebSocket 增量/合并优化
- 下单写路径：解锁写吞吐（当前写走事务+MQ，改用每币对专门线程/事件驱动）

## 五、归档
- 代码：`OrderService` 读缓存（exchange-order）
- 相关 `docs/production-gap.md`、`docs/performance.md`
