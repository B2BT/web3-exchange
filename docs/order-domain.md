# 订单/撮合域（Phase 2）落地设计：数据库 DDL、订单状态机、内存撮合引擎、order→asset 资金契约

> 版本：v1.0 · 作者：系统架构师 · 日期：2026-08-06
> 适用：`exchange-order`（Nacos 服务名 `exchange-order`，端口 **8104**）落地依据。
> 定位：本文件是 `docs/ARCHITECTURE.md` 附录 A2「交易域」的**落地细化**（具体 SQL、状态机、撮合引擎方案、资金接口契约），与架构蓝图保持一致，供 `/dev` 直接照此实现。**不修改任何 Java 代码，不修改 `sql/user.sql`、`sql/asset.sql`**；新增独立 SQL：`sql/order.sql`。
> 兼容基线：Spring Boot 3.2.5 / Spring Cloud Alibaba 2023.0.1.0 / MyBatis-Plus 3.5.7 / MySQL 8 / 统一 `Result<T>` / `BaseEntity`（id=雪花 + createBy/createTime/updateBy/updateTime + isDeleted 逻辑删除 + version 乐观锁 + tenantId 租户）/ RocketMQ `ORDER-TRADE` 主题（见 `docs/mq-topics.md`）。
> 依赖：**资金能力复用 `exchange-asset` 已实现的内部接口 `/internal/asset/**`**（freeze/unfreeze/transfer，金额 Long 最小单位，幂等 requestId），本域**不再自建资金表**，只通过 Feign 调资产域。

---

## 目录

1. [总体设计要点](#一总体设计要点)
2. [金额与精度约定](#二金额与精度约定)
3. [三张表完整 DDL](#三三张表完整-ddl)
4. [订单状态机](#四订单状态机)
5. [内存撮合引擎方案](#五内存撮合引擎方案)
6. [order→asset 资金接口契约](#六orderasset-资金接口契约)
7. [撮合正确性单测要点](#七撮合正确性单测要点)
8. [落地 Checklist（/dev 实施指引）](#八落地-checklistdev-实施指引)

---

## 一、总体设计要点

- **交易对驱动**：撮合引擎只对 `t_symbol.status=1（交易中）` 的交易对开放；价格/数量精度、最小下单、费率全部由 `t_symbol` 配置驱动，业务代码不写死。
- **资金铁律**：订单域**绝不直接改余额**，一律通过 Feign 调 asset 的 `/internal/asset/freeze|transfer|unfreeze`。下单冻结、成交过户、撤单解冻三件事分别对资金流水；`requestId` 全部由 order 派生并保证确定性，asset 幂等兜底。
- **内存撮合（单实例）**：Phase 2 采用**单机内存撮合引擎**，订单簿驻留 JVM，按交易对分 key 串行化（striped lock / 单线程队列），保证同一交易对的撮合严格有序，天然满足价格优先 + 时间优先。
- **落库与撮合顺序**：**先落库、后撮合、再结算**——订单先写库（可追溯），再进引擎撮合产生成交，成交同步落库并发 `ORDER-TRADE` 事件；asset 资金过户在撮合确定后按成交逐笔发起（见 §6）。
- **接口分层**：对外 REST 走网关（`/api/order/**`）；撮合引擎、资金回调等内部能力不对外。事件走 RocketMQ `ORDER-TRADE` 主题（`docs/mq-topics.md`）。
- **表结构规范**：继承 `BaseEntity` 系统字段，主键 `bigint` 雪花，`ENGINE=InnoDB`、`utf8mb4_unicode_ci`、每字段中文注释、索引 `uk_*`/`idx_*`，与 `sql/user.sql`/`sql/asset.sql` 完全一致。

---

## 二、金额与精度约定

> 与资产域 `docs/asset-domain.md §2` 完全一致，**全部金额字段采用 `BIGINT` 最小单位**，应用层禁止 `double`/`float`。

- **币种口径**：由 `t_coin.decimals` 定义。BTC=8、ETH=18、USDT=6。撮合域沿用 asset 的 `t_coin` 精度，不重复维护。
- **字段语义**：
  - 计价币（quote_coin，如 USDT）最小单位：`price` / `quote_amount` / `avg_price` / `freeze_quote_amount` / `taker_fee` / `maker_fee` / `fee` 等。
  - 基础币（base_coin，如 BTC）最小单位：`quantity` / `remaining` / `filled_amount` / `freeze_base_amount` 等。
- **换算**：入参/出参 DTO 一律 `long`（最小单位），对外 REST 由展示层按 `t_coin.decimals`/`t_symbol` 精度换算。服务间 Feign 传整数，杜绝浮点。
- **价格精度 vs 币种精度**：`price` 精度由 `t_symbol.price_precision` 定义，成交价/限价必须是 `price_tick` 的整数倍；`quantity` 精度由 `amount_precision` 定义。下单时按此**截断/校验**（超出精度的尾数直接拒绝或截断，见 §5 校验）。

---

## 三、三张表完整 DDL

> 独立 SQL 文件：`sql/order.sql`。以下为与之一致的全文（系统字段同 `BaseEntity`，此处列全）。

### 3.1 t_symbol（交易对）

| 字段 | 类型 | 说明 |
|------|------|------|
| symbol | varchar(32) | 交易对符号，如 BTC/USDT（唯一） |
| base_coin / quote_coin | varchar(32) | 基础币 / 计价币 |
| base_coin_id / quote_coin_id | bigint | 关联 `t_coin`（可选冗余） |
| price_precision / amount_precision | int | 价格 / 数量精度（小数位数） |
| price_tick | bigint | 最小价格变动单位（计价币最小单位），限价须为其整数倍 |
| min_amount / max_amount | bigint | 最小 / 单笔最大下单数量（基础币最小单位） |
| min_notional | bigint | 最小下单名义值 = price×quantity（计价币最小单位） |
| taker_fee_rate / maker_fee_rate | int | 吃单 / 挂单费率（基点 bp，10=0.1%；本阶段默认 0） |
| sort / status | int / tinyint | 排序；状态 0=停牌 1=交易中 |

```sql
CREATE TABLE `t_symbol` (
  `id` bigint NOT NULL COMMENT '交易对ID',
  `symbol` varchar(32) NOT NULL COMMENT '交易对符号:BTC/USDT',
  `base_coin` varchar(32) NOT NULL COMMENT '基础币(被交易资产,如BTC)',
  `quote_coin` varchar(32) NOT NULL COMMENT '计价币(用于标价,如USDT)',
  `base_coin_id` bigint DEFAULT NULL COMMENT '基础币ID(关联t_coin)',
  `quote_coin_id` bigint DEFAULT NULL COMMENT '计价币ID(关联t_coin)',
  `price_precision` int NOT NULL DEFAULT '0' COMMENT '价格精度(小数位数)',
  `amount_precision` int NOT NULL DEFAULT '0' COMMENT '数量精度(小数位数)',
  `price_tick` bigint NOT NULL DEFAULT '1' COMMENT '最小价格变动单位(计价币最小单位)',
  `min_amount` bigint NOT NULL DEFAULT '0' COMMENT '最小下单数量(基础币最小单位)',
  `max_amount` bigint DEFAULT NULL COMMENT '单笔最大下单数量(基础币最小单位)',
  `min_notional` bigint NOT NULL DEFAULT '0' COMMENT '最小下单名义值(计价币最小单位)',
  `taker_fee_rate` int NOT NULL DEFAULT '0' COMMENT '吃单费率(基点,bp;10=0.1%;本阶段默认0)',
  `maker_fee_rate` int NOT NULL DEFAULT '0' COMMENT '挂单费率(基点,bp;本阶段默认0)',
  `sort` int DEFAULT '0' COMMENT '排序',
  `status` tinyint NOT NULL DEFAULT '1' COMMENT '状态:0=停牌(禁止交易),1=交易中',
  -- 系统字段 --
  `create_by` varchar(64) DEFAULT '' COMMENT '创建者',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by` varchar(64) DEFAULT '' COMMENT '更新者',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `is_deleted` tinyint DEFAULT '0' COMMENT '逻辑删除:0=未删,1=已删',
  `version` int DEFAULT '0' COMMENT '乐观锁版本号',
  `tenant_id` bigint DEFAULT '0' COMMENT '租户ID',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_symbol` (`symbol`),
  KEY `idx_base_coin` (`base_coin`),
  KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='交易对表';
```

### 3.2 t_order（订单）

| 字段 | 类型 | 说明 |
|------|------|------|
| order_no | varchar(64) | 业务订单号（**全局唯一**，作为幂等/冻结 requestId 基） |
| client_oid | varchar(64) | 客户端订单号（客户端幂等，防重复下单） |
| user_id / symbol | bigint / varchar(32) | 用户 / 交易对 |
| base_coin / quote_coin | varchar(32) | 冗余币种，资金操作免查 symbol |
| side | tinyint | 1=BUY 买入 2=SELL 卖出 |
| order_type | tinyint | 1=GTC 限价 2=MARKET 市价 |
| price | bigint | 限价（计价币最小单位；市价为 0） |
| quantity | bigint | 下单数量（基础币最小单位；市价买单为 0，见 quote_amount） |
| quote_amount | bigint | 市价买单预算额（计价币最小单位；限价/市价卖单为 0） |
| remaining | bigint | 剩余未成交数量（基础币最小单位） |
| filled_amount | bigint | 已成交数量（基础币最小单位） |
| filled_quote_amount | bigint | 已成交名义值 = Σ(price×qty)（计价币最小单位） |
| avg_price | bigint | 平均成交价（加权） |
| trade_count | int | 成交笔数 |
| fee | bigint | 累计手续费（计价币最小单位，本阶段 0） |
| freeze_request_id | varchar(64) | 冻结幂等号（= order_no，下单时给 asset freeze 用） |
| freeze_quote_amount | bigint | 已冻结计价币金额（买单=price×qty 或市价预算） |
| freeze_base_amount | bigint | 已冻结基础币数量（卖单=quantity） |
| status | tinyint | 状态：0=NEW 1=PARTIAL_FILLED 2=FILLED 3=CANCELLED 4=REJECTED |
| cancel_time / filled_time | datetime | 撤单 / 全部成交时间 |
| remark | varchar(255) | 备注（拒绝/失败原因） |

```sql
CREATE TABLE `t_order` (
  `id` bigint NOT NULL COMMENT '订单ID',
  `order_no` varchar(64) NOT NULL COMMENT '业务订单号(全局唯一,幂等基)',
  `client_oid` varchar(64) DEFAULT NULL COMMENT '客户端订单号(客户端幂等,防重复下单)',
  `user_id` bigint NOT NULL COMMENT '用户ID',
  `symbol` varchar(32) NOT NULL COMMENT '交易对',
  `base_coin` varchar(32) NOT NULL COMMENT '基础币(冗余,资金操作用)',
  `quote_coin` varchar(32) NOT NULL COMMENT '计价币(冗余,资金操作用)',
  `side` tinyint NOT NULL COMMENT '方向:1=BUY买入 2=SELL卖出',
  `order_type` tinyint NOT NULL COMMENT '类型:1=GTC限价 2=MARKET市价',
  `price` bigint NOT NULL DEFAULT '0' COMMENT '限价(计价币最小单位;市价为0)',
  `quantity` bigint NOT NULL DEFAULT '0' COMMENT '下单数量(基础币最小单位;市价买单为0,见quote_amount)',
  `quote_amount` bigint NOT NULL DEFAULT '0' COMMENT '市价买单预算额(计价币最小单位;限价/市价卖单为0)',
  `remaining` bigint NOT NULL DEFAULT '0' COMMENT '剩余未成交数量(基础币最小单位)',
  `filled_amount` bigint NOT NULL DEFAULT '0' COMMENT '已成交数量(基础币最小单位)',
  `filled_quote_amount` bigint NOT NULL DEFAULT '0' COMMENT '已成交名义值(计价币最小单位,Σ price*qty)',
  `avg_price` bigint NOT NULL DEFAULT '0' COMMENT '平均成交价(计价币最小单位)',
  `trade_count` int NOT NULL DEFAULT '0' COMMENT '成交笔数',
  `fee` bigint NOT NULL DEFAULT '0' COMMENT '累计手续费(计价币最小单位,本阶段0)',
  `freeze_request_id` varchar(64) DEFAULT NULL COMMENT '冻结幂等号(asset freeze的requestId)',
  `freeze_quote_amount` bigint NOT NULL DEFAULT '0' COMMENT '已冻结计价币金额(买单=price*quantity或市价预算;最小单位)',
  `freeze_base_amount` bigint NOT NULL DEFAULT '0' COMMENT '已冻结基础币数量(卖单=quantity;最小单位)',
  `status` tinyint NOT NULL DEFAULT '0' COMMENT '状态:0=NEW 1=PARTIAL_FILLED 2=FILLED 3=CANCELLED 4=REJECTED',
  `cancel_time` datetime DEFAULT NULL COMMENT '撤单/结束时间',
  `filled_time` datetime DEFAULT NULL COMMENT '全部成交时间',
  `remark` varchar(255) DEFAULT NULL COMMENT '备注(拒绝/失败原因)',
  -- 系统字段 --
  `create_by` varchar(64) DEFAULT '' COMMENT '创建者',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by` varchar(64) DEFAULT '' COMMENT '更新者',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `is_deleted` tinyint DEFAULT '0' COMMENT '逻辑删除:0=未删,1=已删',
  `version` int DEFAULT '0' COMMENT '乐观锁版本号',
  `tenant_id` bigint DEFAULT '0' COMMENT '租户ID',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_order_no` (`order_no`),
  UNIQUE KEY `uk_client_oid` (`client_oid`),
  KEY `idx_user_time` (`user_id`, `create_time`),
  KEY `idx_symbol_status` (`symbol`, `status`),
  KEY `idx_symbol_side_status` (`symbol`, `side`, `status`),
  KEY `idx_status_time` (`status`, `create_time`),
  KEY `idx_freeze_req` (`freeze_request_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='订单表';
```

### 3.3 t_trade（成交）

| 字段 | 类型 | 说明 |
|------|------|------|
| trade_no | varchar(64) | 成交单号（全局唯一，过户 requestId 基） |
| symbol / price / quantity / quote_amount | — | 交易对 / 成交价 / 成交量 / 名义值 |
| taker_order_no / maker_order_no | varchar(64) | 吃单 / 挂单订单号 |
| taker_order_id / maker_order_id | bigint | 吃单 / 挂单订单 ID |
| taker_user_id / maker_user_id | bigint | 吃单 / 挂单用户 |
| taker_side | tinyint | 吃单方向 1=BUY 2=SELL |
| buy_user_id / sell_user_id | bigint | 买方 / 卖方用户（冗余） |
| taker_fee / maker_fee | bigint | 双方手续费（计价币最小单位，本阶段 0） |
| settle_status | tinyint | 结算状态：0=待结算 1=已结算 2=结算失败待补偿 |
| settle_quote_request_id / settle_base_request_id | varchar(64) | 计价币 / 基础币过户幂等号（=tradeNo:Q / tradeNo:B） |
| trade_time | datetime | 成交时间 |

```sql
CREATE TABLE `t_trade` (
  `id` bigint NOT NULL COMMENT '成交ID',
  `trade_no` varchar(64) NOT NULL COMMENT '成交单号(全局唯一)',
  `symbol` varchar(32) NOT NULL COMMENT '交易对',
  `price` bigint NOT NULL COMMENT '成交价(计价币最小单位)',
  `quantity` bigint NOT NULL COMMENT '成交量(基础币最小单位)',
  `quote_amount` bigint NOT NULL COMMENT '成交名义值=price*quantity(计价币最小单位)',
  `taker_order_no` varchar(64) NOT NULL COMMENT '吃单订单号',
  `maker_order_no` varchar(64) NOT NULL COMMENT '挂单订单号',
  `taker_order_id` bigint NOT NULL COMMENT '吃单订单ID',
  `maker_order_id` bigint NOT NULL COMMENT '挂单订单ID',
  `taker_user_id` bigint NOT NULL COMMENT '吃单用户ID',
  `maker_user_id` bigint NOT NULL COMMENT '挂单用户ID',
  `taker_side` tinyint NOT NULL COMMENT '吃单方向:1=BUY 2=SELL',
  `buy_user_id` bigint NOT NULL COMMENT '买方用户ID(冗余)',
  `sell_user_id` bigint NOT NULL COMMENT '卖方用户ID(冗余)',
  `taker_fee` bigint NOT NULL DEFAULT '0' COMMENT '吃单手续费(计价币最小单位,本阶段0)',
  `maker_fee` bigint NOT NULL DEFAULT '0' COMMENT '挂单手续费(计价币最小单位,本阶段0)',
  `settle_status` tinyint NOT NULL DEFAULT '0' COMMENT '结算状态:0=待结算 1=已结算 2=结算失败待补偿',
  `settle_quote_request_id` varchar(64) DEFAULT NULL COMMENT '计价币过户幂等号(tradeNo:Q)',
  `settle_base_request_id` varchar(64) DEFAULT NULL COMMENT '基础币过户幂等号(tradeNo:B)',
  `trade_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '成交时间',
  -- 系统字段 --
  `create_by` varchar(64) DEFAULT '' COMMENT '创建者',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by` varchar(64) DEFAULT '' COMMENT '更新者',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `is_deleted` tinyint DEFAULT '0' COMMENT '逻辑删除:0=未删,1=已删',
  `version` int DEFAULT '0' COMMENT '乐观锁版本号',
  `tenant_id` bigint DEFAULT '0' COMMENT '租户ID',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_trade_no` (`trade_no`),
  KEY `idx_symbol_time` (`symbol`, `trade_time`),
  KEY `idx_taker_order` (`taker_order_id`),
  KEY `idx_maker_order` (`maker_order_id`),
  KEY `idx_symbol_settle` (`symbol`, `settle_status`),
  KEY `idx_buy_user` (`buy_user_id`),
  KEY `idx_sell_user` (`sell_user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='成交表';
```

**索引与约束要点**

| 表 | 关键约束 | 用途 |
|----|---------|------|
| t_symbol | `UNIQUE(symbol)` | 交易对符号唯一 |
| t_order | `UNIQUE(order_no)` **+** `UNIQUE(client_oid)` | ① 订单号唯一（冻结/幂等基）；② 客户端订单号幂等，防重复下单 |
| t_order | `KEY(symbol, side, status)` | 撮合引擎重启时按交易对/方向/活跃状态加载订单簿 |
| t_order | `KEY(status, create_time)` | 撤单/超时扫描、对账 |
| t_trade | `UNIQUE(trade_no)` | 成交单号唯一（过户幂等基） |
| t_trade | `KEY(symbol, settle_status)` | 结算失败补偿扫描（settle_status=2 重试） |
| t_trade | `KEY(taker_order_id)`/`KEY(maker_order_id)` | 按订单查成交明细 |

---

## 四、订单状态机

> 状态编码存 `t_order.status`（tinyint），与架构图附录一致，语义如下。

```
                        ┌──────────────────────────────────────────┐
                        │            （下单成功，资金已冻结）         │
                        ▼                                          │
   ┌────────────┐   预冻结失败/校验失败    ┌─────────────────┐      │
   │  NEW (0)   │ ───────────────────────▶ │  REJECTED (4)   │ 终止  │
   │ 待撮合(挂单) │                         └─────────────────┘      │
   └─────┬──────┘                                                  │
         │  撮合到部分/全部                                          │
         ▼                                                          │
   ┌────────────────┐   剩余全部成交     ┌─────────────────┐        │
   │PARTIAL_FILLED(1)│ ────────────────▶ │   FILLED (2)    │ 终止    │
   │   部分成交       │                  └─────────────────┘        │
   └─────┬──────────┘                                              │
         │  用户撤剩余（成交部分已过户，剩余解冻）                      │
         ▼                                                          │
   ┌────────────────┐                                              │
   │  CANCELLED (3)  │ 终止                                          │
   └────────────────┘                                              │
   (NEW 未成交即撤单 / PARTIAL_FILLED 撤剩余，均到 CANCELLED)
```

| 状态 | 编码 | 含义 | 资金状态（asset 侧） | 触发动作 |
|------|------|------|---------------------|---------|
| **NEW** | 0 | 待撮合，挂单中（或市价刚进引擎） | 全额已冻结（买单冻结计价币，卖单冻结基础币） | 下单成功、asset freeze 返回成功 |
| **PARTIAL_FILLED** | 1 | 部分成交，剩余仍在订单簿 | 已成交部分走过户（freeze→transfer），剩余仍冻结 | 撮合到 ≥1 笔但未全成交 |
| **FILLED** | 2 | 全部成交（终止态） | 全额过户完毕（freeze 全部消耗，无剩余可解冻） | remaining 归 0 |
| **CANCELLED** | 3 | 已撤单（终止态） | 未成交剩余 `unfreeze` 解冻；成交部分已过户 | 用户撤单 / 系统停牌撤单 |
| **REJECTED** | 4 | 已拒绝（终止态） | 无冻结（下单校验/预冻结失败，不产生资金变动） | 下单校验失败、余额不足、交易对停牌、超出精度 |

**状态机合法流转表（单向，不可逆）**

| 当前状态 | 允许转移 | 说明 |
|---------|---------|------|
| NEW (0) | → PARTIAL_FILLED(1) | 撮合到部分成交 |
| NEW (0) | → FILLED(2) | 一次性全成交 |
| NEW (0) | → CANCELLED(3) | 未成交即撤单 |
| NEW (0) | → REJECTED(4) | 下单被拒（校验/冻结失败） |
| PARTIAL_FILLED (1) | → FILLED(2) | 剩余全部成交 |
| PARTIAL_FILLED (1) | → CANCELLED(3) | 撤剩余未成交部分 |
| FILLED / CANCELLED / REJECTED | — | **终止态**，不可再转移 |

**状态机约束**：
- 状态变更用 MyBatis-Plus 乐观锁（`version`）更新，`UPDATE ... WHERE id=? AND status=?`，避免并发重复成交/重复撤单。
- `remaining > 0 && status IN (0,1)` ⇒ 该订单在订单簿中；`remaining == 0` ⇒ 必为 FILLED。
- 撤单前置校验：仅 `NEW/PARTIAL_FILLED` 可撤；`FILLED/CANCELLED/REJECTED` 幂等返回「已终态」。
- 撤单动作：① 从内存订单簿移除 → ② 更新 `t_order.status=CANCELLED, cancel_time` → ③ 调 asset `unfreeze` 解冻剩余（见 §6.3）。

---

## 五、内存撮合引擎方案

### 5.1 总体架构（单机内存撮合）

```
                     ┌────────────────────────────── exchange-order (8104) ─────────────────────────────┐
 用户 REST             │                                                                                  │
 ──────▶ /api/order/** │  OrderService 下单校验                                                            │
   (经网关)            │    │ ① 落库 t_order(NEW) + 计算冻结额                                          │
                      │    ▼                                                                             │
                      │  AssetClient.freeze ──Feign──▶ exchange-asset /internal/asset/freeze（预冻结）      │
                      │    │ 冻结成功                                                                      │
                      │    ▼                                                                             │
                      │  ┌────────────────────────────────────────────┐                                 │
                      │  │  MatchingEngine（内存撮合）                   │                                 │
                      │  │  ConcurrentHashMap<symbol, OrderBook>        │                                 │
                      │  │  ├─ OrderBook: bids(买盘价降序) / asks(卖盘价升序)│                                │
                      │  │  ├─ 每交易对一条 ReentrantLock 串行化          │                                 │
                      │  │  └─ match(order) → List<Trade>               │                                 │
                      │  └────────────────────────────────────────────┘                                 │
                      │    │ 成交                                                                            │
                      │    ▼                                                                             │
                      │  ① 落库 t_trade + 更新 t_order(remaining/avg_price/status)                        │
                      │  ② AssetClient.transfer ×2 ──Feign──▶ asset（计价币 + 基础币过户）                  │
                      │  ③ 发 ORDER-TRADE 事件 ──RocketMQ──▶ asset(过户)/notify(成交通知)                  │
                      └──────────────────────────────────────────────────────────────────────────────────┘
```

### 5.2 订单簿数据结构

| 组件 | 结构 | 说明 |
|------|------|------|
| `OrderBook` | `TreeMap<Long, PriorityQueue<Order>> bids` 买盘（价格**降序**） | 价格优先：价格高者先成交；同价按 `TreeMap` 插入序（时间优先） |
| | `TreeMap<Long, PriorityQueue<Order>> asks` 卖盘（价格**升序**） | 价格优先：价格低者先成交 |
| 时间优先 | 同价档内用 FIFO `PriorityQueue` 或队列，`createTime` 递增 | 先到先成交；同价先挂单者优 |
| 引擎容器 | `ConcurrentHashMap<String, OrderBook>` | 键 = symbol（如 `BTC/USDT`） |

> 说明：Phase 2 用 `TreeMap + 队列` 即可正确实现价格/时间优先，无需 Disruptor（Phase 5 再优化）。内存订单簿仅存 `remaining>0` 的活跃限价单。

### 5.3 撮合算法（限价 GTC + 市价）

**下单入口统一 `match(symbol, newOrder)`，先 `lock(symbol)` 串行化，再执行：**

```
match(新订单 N):
  N.side == BUY:
     对手盘 = asks（卖盘，升序）
     限价 BUY：持续取 asks 中 price <= N.price 的最优卖单撮合；成交价=卖单挂单价
     市价 BUY：持续取 asks 最优卖单撮合，直到 quote_amount 预算耗尽 或 卖盘为空
  N.side == SELL:
     对手盘 = bids（买盘，降序）
     限价 SELL：持续取 bids 中 price >= N.price 的最优买单撮合；成交价=买单挂单价
     市价 SELL：持续取 bids 最优买单撮合，直到 N.quantity 耗尽 或 买盘为空

  单笔撮合 fill(maker, taker, price, qty):
     qty = min(taker 剩余, maker 剩余)
     生成 Trade(trade_no=雪花, taker=新单, maker=旧单, price, qty, quote_amount=price*qty)
     更新 taker.remaining, maker.remaining, 双方 filled/avg_price/trade_count
     maker.remaining == 0 → 从订单簿移除
  撮合完：
     taker.remaining > 0 且是限价 → 入订单簿（保留）；市价未完全成交 → 剩余作废（成交多少算多少）
```

**关键规则**：
- **价格优先**：对手盘始终取「最有利价」——买盘取最高出价（asks 匹配时卖盘取最低价），由 `TreeMap` 有序保证。
- **时间优先**：同价档 FIFO，先入簿者先成交。
- **成交价 = 挂单（maker）的挂单价**，吃单按挂单价成交（经典的「挂单价成交」规则，不吃单报价）。
- **市价买单按 quote_amount 预算撮合**：按对手盘价格逐档吃掉，直到预算花完或卖盘空；剩余预算不再下单。
- **市价卖单按 quantity 撮合**：按对手盘价格逐档吃掉，直到数量耗尽或买盘空。

### 5.4 落库与撮合的顺序（先落库、后撮合、再结算）

> 为保证「可追溯、不丢单、可补偿」，采用**串行三步**，且撮合与同交易对的资金过户在同一条串行通道内完成（避免同交易对乱序）：

```
1. 【落库】OrderService：
     - 校验（symbol 交易中、价格/数量精度、min/max/min_notional、side 与 amount 匹配）
     - 计算冻结额（BUY=price×qty 或市价 quote_amount；SELL=quantity）
     - INSERT t_order(status=NEW, freeze_*)
2. 【预冻结】调 asset freeze（requestId=order_no）→ 成功才继续，失败→ REJECTED
3. 【撮合】引擎 lock(symbol) 串行化 → match(N)
4. 【结算·落成交】对每笔成交：
     - INSERT t_trade(settle_status=0 待结算)
     - UPDATE 双方 t_order(remaining/avg_price/trade_count/status)
     - 更新成交双方订单状态（若 N.remaining>0 → PARTIAL_FILLED，==0 → FILLED）
5. 【过户】对每笔成交调 asset transfer ×2（计价币+基础币，requestId=tradeNo:Q / tradeNo:B）
     - 成功 → UPDATE t_trade SET settle_status=1
     - 失败 → 保留 settle_status=2，定时补偿任务按 (symbol,settle_status=2) 重试（幂等）
6. 【发事件】发 ORDER-TRADE 到 RocketMQ（body 见 mq-topics.md）
```

**关于「落库 vs 过户」的取舍**：
- 本阶段采用**「先落成交记录、后资产过户」**：成交单先入库（可追溯），再逐笔过户。过户失败只影响该笔 settle_status，不影响成交事实，可由补偿任务幂等重试。
- **不做**「先过户后落库」——那会导致资金已动但无记录，出错不可追踪。
- 撮合与过户**放在同交易对的串行通道内**，天然避免「同用户同交易对并发下单导致冻结/过户错序」；不同交易对互不阻塞。

### 5.5 交易对分 key 串行化

- 引擎用 `ConcurrentHashMap<String, OrderBook>`，key = symbol。
- 撮合、撤单、成交落库、同交易对过户均需 `lock(symbol)`（`ReentrantLock` 数组 / striped lock 或每 OrderBook 内一把锁）。
- 不同交易对并发互不影响；同一交易对严格串行，保证订单簿一致性与价格/时间优先正确性。
- 撤单：`lock(symbol)` → 从簿移除 → 更新状态 → unfreeze（同串行通道，避免撤单与成交竞态）。

### 5.6 内存订单簿重启恢复（Phase 2 简化）

- 启动时按 `t_order` 中 `status IN (0,1)` 且 `remaining>0` 的限价单，按 `create_time` 升序重建订单簿（**已冻结、未成交部分直接入簿**）。
- 冻结/过户均靠 asset 幂等 `requestId`（order_no / tradeNo:Q / tradeNo:B）保证重启后重复请求不重复扣账。
- 市价单不落簿（一次性撮合），无需恢复。
- **快照表 `t_order_book_snapshot`（Phase 5 可选）**：按 `(symbol, seq)` 存订单簿序列化快照加速恢复，本阶段不建表，仅预留。

---

## 六、order→asset 资金接口契约

> order 通过 Feign 调 `exchange-asset` 已实现的 `/internal/asset/**`。资金 DTO（`FreezeRequest`/`UnfreezeRequest`/`TransferRequest`）已在 `exchange-common` 资产 dto 包定义（见 `docs/asset-domain.md §4.1`），order 直接复用。统一返回 `Result<T>`，金额一律 `long` 最小单位，`requestId` 幂等。

### 6.1 下单预冻结（order → asset `POST /internal/asset/freeze`）

- **币种方向**：
  - **买单（BUY）**：冻结**计价币 quote_coin**，金额 = `price × quantity`（限价）或 `quote_amount`（市价预算）。
  - **卖单（SELL）**：冻结**基础币 base_coin**，金额 = `quantity`。
- **幂等 requestId**：`order_no`（下单号）。同一订单重复冻结，asset 幂等返回首次结果。

```java
// 买单：冻结计价币
FreezeRequest buyFreeze = FreezeRequest.builder()
    .requestId(order.getOrderNo())          // 幂等
    .userId(order.getUserId())
    .symbol(order.getQuoteCoin())           // USDT
    .amount(order.getFreezeQuoteAmount())   // price*quantity 或市价预算(计价币最小单位)
    .bizType("FREEZE")
    .refNo(order.getOrderNo())
    .build();

// 卖单：冻结基础币
FreezeRequest sellFreeze = FreezeRequest.builder()
    .requestId(order.getOrderNo())
    .userId(order.getUserId())
    .symbol(order.getBaseCoin())            // BTC
    .amount(order.getFreezeBaseAmount())    // quantity(基础币最小单位)
    .bizType("FREEZE")
    .refNo(order.getOrderNo())
    .build();
```

> `freeze_quote_amount` 与 `freeze_base_amount` 存于 `t_order`，供成交过户与撤单解冻计算尾差。

### 6.2 成交过户（order → asset `POST /internal/asset/transfer`）

**每笔成交需要 2 笔过户（计价币 + 基础币）**，币种流向固定：**买单方付计价币、收基础币；卖单方付基础币、收计价币**。taker/maker 只影响手续费费率，**不影响币种流向**。

| 过户 | symbol | fromUserId（转出·冻结→可用） | toUserId（转入·可用增加） | amount | requestId（幂等） |
|------|--------|------------------------------|--------------------------|--------|-------------------|
| **计价币过户 Q** | quote_coin（USDT） | **买方** buy_user_id | **卖方** sell_user_id | `price × quantity` | `tradeNo:Q` |
| **基础币过户 B** | base_coin（BTC） | **卖方** sell_user_id | **买方** buy_user_id | `quantity` | `tradeNo:B` |

- **为什么是 2 笔**：买单预先冻结的是 USDT，卖单预先冻结的是 BTC，二者币种不同，无法用 1 笔 transfer 互换，必须分别在两个币种账户间过户。
- **换算说明（简化约定）**：本域**不做跨币种换算**——买单方冻结的 USDT 通过 `Q` 笔全额过户给卖单方（卖单方可用增加 USDT）；卖单方冻结的 BTC 通过 `B` 笔全额过户给买单方（买单方可用增加 BTC）。`price × quantity` 即 USDT 端到端等价名义值，由计价币 `Q` 笔体现。
- **幂等**：`requestId = tradeNo + ":Q"` / `tradeNo + ":B"`。重复过户（Feign 重试/补偿任务）幂等命中返回首次结果，不重复划转。

```java
// 计价币过户（买单方冻结的 USDT → 卖单方可用）
TransferRequest quoteTransfer = TransferRequest.builder()
    .requestId(trade.getTradeNo() + ":Q")          // 幂等
    .fromUserId(trade.getBuyUserId())               // 买单方(冻结→可用)
    .toUserId(trade.getSellUserId())                // 卖单方(可用增加)
    .symbol(trade.getQuoteCoin())                   // USDT
    .amount(trade.getQuoteAmount())                 // price*quantity
    .bizType("TRANSFER")
    .refNo(trade.getTradeNo())
    .build();

// 基础币过户（卖单方冻结的 BTC → 买单方可用）
TransferRequest baseTransfer = TransferRequest.builder()
    .requestId(trade.getTradeNo() + ":B")
    .fromUserId(trade.getSellUserId())              // 卖单方(冻结→可用)
    .toUserId(trade.getBuyUserId())                 // 买单方(可用增加)
    .symbol(trade.getBaseCoin())                    // BTC
    .amount(trade.getQuantity())
    .bizType("TRANSFER")
    .refNo(trade.getTradeNo())
    .build();
```

> **手续费（本阶段 fee=0，字段预留）**：Phase 2 简化——`taker_fee_rate/maker_fee_rate` 默认 0，成交不额外扣手续费。后续接入费率时，可在过户基础上按费率额外发起一笔 `FEE` 类扣减（向平台/币种账户），此处仅预留 `taker_fee/maker_fee/fee` 字段。

### 6.3 撤单解冻（order → asset `POST /internal/asset/unfreeze`）

- 撤单时解冻**未成交的剩余冻结**（成交部分已通过 transfer 消耗冻结，无需再解冻）。
- **剩余冻结额计算**：
  - **买单**：`剩余冻结计价币 = freeze_quote_amount − filled_quote_amount`（未成交的名义值尾差）。
  - **卖单**：`剩余冻结基础币 = freeze_base_amount − filled_amount`。
- **幂等 requestId**：撤单时用 `order_no + ":C"`（区别于下单冻结的 `order_no`，二者是不同流水）。

```java
// 买单撤单：解冻剩余计价币
UnfreezeRequest buyUnfreeze = UnfreezeRequest.builder()
    .requestId(order.getOrderNo() + ":C")                       // 幂等
    .userId(order.getUserId())
    .symbol(order.getQuoteCoin())                               // USDT
    .amount(order.getFreezeQuoteAmount() - order.getFilledQuoteAmount())
    .bizType("UNFREEZE")
    .refNo(order.getOrderNo())
    .build();

// 卖单撤单：解冻剩余基础币
UnfreezeRequest sellUnfreeze = UnfreezeRequest.builder()
    .requestId(order.getOrderNo() + ":C")
    .userId(order.getUserId())
    .symbol(order.getBaseCoin())                                // BTC
    .amount(order.getFreezeBaseAmount() - order.getFilledAmount())
    .bizType("UNFREEZE")
    .refNo(order.getOrderNo())
    .build();
```

### 6.4 requestId 幂等约定汇总

| 操作 | requestId | 对应 asset 接口 | 幂等键落点 |
|------|-----------|-----------------|-----------|
| 下单冻结 | `order_no` | `/internal/asset/freeze` | `t_asset_ledger.uk_request_id` |
| 撤单解冻 | `order_no:C` | `/internal/asset/unfreeze` | `t_asset_ledger.uk_request_id` |
| 成交·计价币过户 | `trade_no:Q` | `/internal/asset/transfer` | `t_asset_ledger.uk_request_id` |
| 成交·基础币过户 | `trade_no:B` | `/internal/asset/transfer` | `t_asset_ledger.uk_request_id` |

> **约定**：requestId 由 **order（调用方）** 生成并保证确定性——同一业务重复发起时 requestId 相同，asset 只做唯一性校验与回读，重复请求幂等返回首次结果（不重放资金操作）。

### 6.5 Feign 客户端示例（order 侧）

```java
@FeignClient(name = "exchange-asset", path = "/internal/asset")
public interface AssetClient {
    @PostMapping("/freeze")
    Result<LedgerVO> freeze(@RequestBody FreezeRequest req);

    @PostMapping("/unfreeze")
    Result<LedgerVO> unfreeze(@RequestBody UnfreezeRequest req);

    @PostMapping("/transfer")
    Result<LedgerVO> transfer(@RequestBody TransferRequest req);

    @GetMapping("/account/balance")
    Result<AccountVO> getBalance(@RequestParam("userId") Long userId,
                                 @RequestParam("symbol") String symbol);
}
```

---

## 七、撮合正确性单测要点

> 用「纯函数式」撮合引擎 + JUnit 断言，重点验证**价格优先 / 时间优先 / 金额守恒**。建议撮合引擎核心写成不依赖 DB 的纯类，便于单测。

### 7.1 价格优先
- 限价买 @100，簿内卖盘有 @102、@101、@99 → 必须先与 @99 成交（最优卖价）。
- 限价卖 @10，簿内买盘有 @8、@9、@11 → 必须先与 @11 成交（最优买价）。

### 7.2 时间优先（同价先到先得）
- 簿内两个卖单同 @100（A 先挂 5，B 后挂 5），一个买单 @100 买 7 → 第一笔吃满 A 的 5，第二笔吃 B 的 2；**成交记录顺序 A→B**，A 优先耗尽。

### 7.3 撮合量/价守恒
- 对每笔成交：`quantity == min(takerRemaining, makerRemaining)`；`quote_amount == price × quantity`。
- 撮合前后：吃单+挂单的 `remaining` 变化之和 == 总成交量；无凭空成交/重复成交。

### 7.4 限价不可穿透
- 买单 @100 **绝不能**与 @101 的卖单成交；卖单 @10 **绝不能**与 @9 的买单成交。

### 7.5 市价单行为
- 市价买单预算耗尽即止：预算 1000 USDT、簿卖价 @100×6 → 成交 6（花 600），余预算 400 不再成交（簿无更低价）。验证剩余不落簿。
- 市价卖单数量耗尽即止：quantity=5，簿买价 @10×3 + @9×4 → 成交 3（@10）+ 2（@9）=5，停止；验证不超卖。

### 7.6 部分成交与状态
- 吃单未完全成交且为限价 → 状态 PARTIAL_FILLED，remaining>0 且留在簿内可被后续单撮合。
- 撤单后订单不再参与撮合；同价后续单优先于被撤单者。
- 全成交 → FILLED，remaining=0。

### 7.7 资金契约单测（mock asset）
- 买单下单 freeze 币种=计价币、金额=price×qty；卖单 freeze 币种=基础币、金额=qty。
- 每笔成交恰好 2 次 transfer，币种/方向/金额、requestId=`tradeNo:Q`/`tradeNo:B` 正确。
- 撤单 unfreeze 金额=剩余冻结，requestId=`order_no:C`。
- 同 requestId 重复调用 freeze/transfer/unfreeze 不重复扣账（幂等）。

---

## 八、落地 Checklist（/dev 实施指引）

- [ ] 执行 `sql/order.sql` 建表（库 `web3_exchange`）。
- [ ] `exchange-order` 新增实体 `Symbol`/`Order`/`Trade`，继承 `BaseEntity`，金额字段 `Long`；`@TableName("t_symbol"/"t_order"/"t_trade")`。
- [ ] `OrderMapper`/`SymbolMapper`/`TradeMapper`，含活跃订单加载、成交结算补偿扫描查询。
- [ ] 内存撮合引擎 `MatchingEngine`：`ConcurrentHashMap<String, OrderBook>` + 每交易对锁；`OrderBook` 用 `TreeMap<Long, PriorityQueue<Order>>` 实现价格/时间优先。
- [ ] 撮合引擎**纯类化**，接入 §7 单测用例（价格/时间优先、量价守恒、限价不可穿透、市价行为）。
- [ ] `OrderService`：下单校验 → 落库 → asset freeze → 撮合 → 成交落库/过户 → 发事件；状态机流转用乐观锁实现。
- [ ] `TradeSettleService`：成交结算（transfer×2 + settle_status 更新）+ 定时补偿（settle_status=2 幂等重试）。
- [ ] `CancelService`：撤单（簿移除 → 状态 CANCELLED → unfreeze）。
- [ ] Feign `AssetClient`（freeze/unfreeze/transfer/balance）对接 exchange-asset；requestId 按 §6.4 约定生成。
- [ ] 启动时重建订单簿（status IN(0,1) 且 remaining>0 的限价单按 create_time 升序）。
- [ ] RocketMQ `ORDER-TRADE` 生产者（tradeId 为幂等键），对接 `docs/mq-topics.md` 契约。
- [ ] 对外 REST `/api/order/**`（下单/撤单/查单/查成交），经网关路由；内部接口/事件不对外。
- [ ] 验收：下单→撮合→成交→资金过户（计价币+基础币）全链路端到端跑通；撮合正确性单测全绿。
