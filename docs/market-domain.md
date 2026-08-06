# 行情域（Phase 4）落地设计：消费 ORDER-TRADE 聚合 K线 / ticker，实时行情服务

> 版本：v1.0 · 作者：系统架构师 · 日期：2026-08-06
> 适用：`exchange-market`（Nacos 服务名 `exchange-market`，端口 **8106**，**新建模块**）落地依据。
> 定位：本文件是 `docs/ARCHITECTURE.md` 附录「交易域/行情」的**落地细化**（消费订阅、内存 K线/ticker 聚合算法、REST 契约），与架构蓝图及 `docs/mq-topics.md` 保持一致，供 `/dev` 直接照此实现。**本文件为新建，不改动 `docs/` 既有文档、不改动 `sql/*.sql`、不改动既有模块任何 Java 代码、不执行 git 提交。**
> 兼容基线：Spring Boot 3.2.5 / Spring Cloud Alibaba 2023.0.1.0 / Java 17 / MyBatis-Plus 3.5.7 / MySQL 8（本期可选）/ Redis 7 / 统一 `Result<T>` / RocketMQ `ORDER-TRADE` 主题（见 `docs/mq-topics.md`）。
> 依赖：**行情数据源 = `exchange-order` 撮合成交后发布的 `ORDER-TRADE` 事件**（消息体 `TradeSettleDTO`，位于 `exchange-common`，见 §2.1）。订单簿深度/盘口**不在本域**，见 §6 边界说明。

---

## 目录

1. [总体设计要点](#一总体设计要点)
2. [消费契约：ORDER-TRADE 成交事件](#二消费契约order-trade-成交事件)
3. [内存 K线聚合（OHLCV）](#三内存-k线聚合ohlcv)
4. [ticker 聚合](#四ticker-聚合)
5. [REST / 网关路由契约](#五rest--网关路由契约)
6. [边界与范围（深度/盘口归属 order 域）](#六边界与范围深度盘口归属-order-域)
7. [DB 取舍与可选持久化](#七db-取舍与可选持久化)
8. [落地 Checklist（/dev 实施指引）](#八落地-checklistdev-实施指引)

---

## 一、总体设计要点

- **职责定位**：消费 `ORDER-TRADE` 成交事件，**实时聚合 K线 / ticker**，供前端/外部通过 REST 查询。只读行情服务，**不产生任何资金/订单写操作**。
- **内存聚合为主（本期）**：K线、ticker 全部驻留 JVM 内存（`ConcurrentHashMap`），成交流水驱动、按时间窗口切分。**重启即重建**（启动后重新消费或回放近期成交即可恢复，见 §7）。本期**不建表、不落库**。
- **精度口径（与资产/订单域完全一致）**：`price` / `quoteAmount` 为**计价币最小单位**（Long，如 USDT=1e6），`quantity` 为**基础币最小单位**（Long，如 BTC=1e8）。行情内部一律 Long 整数运算，对外 REST 由展示层按 `t_coin.decimals` / `t_symbol` 精度换算，**禁止 double/float**。
- **并发模型**：同一 symbol 的成交流量高，K线聚合采用 `ConcurrentHashMap.compute(key, f)`（对单个 key 原子），天然保证同一 K线窗口的 OHLC 更新不丢、不乱；不同 symbol/周期互不阻塞。
- **消费独立性**：market 使用**独立消费组** `market-order-trade-group` 订阅 `ORDER-TRADE`，与 asset 的 `asset-order-trade-group`、notify 的 `notify-order-trade-group` 互不影响（RocketMQ 同主题不同消费组各自消费，见 `docs/mq-topics.md`）。
- **接口分层**：对外 REST 走网关（`/api/market/**`）；**内部不提供写接口**，深度/盘口由 order 域提供（见 §6）。
- **表结构/命名规范**：本期不建表；若启用可选持久化（§7）则遵循既有 `t_` + BaseEntity 系统字段 + 中文注释 + `uk_*`/`idx_*` 规范。

---

## 二、消费契约：ORDER-TRADE 成交事件

### 2.1 消息体（复用 `exchange-common` 的 `TradeSettleDTO`）

> 生产者：`exchange-order`（撮合成交后发事务消息，`docs/order-domain.md §5.4`）。消息体为 `TradeSettleDTO` 序列化 JSON，`KEYS = tradeNo`。

| 字段 | 类型 | 语义 | 行情用途 |
|------|------|------|---------|
| `tradeNo` | String | 成交单号（全局唯一，消息 KEYS） | 去重/日志 |
| `symbol` | String | 交易对，如 `BTC/USDT` | **K线/ticker 主键维度** |
| `baseCoin` / `quoteCoin` | String | 基础币 / 计价币 | 精度换算、展示 |
| `price` | Long | 成交价（**计价币最小单位**） | **K线 OHLC / ticker 最新价** |
| `quantity` | Long | 成交量（**基础币最小单位**） | **K线 volume / ticker 24h 量** |
| `quoteAmount` | Long | 名义值 = price×quantity（计价币最小单位） | **K线 quoteVolume / ticker 24h 额** |
| `buyUserId` / `sellUserId` | Long | 买卖方用户 | （本域暂不用，供调试） |
| `takerOrderNo` / `makerOrderNo` | String | 吃单/挂单号 | （本域暂不用） |

> 精度约定重申：`price` 与 `quoteCoin` 精度一致（如 USDT=6 → 1.23 USDT = 1230000）；`quantity` 与 `baseCoin` 精度一致（如 BTC=8）。聚合时**全部按原始 Long 直接累加/比较**，不做除法。

### 2.2 消费实现要点

```java
@Component
@RocketMQMessageListener(
        topic = "ORDER-TRADE",
        consumerGroup = "market-order-trade-group",   // 独立消费组，见 mq-topics.md 规范
        selectorExpression = "*"                        // 订阅全部 Tag（本主题无细分 Tag 或全量订阅）
)
public class OrderTradeMarketConsumer implements RocketMQListener<MessageExt> {
    // 解析 body → TradeSettleDTO
    // 校验 tradeNo/symbol/price/quantity 非空
    // 调用 MarketAggregator.onTrade(dto)  更新 K线 + ticker
}
```

- **消息 KEYS** = `tradeNo`；消费失败抛异常触发重投（默认 16 次进死信）。行情为**可重放聚合**（重复聚合同一笔成交对 K线结果不变，见 §3 幂等性），重投安全。
- 消费层去重（`mq:dedup:ORDER-TRADE:market:{tradeNo}`）**可做可不做**——因为 OHLC 更新是幂等收敛的（同 trade 重复 compute 结果一致），建议**加一层 Redis SETNX** 减少无效 compute，但**不强依赖**（与 asset 的 `TradeSettleConsumer` 同模式）。

---

## 三、内存 K线聚合（OHLCV）

### 3.1 数据结构

```java
// 时间周期枚举（毫秒）
enum KlineInterval {
    M1(60_000L), M5(300_000L), M15(900_000L),
    H1(3_600_000L), H4(14_400_000L), D1(86_400_000L);
    final long millis;
}

// K线一行（不可变语义，更新时整体替换）
public class Kline {
    private final String symbol;
    private final String interval;      // "1m"/"5m"/"15m"/"1h"/"4h"/"1d"
    private final Long openTime;        // 窗口开始时间(epoch millis)
    private Long open, high, low, close;      // 均为计价币最小单位
    private Long volume;                      // 基础币最小单位
    private Long quoteVolume;                 // 计价币最小单位
}

// 聚合器：symbol:interval:openTime -> Kline
// 外层 ConcurrentHashMap<String(symbol), Map<String(interval), ConcurrentHashMap<Long, Kline>>>
// 用 ConcurrentHashMap.compute(key, ...) 保证单 key 原子更新
public class MarketAggregator {
    ConcurrentHashMap<String, ConcurrentHashMap<String, ConcurrentHashMap<Long, Kline>>> store;
}
```

### 3.2 时间窗口切分

窗口对齐到**UTC 整点边界**（行情统一 UTC，前端按本地时区换算）：

```
openTime(symbol, interval, tradeTimeMs) = (tradeTimeMs / interval.millis) * interval.millis
```

- `tradeTimeMs` = 成交事件时间；本期用消费时系统时间 `System.currentTimeMillis()`（与 `ORDER-TRADE` 生产时差极小，可接受；后续可在 DTO 增加 `tradeTime` 精确字段）。
- 示例：`tradeTimeMs=2026-08-06T10:00:45Z`，`1m` → openTime = `10:00:00`；`1h` → openTime = `10:00:00`；`D1` → openTime = `00:00:00`。

### 3.3 更新算法（成交流水驱动）

对每笔成交 `(symbol, price, quantity, quoteAmount)`，对每个已启用 interval 执行：

```java
void onTrade(TradeSettleDTO dto) {
    for (KlineInterval iv : enabledIntervals) {
        long key = (nowMs / iv.millis) * iv.millis;               // openTime
        klineMapFor(symbol, iv).compute(key, (k, cur) -> {
            if (cur == null) {
                return new Kline(symbol, iv.name, key,
                        dto.price, dto.price, dto.price, dto.price,  // open=high=low=close
                        dto.quantity, dto.quoteAmount);
            }
            cur.high = Math.max(cur.high, dto.price);
            cur.low  = Math.min(cur.low,  dto.price);
            cur.close = dto.price;                    // 最新成交价即收盘价
            cur.volume += dto.quantity;
            cur.quoteVolume += dto.quoteAmount;
            return cur;
        });
    }
}
```

**规则要点**：
- **open**：该窗口**首笔**成交价（窗口新建时定格）。
- **high / low**：该窗口内成交价的**历史最值**（逐笔 max/min）。
- **close**：该窗口内**最新一笔**成交价（覆盖式更新）。
- **volume / quoteVolume**：窗口内 `quantity` / `quoteAmount` 的**累加**。
- **窗口自然滚动**：时间跨入下一窗口后，compute 命中 null 自动开新窗，旧窗口对象仍留在 map（供查询历史）。为避免内存无限增长，对**过旧窗口**（如已滚出 `D1` 之外）做惰性清理（见 §3.4）。
- **幂等收敛**：同一 `tradeNo` 重复消费时，`volume/quoteVolume` 会重复累加 —— **因此必须以 tradeNo 去重一次**（Redis SETNX）或仅在首见时累加量、OHLC 用幂等覆盖式更新。为简单且严格正确，本期**推荐消费层对 tradeNo 做 SETNX 去重**（与 §2.2 一致），保证每个 tradeNo 只累计一次 volume。

### 3.4 内存管理（可选，本期可简化）

- **保留窗口数上限**：每个 `(symbol, interval)` 最多保留最近 N 个窗口（如 M1=720 即 12h、D1=90）。超过时移除最老窗口，用 `ConcurrentHashMap` 的 `removeIf`（JDK 无，需遍历或按需）——**本期实现为「查询时过滤」即可**，不做主动淘汰（见 §7 简化）。
- 也可引入定时任务（如每分钟一次）清理已滚动窗口，属增强项。

---

## 四、ticker 聚合

> 提供每个 symbol 的**实时快照**：最新价 / 24h 涨跌幅 / 24h 量额 / 24h 最高最低。可简化实现。

### 4.1 数据结构

```java
public class Ticker {
    private String symbol;
    private Long lastPrice;        // 最新成交价(计价币最小单位)
    private Long openPrice;        // 24h 前首笔成交价(用于涨跌幅)
    private Long high24h, low24h;  // 24h 最高/最低
    private Long volume24h;        // 24h 成交量(基础币最小单位)
    private Long quoteVolume24h;   // 24h 成交额(计价币最小单位)
    private Long change24h;        // 涨跌幅(基点 bp，整数：10000=100%)
    private Long count24h;         // 24h 成交笔数(可选)
}
// 容器：ConcurrentHashMap<String, Ticker>   key = symbol
```

### 4.2 更新方式（两种，推荐方案一）

**方案一（推荐·由 K线滚动推导，本期简化）**：直接基于 `D1`/当前滚动窗口聚合，**避免维护独立 24h 滑窗**。做法：
- `lastPrice` / `high24h` / `low24h` / `volume24h` / `quoteVolume24h` 由「当前打开的 1d 窗口 + 近 24h 已关窗口」的 K线合并得到（本期**简化为直接读当日 `D1` 窗口 + 保留前 1 个 `D1` 窗口**，近似 24h）。
- `change24h = (lastPrice - openPrice24h) * 10000 / openPrice24h`（`openPrice24h` 取 24h 首笔价，本期取当日 D1 窗口 open）。

**方案二（精确滑窗·增强项，标注）**：维护每 symbol 一个**环形双端队列**，存入近 24h 内每笔成交 `(ts, price, quantity, quoteAmount)`，按时间滚动出队，实时计算 high/low/volume/quoteVolume。内存与计算开销高，**本期不做**。

> 结论：本期采用**方案一**，ticker 直接从 K线聚合结果派生，实现最简、一致性最好；精确 24h 滑窗标注为 Phase 5 增强。

---

## 五、REST / 网关路由契约

> 统一返回 `com.web3.exchange.common.model.Result<T>`。`price/quantity/quoteAmount` 一律返回 **Long 最小单位**（展示层换算），**不返回浮点**。

### 5.1 对外 REST（经网关 `/api/market/**`）

| 方法 | 接口 | 请求参数 | 返回 | 说明 |
|------|------|---------|------|------|
| K线列表 | `GET /api/market/kline/list` | `symbol`（必填）、`interval`（`1m/5m/15m/1h/4h/1d`，必填）、`limit`（默认 200，最大 1000） | `Result<List<KlineVO>>` | 按时间升序返回最近 N 根 K线 |
| ticker 列表 | `GET /api/market/ticker/list` | — | `Result<List<TickerVO>>` | 全部交易对实时 ticker |
| 单 ticker | `GET /api/market/ticker/{symbol}` | — | `Result<TickerVO>` | 按 symbol 查；不存在返回 `notFound` |

**VO 定义（示例）**：

```java
public class KlineVO {
    private String symbol;
    private String interval;      // "1m"
    private Long openTime;        // epoch millis(UTC 窗口起点)
    private Long open, high, low, close;    // 计价币最小单位
    private Long volume;          // 基础币最小单位
    private Long quoteVolume;     // 计价币最小单位
}

public class TickerVO {
    private String symbol;
    private Long lastPrice;
    private Long change24h;       // 基点 bp
    private Long high24h, low24h;
    private Long volume24h;
    private Long quoteVolume24h;
}
```

### 5.2 网关路由（标注，`/dev` 在 gateway application.yml 新增）

> market 服务**无 context-path**，Controller 映射为 `/api/market/***`，网关直接转发（同 `user-service` 风格），**不需要 RewritePath/StripPrefix**。

```yaml
# exchange-gateway/src/main/resources/application.yml 新增路由（/dev 落地时执行，本设计不改文件）
- id: market-service
  uri: lb://exchange-market
  predicates:
    - Path=/api/market/**
```

> 同时可开启预留的 WebSocket 路由 `/ws/**`（`lb://exchange-market`）做实时推送（Phase 5 增强，本期仅 REST 轮询）。

---

## 六、边界与范围（深度/盘口归属 order 域）

- **订单簿深度/盘口快照在 `exchange-order`**（内存撮合引擎维护 `OrderBook`，见 `docs/order-domain.md §5.2`），**不属于行情域职责**。
- 本域（`exchange-market`）**只做**：聚合成交 K线 + ticker。深度数据如需对外，由 **order 域提供内部接口**（如 `GET /internal/order/depth?symbol&limit`，Phase 后续落地），market **不复制、不维护订单簿**。
- **本期范围明确**：K线 + ticker 为**必做**；深度/盘口**标注为 order 域能力**，market 不实现、不依赖。若后续需要「聚合行情 + 深度」一体化对外，由网关聚合或前端分别调用 `/api/market/**` 与 order 深度接口。

---

## 七、DB 取舍与可选持久化

- **本期（必做基线）：纯内存，无表、无持久化。** 重启后 K线/ticker 为空，需重新消费 `ORDER-TRADE` 重建（启动后从 `exchange-order` 的历史 `t_trade` 回放，或接受「重启后逐步重建」的降级）。这符合「行情可重放、非强一致」特性。
- **可选持久化（标注为 Phase 5 增强，本期不建表）**：若需跨重启保留 K线，新增 `t_kline` 表：

```sql
-- sql/market_kline.sql（本期不建，仅设计预留；落在 /dev 后续需求）
CREATE TABLE `t_kline` (
  `id` bigint NOT NULL COMMENT 'K线ID',
  `symbol` varchar(32) NOT NULL COMMENT '交易对',
  `interval` varchar(8) NOT NULL COMMENT '周期:1m/5m/15m/1h/4h/1d',
  `open_time` bigint NOT NULL COMMENT '窗口起点(epoch millis)',
  `open` bigint NOT NULL COMMENT '开盘价(计价币最小单位)',
  `high` bigint NOT NULL COMMENT '最高价',
  `low` bigint NOT NULL COMMENT '最低价',
  `close` bigint NOT NULL COMMENT '收盘价',
  `volume` bigint NOT NULL COMMENT '成交量(基础币最小单位)',
  `quote_volume` bigint NOT NULL COMMENT '成交额(计价币最小单位)',
  -- 系统字段 --
  `create_by` varchar(64) DEFAULT '' COMMENT '创建者',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by` varchar(64) DEFAULT '' COMMENT '更新者',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `is_deleted` tinyint DEFAULT '0' COMMENT '逻辑删除:0=未删,1=已删',
  `version` int DEFAULT '0' COMMENT '乐观锁版本号',
  `tenant_id` bigint DEFAULT '0' COMMENT '租户ID',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_symbol_interval_time` (`symbol`,`interval`,`open_time`),
  KEY `idx_symbol_time` (`symbol`,`open_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='K线聚合表(可选持久化)';
```

> 采用「**内存为主 + 可选落库**」：内存保证实时性，落库保证跨重启可恢复；本期先内存，落库逻辑（消费时 upsert `t_kline`）作为 Phase 5 增量，**不改变本期算法**。

---

## 八、落地 Checklist（/dev 实施指引）

> 本模块为**新建**：新增目录 `exchange-market/`，父 pom `<modules>` 增加 `exchange-market`，其余模块不动。

- [ ] 父 `pom.xml` 的 `<modules>` 增加 `<module>exchange-market</module>`。
- [ ] 新建 `exchange-market/pom.xml`（复制 `exchange-asset` 风格：`spring-boot-starter-web` / `nacos-discovery` / `validation` / `lombok` / `springdoc` / `exchange-common`(含排除旧版 mybatis-plus-extension + 显式 3.5.7，**若引入 DB 可选**；本期纯内存可去掉 mybatis/mysql/druid) / `rocketmq-spring-boot-starter` / 测试）。
- [ ] 新建 `exchange-market/src/main/resources/application.yml`：`server.port: 8106`、`spring.application.name: exchange-market`、Nacos 注册、`rocketmq.name-server: 127.0.0.1:9876`（只配消费，无需 producer group）。
- [ ] 新建启动类 `MarketApplication`（`@SpringBootApplication` + `@Import(GlobalExceptionHandler.class)` 引入 common 的 advice，与 asset/chain 一致）。
- [ ] 新建 `mq/Topics.java`（`ORDER_TRADE = "ORDER-TRADE"`）。
- [ ] 新建 `mq/consumer/OrderTradeMarketConsumer.java`（`@RocketMQMessageListener(topic="ORDER-TRADE", consumerGroup="market-order-trade-group")`）——解析 `TradeSettleDTO`，Redis SETNX(`mq:dedup:ORDER-TRADE:market:{tradeNo}`) 去重后调用聚合器。
- [ ] 新建 `market/KlineInterval.java`（周期枚举 + 毫秒）。
- [ ] 新建 `market/model/Kline.java`、`market/model/Ticker.java`（Long 最小单位字段）。
- [ ] 新建 `market/MarketAggregator.java`：
  - [ ] `onTrade(TradeSettleDTO)` → 对每个启用 interval 做 `ConcurrentHashMap.compute` 更新 OHLCV。
  - [ ] `getKlines(symbol, interval, limit)`（升序返回最近 N 根）。
  - [ ] `getTicker(symbol)` / `getTickers()`（由 K线派生 lastPrice/high/low/volume/quoteVolume/change24h）。
- [ ] 新建 `controller/MarketController.java`（`/api/market/kline/list`、`/api/market/ticker/list`、`/api/market/ticker/{symbol}`），返回 `Result<T>`。
- [ ] 网关 `application.yml` 新增 `market-service` 路由 `Path=/api/market/**` → `lb://exchange-market`。
- [ ] 编译验证：`mvn -pl exchange-market -am package -DskipTests`（JAVA_HOME=temurin-17）。
- [ ] 运行验证：启动 Docker rocketmq → 启动 `exchange-market` → 撮合成交若干笔 → `GET /api/market/kline/list?symbol=BTC/USDT&interval=1m` 与 `GET /api/market/ticker/BTC/USDT` 返回正确 OHLCV。
- [ ] 单测要点：`MarketAggregatorTest`——单笔开窗(open=high=low=close)、连续多笔 high/low/close 更新、跨窗口滚动开新窗、同 tradeNo 去重后不重复累计 volume、精度(大数 Long 累加不溢出)。

---
