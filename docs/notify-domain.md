# 通知域（Phase 4）落地设计：消费 ASSET-CHANGE / ORDER-TRADE 生成站内通知，查询与已读

> 版本：v1.0 · 作者：系统架构师 · 日期：2026-08-06
> 适用：`exchange-notify`（Nacos 服务名 `exchange-notify`，端口 **8107**，当前为空骨架 0 类，本次**完成落地**）实现依据。
> 定位：本文件是 `docs/ARCHITECTURE.md` 附录「通知域」的**落地细化**（消费订阅、`t_notification` 表、事件→通知类型映射、REST 契约），与 `docs/mq-topics.md` 及资产/订单域事件契约保持一致，供 `/dev` 直接照此实现。**本文件为新建，不改动 `docs/` 既有文档、不改动 `sql/` 既有文件（新增独立 `sql/notify.sql`）、不改动既有模块 Java、不执行 git 提交。** `exchange-notify` 自身是本次待完成模块，其 pom/application.yml/Java 由 `/dev` 落地实现（非「既有模块 Java」）。
> 兼容基线：Spring Boot 3.2.5 / Spring Cloud Alibaba 2023.0.1.0 / Java 17 / MyBatis-Plus 3.5.7 / MySQL 8 / Redis 7 / 统一 `Result<T>` / `BaseEntity`（id=雪花 + createBy/createTime/updateBy/updateTime + isDeleted 逻辑删除 + version 乐观锁 + tenantId 租户）。
> 依赖：**事件源** = ① `exchange-asset` 的 `ASSET-CHANGE`（充值/提现/资金变动，消息体 `LedgerVO`）；② `exchange-order` 的 `ORDER-TRADE`（成交，消息体 `TradeSettleDTO`）。两者 DTO 均位于 `exchange-common`。

---

## 目录

1. [总体设计要点](#一总体设计要点)
2. [消费契约与消费组](#二消费契约与消费组)
3. [通知表 t_notification（DDL）](#三通知表-t_notificationddl)
4. [事件 → 通知类型映射与模板](#四事件--通知类型映射与模板)
5. [消费幂等设计](#五消费幂等设计)
6. [REST / 网关路由契约](#六rest--网关路由契约)
7. [落地 Checklist（/dev 实施指引）](#七落地-checklistdev-实施指引)

---

## 一、总体设计要点

- **职责定位**：**站内通知（inbox）服务**。消费资金/交易事件，为对应用户生成一条可查询、可标记已读的通知记录；提供查询/未读数/已读接口。**不负责邮件/短信/Webhook 外发**（本期仅落站内通知，外发通道标注 Phase 5 增强）。
- **表结构规范**：唯一新表 `t_notification`，继承 `BaseEntity` 系统字段，主键 `bigint` 雪花，`ENGINE=InnoDB`、`utf8mb4_unicode_ci`、每字段中文注释、索引 `uk_*`/`idx_*`，与 `sql/user.sql`/`sql/asset.sql` 风格完全一致。
- **消费独立性**：notify 使用**独立消费组**订阅两条主题，与 asset 的 `asset-order-trade-group`、market 的 `market-order-trade-group` 等互不影响：
  - `notify-order-trade-group` ← `ORDER-TRADE`（成交）
  - `notify-asset-change-group` ← `ASSET-CHANGE`（资金/充值/提现）
- **幂等**：以 `biz_ref + type` 唯一索引兜底（见 §5），重复事件不生成重复通知。
- **接口分层**：对外 REST 走网关（`/api/notify/**`）；不提供内部写接口。
- **金额口径**：通知内容中的金额保留 `LedgerVO.amount` / `TradeSettleDTO.quoteAmount` 的 **Long 最小单位**原始值，展示层换算；`title`/`content` 由服务端按模板拼接（见 §4）。

---

## 二、消费契约与消费组

### 2.1 `ASSET-CHANGE`（消息体 = `LedgerVO`，KEYS = `requestId`）

> 生产者 `exchange-asset`（`AssetEventProducer`，资金变动成功后发，`docs/asset-domain.md` / `docs/mq-topics.md`）。

| `LedgerVO` 字段 | 类型 | 通知用途 |
|------|------|---------|
| `userId` | Long | **通知接收人** |
| `symbol` | String | 币种，如 USDT |
| `bizType` | String | `DEPOSIT`/`WITHDRAW`/`FREEZE`/`UNFREEZE`/`TRANSFER`/`FEE`/`REBATE` → **决定通知类型** |
| `direction` | Integer | 1=IN 2=OUT 3=FROZEN 4=UNFROZEN |
| `amount` | Long | 变动金额（最小单位） |
| `refNo` | String | 业务单号（depositId/withdrawId/orderId） |
| `requestId` | String | 幂等键（消息 KEYS） |
| `status` | Integer | 1=成功 |
| `createTime` | LocalDateTime | 事件时间 |

> **本期只处理 `bizType ∈ {DEPOSIT, WITHDRAW}`**（对应充值到账、提现成功两类站内通知）。`FREEZE/UNFREEZE/TRANSFER/FEE` 等高频中间流水**本期不生成通知**（避免刷屏），可作为后续扩展（标注）。

### 2.2 `ORDER-TRADE`（消息体 = `TradeSettleDTO`，KEYS = `tradeNo`）

> 生产者 `exchange-order`（撮合成交后发，`docs/order-domain.md §5.4`）。

| `TradeSettleDTO` 字段 | 类型 | 通知用途 |
|------|------|---------|
| `tradeNo` | String | 成交单号（幂等键） |
| `symbol` / `baseCoin` / `quoteCoin` | String | 交易对 / 基础币 / 计价币 |
| `price` / `quantity` / `quoteAmount` | Long | 成交价 / 量 / 名义值（最小单位） |
| `buyUserId` / `sellUserId` | Long | **买卖双方用户 → 各生成一条通知** |
| `takerOrderNo` / `makerOrderNo` | String | 订单号（biz_ref） |

> **一笔成交给买卖双方各生成一条 TRADE_FILLED 通知**（`buyUserId` 一条、`sellUserId` 一条），`biz_ref` 需区分（见 §5 幂等）。

### 2.3 消费实现要点

```java
// 成交通知消费者
@Component
@RocketMQMessageListener(topic = "ORDER-TRADE", consumerGroup = "notify-order-trade-group", selectorExpression = "*")
public class OrderTradeNotifyConsumer implements RocketMQListener<MessageExt> {
    // 解析 TradeSettleDTO → 生成 buyer/seller 两条 TRADE_FILLED 通知
}

// 资金/充值/提现通知消费者
@Component
@RocketMQMessageListener(topic = "ASSET-CHANGE", consumerGroup = "notify-asset-change-group", selectorExpression = "*")
public class AssetChangeNotifyConsumer implements RocketMQListener<MessageExt> {
    // 解析 LedgerVO，仅 bizType∈{DEPOSIT,WITHDRAW} 生成通知
}
```

- 消费失败抛异常触发重投（默认 16 次进死信）；幂等由 DB 唯一索引兜底（见 §5），重投安全。
- 可选：消费层 Redis SETNX（`mq:dedup:ASSET-CHANGE:notify:{requestId}` / `mq:dedup:ORDER-TRADE:notify:{tradeNo}`）减少无效 insert，但不强依赖（DB 唯一索引是最终防线）。

---

## 三、通知表 t_notification（DDL）

> 新建独立 SQL：`sql/notify.sql`（与 `sql/order.sql` 同级、同风格，不改既有文件）。

### 3.1 字段说明

| 字段 | 类型 | 说明 |
|------|------|------|
| id | bigint | 通知ID（雪花） |
| user_id | bigint | 接收用户ID |
| type | varchar(32) | 通知类型：`DEPOSIT_CONFIRMED`/`WITHDRAW_SUCCESS`/`TRADE_FILLED` 等（见 §4） |
| title | varchar(128) | 标题 |
| content | varchar(1024) | 内容（含业务详情） |
| biz_type | varchar(32) | 源事件业务类型（如 DEPOSIT/WITHDRAW/TRADE） |
| biz_ref | varchar(64) | 关联业务单号（depositId/withdrawId/tradeNo:BUY|:SELL），**幂等键组分** |
| symbol | varchar(32) | 关联币种/交易对（冗余，便于检索） |
| amount | bigint | 关联金额（最小单位，冗余展示） |
| is_read | tinyint | 已读状态：0=未读 1=已读 |
| read_time | datetime | 已读时间 |
| channel | varchar(20) | 通知渠道：`INBOX`（本期仅站内信） |

> 系统字段（继承 `BaseEntity` 约定，DDL 中列全）：`create_by` / `create_time` / `update_by` / `update_time` / `is_deleted` / `version` / `tenant_id`。

### 3.2 完整 DDL

```sql
-- ============================================================
-- 通知域（Phase 4）建表脚本
-- 库：web3_exchange；表前缀 t_；引擎 InnoDB；utf8mb4
-- 与 sql/user.sql、sql/asset.sql、sql/order.sql 风格一致：雪花主键 + BaseEntity 系统字段 + 中文注释
-- 落地依据：docs/notify-domain.md
-- ============================================================

CREATE TABLE `t_notification` (
  `id` bigint NOT NULL COMMENT '通知ID',
  `user_id` bigint NOT NULL COMMENT '接收用户ID',
  `type` varchar(32) NOT NULL COMMENT '通知类型:DEPOSIT_CONFIRMED/WITHDRAW_SUCCESS/TRADE_FILLED',
  `title` varchar(128) NOT NULL COMMENT '标题',
  `content` varchar(1024) NOT NULL COMMENT '内容(含业务详情)',
  `biz_type` varchar(32) NOT NULL DEFAULT '' COMMENT '源事件业务类型:DEPOSIT/WITHDRAW/TRADE',
  `biz_ref` varchar(64) NOT NULL COMMENT '关联业务单号(幂等键组分):depositId/withdrawId/tradeNo:BUY|:SELL',
  `symbol` varchar(32) DEFAULT NULL COMMENT '关联币种/交易对(冗余检索)',
  `amount` bigint DEFAULT NULL COMMENT '关联金额(最小单位,冗余展示)',
  `is_read` tinyint NOT NULL DEFAULT '0' COMMENT '已读状态:0=未读,1=已读',
  `read_time` datetime DEFAULT NULL COMMENT '已读时间',
  `channel` varchar(20) NOT NULL DEFAULT 'INBOX' COMMENT '通知渠道:INBOX站内信(本期仅此)',
  -- 系统字段 --
  `create_by` varchar(64) DEFAULT '' COMMENT '创建者',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by` varchar(64) DEFAULT '' COMMENT '更新者',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `is_deleted` tinyint DEFAULT '0' COMMENT '逻辑删除:0=未删,1=已删',
  `version` int DEFAULT '0' COMMENT '乐观锁版本号',
  `tenant_id` bigint DEFAULT '0' COMMENT '租户ID',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_type_bizref` (`user_id`,`type`,`biz_ref`),
  KEY `idx_user_read_time` (`user_id`,`is_read`,`create_time`),
  KEY `idx_user_time` (`user_id`,`create_time`),
  KEY `idx_biz_ref` (`biz_ref`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='站内通知表';
```

**索引与约束要点**

| 键 | 用途 |
|----|------|
| `UNIQUE(user_id, type, biz_ref)` | **消费幂等最终防线**：同一用户同一类型同一业务单号只生成一条通知，重复事件 insert 撞唯一索引 → 捕获后跳过 |
| `idx(user_id, is_read, create_time)` | 「未读数」计数与「用户通知列表」分页查询 |
| `idx(user_id, create_time)` | 用户通知时间倒序分页 |
| `idx(biz_ref)` | 按业务单号追溯通知 |

---

## 四、事件 → 通知类型映射与模板

| 源主题 | 源事件（bizType） | 通知类型 | 接收人 | 标题模板 | 内容模板 | biz_ref |
|--------|------------------|---------|--------|---------|---------|---------|
| `ASSET-CHANGE` | `DEPOSIT`（充值入账成功） | `DEPOSIT_CONFIRMED` | `LedgerVO.userId` | `充值到账` | `您的 {symbol} 充值已到账 {amount}，单号 {refNo}` | `depositId`（`LedgerVO.refNo`） |
| `ASSET-CHANGE` | `WITHDRAW`（提现成功扣减） | `WITHDRAW_SUCCESS` | `LedgerVO.userId` | `提现成功` | `您的 {symbol} 提现已处理成功 {amount}，单号 {refNo}` | `withdrawId`（`LedgerVO.refNo`） |
| `ORDER-TRADE` | `TRADE`（成交） | `TRADE_FILLED` | `buyUserId` | `订单成交` | `您买入 {symbol} 已成交 {quantity}，成交价 {price}，金额 {quoteAmount}` | `tradeNo + ":BUY"` |
| `ORDER-TRADE` | `TRADE`（成交） | `TRADE_FILLED` | `sellUserId` | `订单成交` | `您卖出 {symbol} 已成交 {quantity}，成交价 {price}，金额 {quoteAmount}` | `tradeNo + ":SELL"` |

**模板填充说明**：
- `amount`、`price`、`quantity`、`quoteAmount` 均为 **Long 最小单位**原始值，模板中可先以「数值 + 单位标注」呈现，或由 `/dev` 用 `t_coin.decimals` 换算成带小数展示（本期建议：content 内直接拼最小单位整数 + 说明，避免跨模块换算复杂度；展示层增强留 Phase 5）。
- `biz_ref` 语义：充值/提现直接取 `LedgerVO.refNo`（即 depositId/withdrawId）；成交按买方/卖方拆成 `tradeNo:BUY` / `tradeNo:SELL`，确保**同一用户同一笔成交**与**买卖双方**各自幂等、各得一条。

---

## 五、消费幂等设计

> RocketMQ「至少一次」投递（消费失败重投、集群重放）会导致同一事件重复到达。**必须幂等**。

**双层防线（与 asset 模式一致）：**

1. **DB 唯一索引（强一致兜底）**：`t_notification.uk_user_type_bizref(user_id, type, biz_ref)`。消费时：
   ```
   try { INSERT ... }
   catch (DuplicateKeyException) { log.info("重复通知事件，跳过。biz_ref={}") ; return; }  // 已处理过
   ```
2. **消费层 Redis SETNX（推荐，秒级、低侵入）**：`setIfAbsent("mq:dedup:ASSET-CHANGE:notify:"+requestId, "1", TTL=24h)` / `mq:dedup:ORDER-TRADE:notify:{tradeNo}`，返回 false 直接 ACK 跳过。命中后即使 SETNX 过期，DB 唯一索引仍兜底。

**幂等键汇总**：

| 主题 | 幂等键 | 落点 |
|------|--------|------|
| `ASSET-CHANGE` | `requestId`（消息 KEYS） | Redis SETNX + `uk_user_type_bizref` |
| `ORDER-TRADE` | `tradeNo`（消息 KEYS），biz_ref 再拆 `:BUY`/`:SELL` | Redis SETNX + `uk_user_type_bizref` |

> 关键点：**biz_ref 对同一用户是确定性的**（充值/提现 = depositId/withdrawId；成交按买卖方拆分），保证同一事件重复到达只落一条通知。

---

## 六、REST / 网关路由契约

> 统一返回 `com.web3.exchange.common.model.Result<T>`。金额字段 Long 最小单位。

### 6.1 对外 REST（经网关 `/api/notify/**`）

| 方法 | 接口 | 请求参数 | 返回 | 说明 |
|------|------|---------|------|------|
| 通知列表 | `GET /api/notify/list` | `userId`（必填）、`isRead`（可选 0/1）、`page`（默认 1）、`size`（默认 20） | `Result<Page<NotificationVO>>` | 用户通知分页（按 create_time 倒序） |
| 未读数 | `GET /api/notify/unread-count` | `userId` | `Result<Long>` | 未读通知数（`count(is_read=0)`） |
| 标记单条已读 | `PUT /api/notify/{id}/read` | `userId`（校验归属） | `Result<Boolean>` | 置 `is_read=1, read_time=now` |
| 标记全部已读 | `PUT /api/notify/read-all` | `userId` | `Result<Integer>` | 该用户全部未读 → 已读，返回更新条数 |

**VO 定义（示例）**：

```java
public class NotificationVO {
    private Long id;
    private Long userId;
    private String type;          // DEPOSIT_CONFIRMED / WITHDRAW_SUCCESS / TRADE_FILLED
    private String title;
    private String content;
    private String bizRef;        // 关联业务单号
    private Integer isRead;       // 0=未读 1=已读
    private LocalDateTime createTime;
}
```

> 说明：`userId` 本期作为请求参数传入（尚未接统一鉴权取当前用户；Phase 5 可由网关 JWT 解析注入，与 `/api/chain`、`/api/order` 现状一致）。标记已读接口需校验通知归属 `user_id == userId`，越权返回 `Result.error`。

### 6.2 网关路由（标注，`/dev` 在 gateway application.yml 新增）

> notify 服务**无 context-path**，Controller 映射为 `/api/notify/***`，网关直接转发（同 `user-service` 风格，不需要 RewritePath）。

```yaml
# exchange-gateway/src/main/resources/application.yml 新增路由（/dev 落地时执行，本设计不改文件）
- id: notify-service
  uri: lb://exchange-notify
  predicates:
    - Path=/api/notify/**
```

---

## 七、落地 Checklist（/dev 实施指引）

> `exchange-notify` 当前为空骨架（仅 pom.xml + 极简 application.yml，0 个 Java 类），本次**完成落地**。父 pom 已含 `exchange-notify` 模块，无需改 `<modules>`。

- [ ] 执行 `sql/notify.sql` 建表（库 `web3_exchange`，新建 t_notification）。
- [ ] 完善 `exchange-notify/pom.xml`（复制 `exchange-asset` 风格：`spring-boot-starter-web` / `nacos-discovery` / `validation` / `mybatis-plus-spring-boot3-starter` + 显式 3.5.7 extension + 排除 common 旧版 extension / `mysql` / `druid` / `lombok` / `springdoc` / `exchange-common` / `rocketmq-spring-boot-starter` / 测试）。
- [ ] 更新 `exchange-notify/src/main/resources/application.yml`：
  - [ ] `server.port: 8107`（当前骨架为 8106，按本设计改为 8107；**与 market 8106 错开**）。
  - [ ] `spring.application.name: exchange-notify`。
  - [ ] 补 Nacos 注册、Druid 数据源（库 `web3_exchange`）、MyBatis-Plus 配置（`type-aliases-package: com.web3.exchange.notify.entity`、`table-prefix: t_`）、springdoc。
  - [ ] `rocketmq.name-server: 127.0.0.1:9876`（仅消费，无需 producer group）。
- [ ] 新建启动类 `NotifyApplication`（`@SpringBootApplication` + `@Import(GlobalExceptionHandler.class)`）。
- [ ] 新建 `entity/Notification.java`（继承 `BaseEntity`，字段 `userId/type/title/content/bizType/bizRef/symbol/amount/isRead/readTime/channel`，`@TableLogic`/`@Version`）。
- [ ] 新建 `mapper/NotificationMapper.java`（含 `countUnread(userId)`、`updateAllRead(userId)` 等 SQL 或 MP wrapper）。
- [ ] 新建 `service/NotificationService.java` + `impl/NotificationServiceImpl.java`：`createWithIdempotent(...)`（INSERT + DuplicateKeyException 捕获跳过）、`pageByUser`、`unreadCount`、`markRead(id,userId)`、`markAllRead(userId)`。
- [ ] 新建 `mq/Topics.java`（`ASSET_CHANGE` / `ORDER_TRADE`）。
- [ ] 新建 `mq/consumer/AssetChangeNotifyConsumer.java`（`notify-asset-change-group`）：仅 `bizType∈{DEPOSIT,WITHDRAW}` → 生成 `DEPOSIT_CONFIRMED`/`WITHDRAW_SUCCESS`。
- [ ] 新建 `mq/consumer/OrderTradeNotifyConsumer.java`（`notify-order-trade-group`）：buyer/seller 各生成一条 `TRADE_FILLED`。
- [ ] 新建 `controller/NotifyController.java`（`/api/notify/list`、`/unread-count`、`/{id}/read`、`/read-all`），返回 `Result<T>`。
- [ ] 网关 `application.yml` 新增 `notify-service` 路由 `Path=/api/notify/**` → `lb://exchange-notify`。
- [ ] 编译验证：`mvn -pl exchange-notify -am package -DskipTests`（JAVA_HOME=temurin-17）。
- [ ] 运行验证：启动 Docker rocketmq + 各服务 → 触发一笔充值入账（asset credit 发 `ASSET-CHANGE`）与一笔成交（order 发 `ORDER-TRADE`）→ `GET /api/notify/list` 出现对应通知、`/unread-count` 正确；重复投递同一事件不产生重复通知。
- [ ] 单测要点：`NotificationServiceTest`——幂等（同 biz_ref 二次 insert 跳过）、未读数统计、标记已读/全部已读、越权校验。

---
