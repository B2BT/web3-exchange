# Web3-Exchange 微服务架构设计文档

> 版本：v1.0 · 作者：系统架构师 · 日期：2026-08-04
> 适用代码基线：Spring Boot 3.2.5 / Spring Cloud 2023.0.1 / Spring Cloud Alibaba 2023.0.1.0 / Java 17 / MyBatis-Plus 3.5.7 / MySQL 8 / Redis 7 / Nacos 2.4 / web3j 4.10.3
> 配套文件：`docs/PROJECT_MEMORY.md`（项目现状基线，只读，本设计与其互补）
> 定位：本文件是**全局架构蓝图**，指导后续模块落地；不含具体代码实现细节（由各模块开发文档补充）。

---

## 目录

1. [当前问题分析](#一当前问题分析)
2. [多种方案比较](#二多种方案比较)
3. [推荐方案](#三推荐方案)
4. [技术风险](#四技术风险)
5. [后续实施步骤](#五后续实施步骤)
6. [附录：关键数据库表设计](#附录关键数据库表设计)

---

## 一、当前问题分析

### 1.1 现状盘点

| 模块 | 端口 | 职责 | 状态 | 主要问题 |
|------|------|------|------|----------|
| `exchange-common` | — | 公共类库 | ✅ 完整 | 统一 `Result`、异常体系、`BaseEntity`、监控、MyBatis-Plus 配置齐全，质量良好，可作为基座 |
| `exchange-user` | 8101 | 用户服务 | 🟡 基本可用 | `type-aliases-package` 指向错误包；`UserServiceImpl` 把 `password`/`secretKey` 塞进 DTO（**敏感泄露**）；部分接口返回裸 DTO 而非 `Result<T>` |
| `exchange-auth` | 8102 | 认证服务 | ⚠️ 不完整 | **无法编译**：`AuthServiceImpl` 引用不存在的 `CaptchaService`、`auth.service.UserService`、`auth.domain.UserPrincipal`；`AuthController` 引用缺失的 `SecurityUtils`、`RefreshTokenRequest`；`application.yml` 有中文句号、`cig.import` 拼写、springdoc 缩进错误 |
| `exchange-gateway` | 8080 | API 网关 | ⚠️ 空壳 | `AuthFilter` 全部被注释；路由配置与 Nacos 服务名不一致（`auth-service` vs 实际 `exchange-auth`）；无限流/跨域/WebSocket 支持 |
| `exchange-asset` | — | 资产服务 | 🅿️ 空骨架 | 仅 pom+yml，无任何代码/表设计 |
| `exchange-order` | — | 订单服务 | 🅿️ 空骨架 | 仅 pom+yml，无撮合引擎/订单模型 |
| `exchange-chain` | — | 链上服务 | 🅿️ 空骨架 | web3j 4.10.3 已声明**未使用**，无多链抽象/区块监听/钱包管理 |
| `exchange-notify` | — | 通知服务 | 🅿️ 空骨架 | 仅 pom+yml |
| `exchange-monitor` | — | 监控服务 | 🅿️ 空骨架 | 仅 pom+yml |
| **`exchange-market`** | — | 行情服务 | ❌ **不存在** | 行业标准 9 大功能域中「行情数据」完全没有对应模块 |

### 1.2 关键缺口清单

1. **基础设施类**
   - 网关 `AuthFilter` 空壳，认证过滤器与 Nacos 服务名不一致，导致路由/鉴权链路断裂。
   - 无统一鉴权上下文传播（网关如何把 userId/roles 透传给下游）。

2. **核心交易类（最缺）**
   - **无撮合引擎**：`exchange-order` 是空的，订单从下达到成交的整个链路无设计。
   - **无资产账户体系**：`exchange-asset` 空，钱包账户、余额、冻结、充值/提现、资产流水完全缺失。
   - **无链上交互**：`exchange-chain` 空，web3j 声明未用，无充值监听/提现上链/冷热钱包。

3. **支撑类**
   - **无行情模块** `exchange-market`（K线、深度、ticker），行业标准域缺失。
   - **无消息中间件**：现有依赖无 MQ，撮合/充值/通知间的异步解耦无载体（需决策是否引入 RocketMQ/Kafka）。
   - **无调度**：区块监听、提现打包、过期订单清理等定时任务无承载。

4. **工程质量类**
   - auth 无法编译阻塞整体构建；敏感字段泄露；配置文件名/路径混乱（`exchange-chain`、`exchange-order` 的 yml 里 `spring.application.name` 都写成了 `exchange-common`）。
   - 所有空骨架模块的 yml 均为 `exchange-common` 的复制，端口 8101 冲突。

### 1.3 结论

**现状是"用户/认证域半成品 + 交易核心完全空白"。** 要成为可运营的 Web3 交易所，必须先补齐四大交易相关模块（asset/order/chain/market）与接入层（gateway），并把 auth/user 的既有问题修复到可编译、可上线。

---

## 二、多种方案比较

### 2.1 微服务划分方案

**方案 A：保持 9+1 域拆分（推荐）**
按业务域拆分：`gateway / user / auth / asset / order / chain / market / notify / monitor`，另加 `common` 基座。
- ✅ 与现有模块结构几乎一致，改动最小，复用已有骨架。
- ✅ 每个域独立部署/扩容，符合行业标准 9 大域。
- ✅ 撮合引擎可独立演进，性能瓶颈与业务解耦。
- ⚠️ 跨域调用多（Feign 依赖），需治理好调用链与分布式事务。
- ⚠️ 运维成本高于单体。

**方案 B：先单体，后拆分（模块化单体）**
先在一个应用内用模块分包实现全部业务，跑通后再拆微服务。
- ✅ 起步快，无分布式事务问题，调试简单。
- ❌ 与现有 9 个已建 Maven 子模块冲突，推倒重来。
- ❌ 交易所对撮合延迟要求极高，模块化单体无法独立水平扩展撮合引擎。
- ❌ 后期拆分成本高，重构风险大。

**方案 C：超大粒度假合并**
把 asset+order+chain 合成一个"交易服务"，market+notify 合并。
- ❌ 过度耦合，撮合性能与资产安全混在一起，故障域扩大。
- ❌ 违背单一职责，团队协作冲突多。

> **结论：选 A**。尊重现有骨架，按业务域拆分，撮合引擎作为 order 内部的可独立扩展组件。

### 2.2 撮合引擎选型

**方案 A：自研内存撮合引擎（推荐，单机 + 分 key 扩展）**
用 Java 内存数据结构（`ConcurrentSkipListMap` 价格层 + 双端队列）维护订单簿，订单按交易对分 key，用锁/无锁队列串行化同一交易对的撮合。
- ✅ 延迟极低（微秒~毫秒级），可控性最强，无外部依赖。
- ✅ 完全贴合本项目的现货限价/市价撮合需求，可定制（冰山单、改单等）。
- ✅ 内存撮合 + 异步持久化是主流交易所（币安/火币早期）验证过的路子。
- ⚠️ 单机容量受内存限制；宕机会丢失未落盘内存单（需配合订单快照 + WAL 恢复）。
- ⚠️ 无现成组件，需自行实现订单簿、撮合算法、成交回报，工作量大。

**方案 B：消息队列撮合（Kafka/RocketMQ 做订单总线）**
订单写入 MQ，消费者拉取后撮合。
- ✅ 天然异步削峰，天然可水平扩展。
- ❌ 撮合本身仍是单点逻辑，MQ 只是传输层，没有解决撮合并发问题。
- ❌ 多一跳 MQ 增加撮合延迟（毫秒级→几十毫秒），对高频撮合不利。
- ✅ 更适合作为**下单入口解耦**（订单入库）+ 撮合后**事件广播**，而非撮合主体。

**方案 C：引入现成撮合组件/平台**
如 OpenHFT/Chronicle、或第三方撮合服务（做市商撮合 SaaS）。
- ✅ 开箱即用，性能高。
- ❌ 商业授权/闭源、技术栈不匹配（多为 C++/Rust）、与 Spring 生态集成成本高、不可定制。
- ❌ 依赖第三方，安全与合规不可控（交易所核心不应外包）。

> **结论：选 A（自研内存撮合）+ B 的传输层思想**：撮合引擎内置于 `exchange-order`，用内存订单簿撮合；外部订单经消息队列/HTTP 进入，撮合成交事件再经 MQ 广播给 asset/notify/market。这样既有内存撮合的低延迟，又有 MQ 的解耦与扩展性。

### 2.3 行情模块取舍

**方案 A：新增独立 `exchange-market` 服务（推荐）**
从成交事件聚合 K 线、维护深度快照、广播 ticker/深度/K线；内存缓存 + Redis 发布订阅/WebSocket 推送。
- ✅ 独立扩容，承受最高 QPS 的读流量。
- ✅ 与撮合解耦，撮合只发成交事件，行情自己聚合。
- ✅ 行业标准域，必选。
- ⚠️ 需新增一个模块，需要引入 WebSocket 推送能力。

**方案 B：不建独立模块，把行情放进 order**
- ❌ order 已承担撮合高负载，再背行情读流量会互相拖累。
- ❌ 违背单一职责。

**方案 C：依赖第三方行情 API（CoinGecko 等）**
- ❌ 外部依赖不可控、延迟高、有数据源费用，且仅做参考价可，不能做自家撮合盘口。

> **结论：选 A**，新增 `exchange-market`，数据来自撮合成交事件与链上/参考价源。

### 2.4 消息中间件选择（支撑项）

- **RocketMQ**：与 Spring Cloud Alibaba 生态契合，事务消息支持好（可支撑充值入账、提现等资金对账），推荐。
- **Kafka**：吞吐更高，但资金事务能力弱于 RocketMQ。
- **RabbitMQ**：简单易用，但吞吐与事务消息弱。
> **推荐 RocketMQ**（事务消息解决资金一致性问题），本地无集群时可用单机 embedded 起步。若团队不熟可暂用 Kafka + 应用层补偿，但资金链路建议事务消息。

---

## 三、推荐方案

### 3.1 总体架构（目标态）

```
                        ┌────────────────────────────────────────────┐
                        │         前端 / APP / 量化机器人             │
                        └───────────────────┬────────────────────────┘
                                            │ HTTPS / WSS
                                            ▼
                        ┌────────────────────────────────────────────┐
                        │   exchange-gateway (8080) · Spring Cloud    │
                        │   Gateway 路由+鉴权(AuthFilter)+限流+跨域    │
                        │   认证校验(校验JWT,透传userId)→ /ws/** WebSocket │
                        └───────────────────┬────────────────────────┘
                                            │ lb:// 按服务名路由
   ┌──────────┬──────────┬──────────┬───────┴─────┬──────────┬──────────┐
   ▼          ▼          ▼          ▼             ▼          ▼          ▼
┌──────┐   ┌──────┐   ┌──────┐   ┌──────┐      ┌──────┐   ┌──────┐   ┌──────┐
│ user │   │ auth │   │ asset│   │ order│      │ chain│   │market│   │notify│
│ 8101 │   │ 8102 │   │ 8103 │   │ 8104 │      │ 8105 │   │ 8106 │   │ 8107 │
└──┬───┘   └──┬───┘   └──┬───┘   └──┬───┘      └──┬───┘   └──┬───┘   └──┬───┘
   │          │          │          │             │          │          │
   │   Feign  │          │   Feign  │             │  Feign   │          │
   └──────────┴──────────┴──────────┴─────────────┴──────────┴──────────┘
                                            │
                     ┌──────────────────────┴───────────────────────┐
                     │   RocketMQ (事务/普通消息): 成交事件/充值/通知    │
                     └──────────────────────┬───────────────────────┘
                                            │
   ┌──────────┬──────────┬──────────┬───────┴─────┬──────────┐
   ▼          ▼          ▼          ▼             ▼          ▼
 ┌─────┐   ┌─────┐   ┌─────┐   ┌─────┐        ┌─────┐    ┌─────┐
 │ MySQL│  │ Redis│  │ Nacos│  │链上 │(eth/bsc..│ 冷钱包│   │ Actuator │
 │(多库)│  │(缓存/ │  │注册/ │  │RPC │ )       │(离链) │   │ /Prometheus│
 │      │  │ 会话) │  │配置  │  │     │        │      │   │            │
 └─────┘   └─────┘   └─────┘   └─────┘        └─────┘    └─────┘
```
（`exchange-monitor` 与 `exchange-common` 的监控共同承担运维可观测，见 3.2.8。）

### 3.2 目标模块划分与职责

#### 3.2.1 `exchange-common`（基座，无端口）—— 已完成，保持稳定
- 统一 `Result<T>`、统一异常体系、`BaseEntity/BaseDTO/BaseVO`、全局异常处理器。
- MyBatis-Plus 配置（逻辑删除、乐观锁、字段填充、分页、雪花 ID）。
- 监控（`ExceptionMonitor`）+ 新增：**跨服务用户上下文**（`UserContext`/`LoginUser`，从网关透传的 header 解析），供所有业务服务使用。
- 新增：统一 Web3 异常体系已具备（Wallet/Transaction/Token/Signature/Gas/Contract），补一个**通用事件消息 DTO**（成交、充值、提现等）放 common，供多模块复用。
- 依赖方向：**所有业务模块依赖 common**；common 不依赖任何业务模块。

#### 3.2.2 `exchange-gateway`（8080）—— 接入层，必补
- Spring Cloud Gateway + Nacos 服务发现，按服务名 `lb://exchange-xxx` 路由。
- **`AuthFilter`（GlobalFilter）实现**：解析 JWT（从 common 复用校验逻辑或独立网关 JWT 工具），校验 access token，提取 `userId/username/roles`，写入转发 header（如 `X-User-Id`、`X-Username`、`X-User-Roles`）透传给下游；白名单路径（/api/auth/login、/api/auth/register、/api/market/public/**、/api/chain/ping）跳过鉴权。
- 限流：`RequestRateLimiter`（基于 Redis），区分公共行情接口与私有交易接口。
- 跨域 CORS、请求日志、全局异常兜底。
- **WebSocket 路由**：`/ws/**` 透传给 `exchange-market`（行情推送）与 `exchange-notify`（站内信实时推送），需升级为支持 WebSocket 的 WebFlux 路由。
- 依赖：`exchange-common`（上下文/常量）+ webflux + spring-cloud-starter-gateway + nacos discovery。

#### 3.2.3 `exchange-user`（8101）—— 用户与账号，修复增强
- 现有用户 CRUD、RBAC（角色/权限/部门/岗位）保持，修复已知问题：
  - 修正 `type-aliases-package` → `com.web3.exchange.user.entity`。
  - **消除敏感信息泄露**：`userToDetailDTO` 不返回 `password`/`secretKey`，改为只返回授权/认证所需字段；统一 `Result<T>` 包装。
  - 补全 `PageHelper` 依赖或改用 MyBatis-Plus 自带分页。
- 职责扩展：KYC 信息管理（kyc_status/kyc_level 更新、证件审核回调）、邀请返佣关系维护（invite_code/invited_by）、用户等级。
- 对外（Feign/内部）：`/internal/user/info/{id}`、`/internal/user/wallet` 等内部接口，供 auth/asset/notify 调用（内部接口与对外 REST 分离，`/internal/**` 仅服务间调用）。
- 依赖：common、MySQL、Redis、SpringDoc。

#### 3.2.4 `exchange-auth`（8102）—— 认证授权，先修复编译
- 修复编译问题：创建缺失的 `CaptchaService`、`auth.service.UserService`（或删除引用改走 Feign）、修正 `UserPrincipal` 包路径、补 `SecurityUtils`/`RefreshTokenRequest`。
- 修正 `application.yml`：中文句号→`.`、`cig.import`→`config.import`、springdoc 缩进。
- 保留现有能力：登录/登出/双令牌刷新/验证码/登录失败锁定/黑名单/单次使用 refresh token。
- 演进：支持多因子（2FA google/sms/email，t_user 已有字段）、验证码走 Redis、登录日志异步写入 `t_login_log`。
- 对外：`/api/auth/**`；内部不做太多，主要给网关提供 JWT 签发。
- 依赖：common、Redis、MySQL（角色缓存可走 user 的 Feign）。

#### 3.2.5 `exchange-asset`（8103）—— 资产与钱包核心（重点）
职责：用户资金账户、余额冻结/解冻、充值入账、提现申请/审核/打款、资产流水、冷热钱包地址管理。
- 资金模型（详见附录表设计）：
  - **`t_wallet_account`（钱包账户）**：`(user_id, coin)` 唯一，持有 `available`/`frozen`/`total` 三余额，乐观锁 + 行锁保障原子性。**所有余额变动必须走 `t_asset_ledger`（资产流水）并加锁**，杜绝直接改余额。
  - **`t_asset_ledger`（资产流水）**：不可变 append-only，记录每笔变动方向（IN/OUT/FREEZE/UNFREEZE）、关联业务号（orderId/withdrawId/depositId）、前后余额。**账务一致性审计的基础。**
  - **`t_deposit`（充值订单）**：链上监听匹配后入账，状态机 PENDING→CONFIRMED→SUCCESS/FAILED。
  - **`t_withdraw`（提现订单）**：状态机 PENDING→AUDITING→PROCESSING→SUCCESS/REJECTED；冻结余额，经 chain 上链打款，成功确认后扣减，失败解冻回滚。
  - **`t_asset_address`（充币地址）**：用户专属充值地址（冷热钱包地址簿）。
  - **`t_coin` / `t_chain`（币种/链配置）**：支持的币种、链、合约地址、确认数、精度。
- 接口：
  - 对外：`/api/asset/balance`（查询）、`/api/asset/deposit/**`、`/api/asset/withdraw/**`、`/api/asset/address/**`。
  - 内部（Feign）：`/internal/asset/freeze`、`/internal/asset/unfreeze`、`/internal/asset/transfer`（给 order 成交结算调用）、`/internal/asset/credit`（给 chain 充值入账调用）。
- 依赖：common、MySQL、Redis（余额缓存/分布式锁）、RocketMQ（事务消息做充提入账，保证资金一致性）、Feign 调 user（地址/kyc 校验）。

#### 3.2.6 `exchange-order`（8104）—— 订单与撮合引擎（重点）
职责：交易对管理、订单接收/校验、**内存撮合引擎**、订单状态机、成交回报广播。
- **撮合引擎设计（自研内存撮合）**：
  - 订单按交易对（symbol）分 key，每个 key 一个独立订单簿（价格层用 `ConcurrentSkipListMap`，同价订单用队列），同一 key 的撮合在同一线程串行（单一写锁或 `Disruptor` 单消费者），**保证同交易对撮合强一致**；不同交易对天然并行。
  - 订单类型：限价单（GTC/IOC/FOK/限价止盈止损）、市价单；先做 GTC 限价 + 市价，后续扩展。
  - 撮合后产出：成交记录 + 更新订单簿 + 生成资金变更指令（发给 asset）。
  - **可靠性**：订单落库先行（DB 状态 `PENDING`）+ 内存撮合 + 撮合结果异步写入 `t_trade`（成交表）与订单状态；定期订单簿快照 + 成交 WAL，重启时从快照+WAL 恢复未成交挂单。
- 表设计（详见附录）：
  - `t_symbol`（交易对）、`t_order`（订单）、`t_trade`（成交记录）、`t_order_book_snapshot`（订单簿快照）、`t_order_trade`（订单-成交关联，撮合 T+0 对冲用）。
- 对外：
  - 对外：`/api/order/create`、`/api/order/cancel`、`/api/order/list`、`/api/order/detail`。
  - 内部：`/internal/order/...`（给 market 拉历史成交、给 asset 通知结算）。
- **资金协作**：下单 → asset 冻结；成交 → asset 过户（买方付 quote 收 base，卖方相反）；撤单 → asset 解冻。用 RocketMQ 事务消息保证"订单状态 + 资金变动"一致。
- 依赖：common、MySQL、Redis（订单/成交缓存、撮合状态）、RocketMQ（成交事件广播）、Feign 调 asset（冻结/过户/解冻）。

#### 3.2.7 `exchange-chain`（8105）—— 链上交互（重点，启用 web3j 4.10.3）
职责：多链抽象、充值监听、提现上链、冷热钱包、Gas 管理、智能合约交互。
- 组件：
  - **`ChainProvider` 抽象**：按链（ETH/BSC/TRON/Polygon…）封装 web3j 客户端，统一接口；`ChainRegistry` 管理多链。
  - **`BlockScanner`（充值监听）**：轮询最新区块，扫描充值交易/事件日志（ERC20 Transfer、原生币），确认数达阈值后回调 asset 入账（`/internal/asset/credit`）。用 RocketMQ 异步解耦，避免阻塞扫描。
  - **`WithdrawService`（提现上链）**：从 asset 拿到已审核提现，构建并签名交易，广播上链，监听确认；失败/回滚通知 asset。
  - **`WalletService`（冷热钱包）**：热钱包在线签名小额/频繁提现，冷钱包离线签名大额（签名后冷端广播）；助记词/私钥加密存储（KMS/HSM 或加密文件），**严禁明文入库**。
  - **`GasService`**：动态 gas 估算与费率管理。
- 表设计（详见附录）：`t_chain`、`t_block_height`（各链已扫描高度）、`t_withdraw`（与 asset 共享或由 asset 主导，chain 只负责上链状态，建议提现单归属 asset，chain 记录链上哈希/回执）、`t_deposit`（链上原始交易记录 → 入账）。
- 对外：`/api/chain/deposit/address`（生成/查询充值地址）、`/api/chain/withdraw/hash`（查询上链状态）、`/api/chain/gas`（预估 gas）、`/api/chain/price`（参考价，供 market）。
- 内部：`/internal/chain/withdraw/send`、`/internal/chain/withdraw/confirm`、充值入账回调。
- 依赖：common、web3j 4.10.3（**激活**）、MySQL、Redis、RocketMQ（充值/提现事件）、Feign 调 asset。

#### 3.2.8 `exchange-market`（8106）—— 行情服务（新增）
职责：K线、深度快照、ticker、成交推送。
- 数据来源：消费 `exchange-order` 广播的**成交事件**（RocketMQ）聚合生成 K 线；消费订单簿变动生成深度快照；ticker 聚合最新价/24h 涨跌/成交量。
- 缓存：K线/深度/ticker 存 Redis（`market:kline:{symbol}:{interval}`、`market:depth:{symbol}`、`market:ticker:{symbol}`），读多写少用内存 + Redis 双写。
- 推送：WebSocket（STOMP/原生 WS），订阅行情主题，Redis pub/sub 广播给网关/直连客户端；公共行情接口免鉴权走网关白名单。
- 表（可选落库历史 K 线）：`t_kline`（K线表，可按 symbol+interval 分区）。
- 对外：`/api/market/ticker/{symbol}`、`/api/market/depth/{symbol}`、`/api/market/kline/{symbol}`、`/api/market/trades/{symbol}`（公共）、`/ws/**`（WebSocket）。
- 依赖：common、Redis、RocketMQ（消费成交事件）、可选 MySQL（K线归档）。

#### 3.2.9 `exchange-notify`（8107）—— 通知服务
职责：站内信、邮件、短信（SMS）、WebSocket 实时推送、风控/运营事件通知。
- 表：`t_notice`（站内信）、`t_notice_user`（用户-站内信关联）、`t_message_template`（消息模板）、`t_push_record`（推送记录）。
- 消费 RocketMQ 通知事件（成交、充值到账、提现结果、风控提醒），按渠道模板渲染并推送；WebSocket 直连 `exchange-market`/`notify` 通道。
- 对外：`/api/notify/message/list`、`/api/notify/message/read`、`/api/notify/subscribe`。
- 依赖：common、MySQL、Redis、RocketMQ、邮件/SMS 三方 SDK。

#### 3.2.10 `exchange-monitor` —— 监控与风控
- 定位调整为**运维可观测 + 规则引擎**：集成 `spring-boot-starter-actuator` + Prometheus（Micrometer）指标暴露、健康检查聚合、`t_operation_log`/`t_login_log` 审计查询。
- 风控规则引擎：资金异动（大额提现、高频下单、频繁撤单）、IP/设备风控、KYC 合规校验，规则配置化（后续可引入 Drools/Aviator）。
- 依赖：common、Actuator、Prometheus；可选 MySQL（监控指标归档）。
- 说明：轻量起步，可与 common 的 `ExceptionMonitor` 配合；完整 APM 可后续引入 SkyWalking（不改架构，仅增加 agent）。

### 3.3 端口规划总表

| 服务 | 端口 | Nacos 服务名 | 依赖 |
|------|------|--------------|------|
| exchange-gateway | 8080 | exchange-gateway | common, nacos, redis |
| exchange-user | 8101 | exchange-user | common, mysql, redis |
| exchange-auth | 8102 | exchange-auth | common, redis, feign→user |
| exchange-asset | 8103 | exchange-asset | common, mysql, redis, rocketmq, feign→user |
| exchange-order | 8104 | exchange-order | common, mysql, redis, rocketmq, feign→asset |
| exchange-chain | 8105 | exchange-chain | common, mysql, redis, rocketmq, web3j, feign→asset |
| exchange-market | 8106 | exchange-market | common, redis, rocketmq |
| exchange-notify | 8107 | exchange-notify | common, mysql, redis, rocketmq |
| exchange-monitor | 8108 | exchange-monitor | common, actuator, prometheus |

> 注意：现有多个空骨架 yml 都写成 `server.port=8101`、`spring.application.name=exchange-common`，**必须逐模块修正**为上表端口与服务名。

### 3.4 模块间调用关系（Feign / REST / 消息）

**同步调用（Feign，内部 `/internal/**`）**
```
auth ──Feign──▶ user        （用户鉴权信息）
asset ──Feign──▶ user       （充值地址所属用户、KYC 校验）
order ──Feign──▶ asset      （冻结 / 过户 / 解冻：/internal/asset/freeze|transfer|unfreeze）
chain ──Feign──▶ asset      （充值入账 /internal/asset/credit；提现状态回写）
notify ──Feign──▶ user      （通知目标用户信息）
market ──(无同步依赖，纯消费消息)──
```

**异步消息（RocketMQ，主题规划）**
```
order  ──▶ topic:TRADE        （成交事件）        ──▶ market（聚合K线/深度）、notify（成交通知）
order  ──▶ topic:ORDER        （订单状态事件）     ──▶ notify、asset（对账）
asset  ──▶ topic:DEPOSIT      （充值事件）         ──▶ notify（到账通知）
asset  ──▶ topic:WITHDRAW     （提现事件）         ──▶ chain（上链）、notify（结果通知）
chain  ──▶ topic:CHAIN_CONFIRM（链上确认/充值入账指令）──▶ asset（入账）
```

**关键链路（成交结算，事务消息保证一致性）**
```
下单 POST /api/order/create
  → order 落库(t_order=PENDING) → 调 asset 冻结余额（Feign，事务消息） → 成功→状态=NEW 进撮合
撮合成交
  → 生成 t_trade → order 状态=CANCELED/FILLED/PARTIALLY_FILLED
  → 发 TRADE 事件 → asset 消费后执行过户（freeze→available 划转），事务消息保证"成交+过户"原子
撤单
  → order 状态=CANCELED → 发消息 → asset 解冻冻结余额
```

### 3.5 数据库拆分策略

- **不物理分库起步**：所有业务表仍放库 `web3_exchange`（表前缀 `t_`），但**按模块划分表空间/命名段**，逻辑清晰，后期再按域拆库。
- 数据域规划：
  - 用户域（已有）：`t_user/t_role/t_permission/t_user_role/t_role_permission/t_dept/t_post/t_user_dept_post/t_login_log/t_operation_log`。
  - 资产域（新增）：`t_wallet_account/t_asset_ledger/t_deposit/t_withdraw/t_asset_address/t_coin/t_chain`。
  - 交易域（新增）：`t_symbol/t_order/t_trade/t_order_book_snapshot/t_order_trade`。
  - 行情域（新增）：`t_kline`。
  - 通知域（新增）：`t_notice/t_notice_user/t_message_template/t_push_record`。
  - 链上域（新增）：`t_block_height/t_chain_deposit`。
- 每条表继承 `BaseEntity` 的系统字段（id/create_by/create_time/update_by/update_time/is_deleted/version/tenant_id），`@TableLogic` 逻辑删除 + `@Version` 乐观锁 + 雪花 ID。

---

## 四、技术风险

### 4.1 高优先级（影响上线/资金安全）

| 风险 | 说明 | 缓解 |
|------|------|------|
| **资金一致性与幂等** | 余额变动若并发、重试会造成资产错账 | 所有余额变动走 `t_asset_ledger` + 行锁 + 乐观锁；外部调用（Feign/MQ）必须幂等（业务号唯一索引 + 幂等表）；RocketMQ 事务消息覆盖充值/提现/成交结算 |
| **撮合引擎数据丢失** | 内存撮合宕机丢未落盘挂单/成交 | 订单先落库；订单簿周期快照 + 成交 WAL；重启从快照+WAL 恢复；交易对分 key + 单线程串行保证恢复顺序正确 |
| **提现安全** | 大额提现/私钥泄露/双重支付 | 冷热钱包分离、冷签名热广播；提现多级审核 + 风控规则；私钥加密存储/HSM，禁止明文；提现非幂等校验（重放攻击）；到账需链上确认数 |
| **auth 编译阻塞** | 当前 auth 无法编译，阻塞整个构建与联调 | **第一步即修复** auth 编译 + 配置错误，作为 Phase 1 里程碑 |

### 4.2 中优先级

| 风险 | 说明 | 缓解 |
|------|------|------|
| **跨服务调用链过长** | order→asset→user 等 Feign 链路增加延迟与故障点 | 内部接口统一 `/internal/**`；Feign 超时/重试/降级策略（Resilience4j）；核心链路异步化 |
| **分布式事务** | 订单状态与资金变动跨服务 | 尽量用**事务消息 + 最终一致性**，避免强分布式事务（XA/Seata 成本高）；必要处引入 Seata AT |
| **行情与撮合延迟** | 高 QPS 下行情推送滞后 | 撮合单线程串行保证顺序；行情内存聚合 + Redis pub/sub；WebSocket 推送限流与合并 |
| **多链维护成本** | 每条链 web3j 适配、Gas 波动 | `ChainProvider` 抽象 + 配置驱动；Gas 动态估算；上线初期聚焦 1-2 条链（如 ETH/BSC） |

### 4.3 低优先级 / 演进

| 风险 | 说明 | 缓解 |
|------|------|------|
| **安全审计** | RBAC/敏感字段泄露（已存在） | 修复 user DTO 泄露；统一 `Result`；网关鉴权 + 权限点校验 |
| **KYC/合规** | 实名认证、反洗钱 | 数据入库加密/脱敏；预留证件审核流；监控 KYC 状态字段 |
| **配置混乱** | 空骨架 yml 端口/服务名复制错误 | Phase 1 统一修正所有 yml，纳入父 POM 校验 |
| **可观测性** | 多服务排障困难 | Actuator + Prometheus + 统一 trace（先 Micrometer，后 SkyWalking） |

---

## 五、后续实施步骤

### Phase 0：基座与修复（1 周）—— 让工程可编译可运行
- [ ] 修复 `exchange-auth` 编译错误与 `application.yml` 配置错误（中文句号、`config.import`、springdoc 缩进）。
- [ ] 修复 `exchange-user`：`type-aliases-package`、DTO 敏感字段泄露、统一 `Result<T>`。
- [ ] 修正**所有**空骨架模块 yml 的端口与服务名（见 3.3 端口表），删除重复的 `exchange-common` 复制配置。
- [ ] 补齐 `exchange-gateway`：实现 `AuthFilter`、修正路由 `lb://exchange-auth` 等、加白名单、限流、CORS。
- [ ] `exchange-common` 增加：用户上下文 `UserContext`、通用事件 DTO、内部接口规范约定。
- **验收**：`mvn clean package` 通过；网关→auth→user 登录链路端到端跑通。

### Phase 1：资产域（2-3 周）—— 资金账户核心
> 落地依据：`docs/asset-domain.md`（7 表完整 DDL + 内部 Feign 契约 + 幂等设计）+ `sql/asset.sql`（独立建表脚本）。
- [ ] 新增资产域表（`t_wallet_account/t_asset_ledger/t_deposit/t_withdraw/t_asset_address/t_coin/t_chain`，见附录与 `docs/asset-domain.md`）。
- [ ] `exchange-asset`：钱包账户 CRUD、余额查询、冻结/解冻/过户（内部 Feign 接口）。
- [ ] 资产流水机制：所有变动写 `t_asset_ledger`，幂等 + 行锁。
- [ ] 引入 RocketMQ（本地单机起步），建立主题规范与事务消息骨架。
- **验收**：通过内部 Feign 完成"冻结→过户→解冻"资金闭环，账实一致可对账。

### Phase 2：撮合域（3-4 周）—— 交易核心
- [ ] 新增 `t_symbol/t_order/t_trade` 表；`exchange-order` 订单落库与状态机。
- [ ] 自研内存撮合引擎（限价 GTC + 市价），订单簿 + 交易对分 key 串行化。
- [ ] order→asset 冻结/过户/解冻集成（Feign + 事务消息）。
- [ ] 撮合成交事件 → RocketMQ `TRADE` 主题。
- **验收**：下单→撮合→成交→资金过户全链路端到端；撮合正确性单测（价格优先/时间优先）。

### Phase 3：链上域 + 行情域（3-4 周）
- [ ] 激活 web3j 4.10.3：`exchange-chain` `ChainProvider` 抽象、ETH/BSC 适配。
- [ ] `BlockScanner` 充值监听 → 确认 → 入账 asset；提现上链与确认。
- [ ] 冷热钱包、Gas 管理、私钥安全存储。
- [ ] 新增 `exchange-market`：消费 TRADE 事件聚合 K线/深度/ticker，Redis 缓存。
- [ ] 网关 WebSocket 路由 + market 行情推送（公共接口白名单）。
- **验收**：测试网充值/提现闭环；行情实时推送（K线/深度/ticker）。

### Phase 4：通知 + 监控风控（2-3 周）
- [ ] `exchange-notify`：站内信、邮件/短信、WebSocket 实时推送、模板。
- [ ] `exchange-monitor`：Actuator + Prometheus 指标、健康聚合、规则引擎骨架。
- [ ] 全链路 trace（可选 SkyWalking）、登录/操作审计查询。
- **验收**：成交/充值/提现事件全渠道通知；监控面板可观测各服务健康与指标。

### Phase 5：加固与演进（持续）
- [ ] 撮合引擎性能优化（Disruptor、订单簿快照恢复）。
- [ ] 提现风控规则、防重放、限流加固。
- [ ] 多链扩展、合约代币支持、KYC 审核流。
- [ ] 数据库按域物理分库、高可用（Redis 集群、MySQL 主从）。

---

## 附录：关键数据库表设计

> 所有表继承 `BaseEntity` 系统字段（id/create_by/create_time/update_by/update_time/is_deleted/version/tenant_id），逻辑删除 + 乐观锁 + 雪花 ID，此处仅列业务字段。

### A1. 资产域

> 落地细化（完整 DDL + 内部 Feign 契约 + 幂等/并发设计）见 `docs/asset-domain.md`。**金额精度约定：本域统一采用 `BIGINT`（币种最小单位，由 `t_coin.decimals` 定义），替代下文附录草稿中的 `decimal(38,18)`**，以消除浮点尾差、保证对账精确；取舍分析见 `docs/asset-domain.md` §2。

**t_wallet_account（钱包账户）**
| 字段 | 类型 | 说明 |
|------|------|------|
| user_id | bigint | 用户ID（联合唯一 with coin） |
| coin | varchar(32) | 币种，如 BTC/ETH/USDT |
| available | decimal(38,18) | 可用余额 |
| frozen | decimal(38,18) | 冻结余额 |
| total | decimal(38,18) | 总余额 = available + frozen |
| version | int | 乐观锁（继承），余额更新必带 |
| 索引 | — | UNIQUE(user_id, coin)；KEY(user_id) |

**t_asset_ledger（资产流水，append-only 不可变）**
| 字段 | 类型 | 说明 |
|------|------|------|
| user_id | bigint | 用户ID |
| coin | varchar(32) | 币种 |
| biz_type | varchar(32) | 业务类型：DEPOSIT/WITHDRAW/TRADE_BUY/TRADE_SELL/FREEZE/UNFREEZE/FEE/REBATE |
| direction | varchar(8) | IN/OUT/INOUT（冻结类） |
| amount | decimal(38,18) | 变动金额 |
| biz_no | varchar(64) | 业务单号（orderId/withdrawId/depositId） |
| balance_before / balance_after | decimal(38,18) | 变动前后可用余额（对账） |
| remark | varchar(255) | 备注 |
| 索引 | — | UNIQUE(user_id, biz_type, biz_no)（幂等）；KEY(user_id, create_time) |

**t_deposit（充值订单）**
| 字段 | 类型 | 说明 |
|------|------|------|
| user_id | bigint | 用户 |
| chain | varchar(32) | 链 |
| coin | varchar(32) | 币种 |
| from_address | varchar(255) | 来源地址 |
| to_address | varchar(255) | 充值地址 |
| amount | decimal(38,18) | 金额 |
| tx_hash | varchar(255) | 链上交易哈希（唯一） |
| confirmations | int | 已确认数 |
| status | tinyint | 0=监听中 1=待确认 2=已入账 3=失败 |
| 索引 | — | UNIQUE(tx_hash)；KEY(user_id, status) |

**t_withdraw（提现订单）**
| 字段 | 类型 | 说明 |
|------|------|------|
| user_id | bigint | 用户 |
| chain / coin | varchar(32) | 链/币种 |
| to_address | varchar(255) | 提现目标地址 |
| amount / fee | decimal(38,18) | 金额/手续费 |
| status | tinyint | 0=待审核 1=审核中 2=处理中 3=成功 4=拒绝 5=失败回滚 |
| audit_by / audit_time | — | 审核人/时间 |
| tx_hash | varchar(255) | 上链哈希 |
| 索引 | — | KEY(user_id, status)；KEY(status, create_time)（风控扫描） |

**t_asset_address（充币地址）**
| 字段 | 类型 | 说明 |
|------|------|------|
| user_id | bigint | 用户 |
| chain | varchar(32) | 链 |
| address | varchar(255) | 充值地址（唯一） |
| is_hot | tinyint | 是否热钱包地址 |
| memo / tag | varchar(64) | 备注（如 TRON 标签） |
| 索引 | — | UNIQUE(chain, address)；KEY(user_id) |

### A2. 交易域

**t_symbol（交易对）**
| 字段 | 类型 | 说明 |
|------|------|------|
| symbol | varchar(32) | 交易对，如 BTC/USDT（唯一） |
| base_coin / quote_coin | varchar(32) | 基础/计价币 |
| price_precision / amount_precision | int | 价格/数量精度 |
| min_amount / min_notional | decimal | 最小下单量/名义值 |
| status | tinyint | 0=停牌 1=交易中 |

**t_order（订单）**
| 字段 | 类型 | 说明 |
|------|------|------|
| user_id | bigint | 用户 |
| symbol | varchar(32) | 交易对 |
| side | tinyint | 1=买入 2=卖出 |
| order_type | tinyint | 1=限价 2=市价 |
| price | decimal(38,18) | 限价（市价为0） |
| amount | decimal(38,18) | 下单数量 |
| filled_amount | decimal(38,18) | 已成交数量 |
| filled_price_avg | decimal(38,18) | 平均成交价 |
| fee | decimal(38,18) | 手续费 |
| status | tinyint | 0=待撮合 1=部分成交 2=全部成交 3=已撤单 4=已拒绝 |
| client_oid | varchar(64) | 客户端订单号（幂等） |
| 索引 | — | UNIQUE(client_oid)；KEY(user_id, create_time)；KEY(symbol, status) |

**t_trade（成交记录）**
| 字段 | 类型 | 说明 |
|------|------|------|
| symbol | varchar(32) | 交易对 |
| taker_order_id / maker_order_id | bigint | 吃单/挂单订单ID |
| taker_user_id / maker_user_id | bigint | 买卖方用户 |
| price / amount | decimal(38,18) | 成交价/数量 |
| taker_fee / maker_fee | decimal(38,18) | 双方手续费 |
| trade_time | datetime | 成交时间 |
| 索引 | — | KEY(symbol, trade_time)；KEY(order_id) |

**t_order_book_snapshot（订单簿快照，撮合恢复用）**
| 字段 | 类型 | 说明 |
|------|------|------|
| symbol | varchar(32) | 交易对 |
| snapshot | json/text | 订单簿序列化快照 |
| seq | bigint | 序列号（恢复顺序） |
| 索引 | — | UNIQUE(symbol, seq) |

### A3. 行情域

**t_kline（K线）**
| 字段 | 类型 | 说明 |
|------|------|------|
| symbol | varchar(32) | 交易对 |
| interval | varchar(8) | 周期：1m/5m/1h/1d… |
| open / high / low / close | decimal(38,18) | 开高低收 |
| volume / turnover | decimal(38,18) | 成交量/成交额 |
| open_time | bigint | K线起始毫秒时间戳（唯一 with symbol,interval） |
| 索引 | — | UNIQUE(symbol, interval, open_time) |

### A4. 通知域

**t_notice（站内信）**：id, title, content, type(INFO/TEADE/DEPOSIT/WITHDRAW/RISK), send_type(ALL/SINGLE), create_by
**t_notice_user（用户站内信）**：id, notice_id, user_id, is_read, read_time；UNIQUE(notice_id, user_id)
**t_message_template（消息模板）**：id, code, channel(EMAIL/SMS/INAPP), title_template, content_template, status
**t_push_record（推送记录）**：id, user_id, channel, template_code, params(json), status(0=待发 1=成功 2=失败), retry_count

### A5. 链上域

**t_block_height（区块高度）**
| 字段 | 类型 | 说明 |
|------|------|------|
| chain | varchar(32) | 链 |
| height | bigint | 已扫描最新高度 |
| update_time | datetime | 更新时间 |
| 索引 | — | UNIQUE(chain) |

---

*本文档为架构设计基线，落地细节（具体 SQL、接口契约、撮合算法伪码）由各阶段开发补充，并建议同步维护到 `docs/` 目录，保持与 `PROJECT_MEMORY.md` 一致。*
