# MQ 主题规范与首批主题规划（RocketMQ）

> 版本：v1.0（批次B 骨架） · 作者：系统架构师 · 日期：2026-08-06
> 定位：为后续 order / chain / notify 事件驱动落地定规矩。本文件**新建**，不改动 `docs/asset-domain.md`、`docs/feature-guide.md`、`docs/ARCHITECTURE.md`。
> 现状：Phase 1 以「Feign 同步 + 幂等」为资金主链路（见 `asset-domain.md` §5.3），RocketMQ 作为**事件通知补充**；事务消息在后续 Phase 再引入，二者不冲突。
> 中间件版本：`rocketmq-spring-boot-starter:2.3.1`（兼容 Spring Boot 3.2.5 / Java 17），nameserver 规划 `127.0.0.1:9876`。

---

## 目录

1. [命名规范](#一命名规范)
2. [消息体字段约定](#二消息体字段约定)
3. [幂等消费建议](#三幂等消费建议)
4. [首批主题清单](#四首批主题清单)
5. [主题×生产/消费矩阵](#五主题生产消费矩阵)

---

## 一、命名规范

主题名统一采用 **大写 + 短横线** 的 `{领域}-{事件}` 格式（`DOMAIN-EVENT`），与业务域（asset/order/chain/notify）一一对应，便于按域隔离与检索。

| 规范项 | 规则 | 示例 |
|--------|------|------|
| 大小写 | 全大写（`[A-Z0-9-]`） | `ASSET-CHANGE` |
| 分隔符 | 单个 `-`，不用 `.`/`_` | `ORDER-TRADE` |
| 前缀（领域） | `ASSET` / `ORDER` / `DEPOSIT` / `WITHDRAW` / `NOTIFY` / `USER` 等 | `ASSET-*` |
| 后缀（事件） | 表示「发生了什么」的过去式/名词 | `-CHANGE` / `-TRADE` / `-CONFIRMED` |
| 长度 | 建议 ≤ 48 字符 | — |
| 同一主题 | 只描述**一类**业务事件，不混装 | — |

**命名反例**：`asset_change`（用了下划线）、`AssetChange`（非全大写）、`messages`（无领域/事件语义）、`all`（过于笼统）。

**消费者组命名**：`{消费方}-{主题}-group`（如 `order-order-trade-group`、`notify-asset-change-group`），保证同一条消息可被多个服务各自的消费组分别消费（RocketMQ 广播给不同消费组）。

**Tag 使用**：同一主题下如需细分（如资产变动里区分 freeze/credit），用 Tag 表达（`ASSET-CHANGE` 的 Tag：`FREEZE`/`UNFREEZE`/`TRANSFER`/`DEPOSIT`/`WITHDRAW`）；消息体仍保持统一 schema。

---

## 二、消息体字段约定

消息体统一使用 **JSON 字符串**（序列化用 Jackson `ObjectMapper`），采用扁平对象结构，字段全链路约定如下：

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `eventType` | String | ✅ | 事件类型，与 Tag 对齐（如 `DEPOSIT`） |
| `bizKey` | String | ✅ | **业务键（幂等键）**，如 `requestId` / `tradeId` / `depositId`；消费端用它去重 |
| `userId` | Long | 业务相关 | 涉及用户的主题必填 |
| `symbol` | String | 资金相关 | 币种符号 |
| `amount` | Long | 资金相关 | 金额（**最小单位整数**，禁止浮点） |
| `refNo` | String | 业务相关 | 业务单号（orderId/withdrawId/depositId） |
| `data` | Object | 可选 | 事件附带的结构化载荷（如 `LedgerVO`），与扁平字段二选一 |
| `occurredAt` | String | ✅ | 事件发生时间（ISO-8601，如 `2026-08-06T10:00:00`） |

**约定要点**：
- **金额一律最小单位整数**（`long`），与资产域 `BIGINT` 口径一致，杜绝浮点误差。
- **必须带业务键**：将幂等键 `bizKey`（通常取 `requestId`）写入 RocketMQ 消息 `KEYS` 属性，消费端据此做去重与按 key 查询。
- 消息体 `data` 若序列化跨模块 DTO（如 `LedgerVO`），需保证该类 `Serializable` 且字段稳定——增加字段前先评估下游兼容。

**ASSET-CHANGE 事件体示例（asset 发，body 直接复用 `LedgerVO` 序列化结果）**：

```json
{
  "id": 10241,
  "requestId": "20260806-10001",
  "userId": 88,
  "accountId": 501,
  "coinId": 1,
  "symbol": "USDT",
  "bizType": "DEPOSIT",
  "direction": 1,
  "amount": 1000000,
  "beforeAvailable": 0,
  "afterAvailable": 1000000,
  "beforeFrozen": 0,
  "afterFrozen": 0,
  "refNo": "dep-20260806-9",
  "status": 1,
  "remark": "充值入账"
}
```

---

## 三、幂等消费建议

**问题**：RocketMQ 默认「至少一次（at-least-once）」投递，消费失败重投、集群重启重放都会导致**同一业务事件被重复消费**。

**消费端统一做幂等去重**，推荐优先级从高到低：

1. **Redis SETNX（推荐，秒级、低侵入）**
   - 键：`mq:dedup:{topic}:{bizKey}`
   - 操作：`setIfAbsent(key, "1", TTL=24h)`，返回 `false` 即重复，直接 ACK 跳过。
   - 适合消费侧只需「处理过/未处理」标记的场景（通知、对账触发）。
2. **业务表唯一索引（强一致兜底）**
   - 如充值已入账则 `t_deposit.uk_tx_hash` 已存在、流水 `t_asset_ledger.uk_request_id` 已存在——消费时先回读，命中即跳过。
   - 适合消费端会**写库**、且要求账实严格的场景（asset 消费 DEPOSIT-CONFIRMED 入账）。
3. **本地去重表（无 Redis 时）**
   - 维护一张 `t_mq_consume_record(topic, biz_key, PRIMARY KEY(topic, biz_key))`，先插后处理，唯一索引兜底。

**规范**：
- 每个消费者**必须**以 `bizKey`（或业务单号）去重，不能依赖「消息只会来一次」。
- 幂等键语义由**生产者**保证确定性（同一业务重复发生时 bizKey 相同）。
- 消费失败应抛异常触发重投（默认 16 次后进死信），或在日志告警后落库人工补偿。

---

## 四、首批主题清单

| 主题 | 语义 | Producer（发） | Consumer（订阅） | Tag | 幂等键 |
|------|------|----------------|------------------|-----|--------|
| `ASSET-CHANGE` | 资金变动事件（写流水+改余额成功后发） | **exchange-asset**（`AssetEventProducer`，本次骨架已接入） | **exchange-order**（对账/余额刷新）、**exchange-notify**（资金变动推送） | `FREEZE`/`UNFREEZE`/`TRANSFER`/`DEPOSIT`/`WITHDRAW`/`FEE` | `requestId` |
| `ORDER-TRADE` | 撮合成交事件（order 撮合成交后发） | **exchange-order**（后续落地） | **exchange-asset**（过户结算驱动）、**exchange-notify**（成交通知） | `BUY`/`SELL`/`CANCEL` | `tradeId` |
| `DEPOSIT-CONFIRMED` | 充值确认事件（chain 确认入账前发） | **exchange-chain**（后续落地） | **exchange-asset**（调用 credit 入账，幂等由 `uk_tx_hash` 兜底） | — | `txHash` |

> 说明：本批次（B）只在 **exchange-asset** 落地 `ASSET-CHANGE` 的**生产者**（资金变动成功后发事件）与一个**幂等消费骨架**；`ORDER-TRADE` / `DEPOSIT-CONFIRMED` 的 producer/consumer 由 order / chain / notify 后续批次接入，本文件先行定好契约。

---

## 五、主题×生产/消费矩阵

```
            ┌───────────────┐
            │  exchange-order │  ──发──▶  ORDER-TRADE  ──订阅──▶  exchange-asset (过户)
            └───────────────┘                                      └────▶  exchange-notify (成交通知)
            ┌───────────────┐
            │  exchange-chain │  ──发──▶  DEPOSIT-CONFIRMED  ──订阅──▶  exchange-asset (credit 入账)
            └───────────────┘
            ┌───────────────┐
            │  exchange-asset │  ──发──▶  ASSET-CHANGE  ──订阅──▶  exchange-order (余额/对账)
            └───────────────┘                                      └────▶  exchange-notify (资金变动推送)
```

- **同一主题可被多个消费组分别消费**：如 `ASSET-CHANGE` 同时被 order 组、notify 组各自消费，互不影响。
- **顺序性**：资金类主题不强依赖全局有序；若某业务需要同账户有序消费，可约定用 `userId` 作消息 `KEYS` 走顺序消息（后续按需启用）。
- **事务消息**：跨服务最终一致性（order↔asset、chain↔asset）在后续 Phase 引入 RocketMQ 事务消息；本阶段以「Feign 同步 + 幂等」为准，MQ 仅做事件通知补充，发失败**只记录日志、不阻断资金主流程**。

---

## 附：配置参考（exchange-asset/application.yml）

```yaml
rocketmq:
  name-server: 127.0.0.1:9876
  producer:
    group: asset-producer-group
    send-message-timeout: 3000   # 发消息超时(ms)，失败降级为仅记日志
```
