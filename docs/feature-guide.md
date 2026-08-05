# Web3-Exchange 功能模块开发指南（Feature Guide）

> 本指南用于**快速看懂**本项目已开发的功能模块：每个模块**是做什么的**、**为什么需要它**、**代码是怎么实现的**。
> 面向读者：项目维护者、后续开发者。
> 更新日期：2026-08-05 · 配套文档：`docs/ARCHITECTURE.md`（架构蓝图）、`docs/PROJECT_MEMORY.md`（项目状态）、`docs/asset-domain.md`（资产域落地设计）。

---

## 目录

1. [项目是什么](#一项目是什么)
2. [模块地图](#二模块地图)
3. [资产域 exchange-asset（本轮核心新增）](#三资产域-exchange-asset本轮核心新增)
4. [增强域：用户/认证/网关增强](#四增强域用户认证网关增强)
5. [错误响应统一](#五错误响应统一)
6. [核心流程速览](#六核心流程速览)

---

## 一、项目是什么

**Web3 数字资产交易所后端**，Spring Cloud Alibaba 微服务架构（Java 17 / Spring Boot 3.2 / Nacos 注册配置中心 / MyBatis-Plus / MySQL / Redis）。

业务上要做一个"能安全托管用户资产并撮合交易"的平台。资金安全是生命线——所以资产域用了一整套**流水 + 行锁 + 幂等**的设计来保证"钱不会多、不会少、不会重复扣"。

---

## 二、模块地图

| 模块 | 端口 | 做什么 | 已开发 |
|------|------|--------|--------|
| `exchange-common` | — | 公共库：`Result` 统一响应、异常体系、`GlobalExceptionHandler`、资产 DTO、`UserDetailDTO` | ✅ |
| `exchange-gateway` | 8080 | API 网关：路由转发 + JWT 鉴权 + 限流 + CORS + 请求日志 | ✅ |
| `exchange-user` | 8101 | 用户服务：注册/资料/KYC/2FA/邀请码/等级 | ✅ |
| `exchange-auth` | 8102 | 认证服务：登录/验证码/注册/2FA校验/改密重置 | ✅ |
| `exchange-asset` | 8103 | **资产服务：钱包账户/余额/冻结/解冻/过户/充值入账/流水** | ✅ 本轮 |
| `exchange-order` | 8104 | 订单/撮合 | 🅿️ 空骨架 |
| `exchange-chain` | 8105 | 链上/web3j | 🅿️ 空骨架 |
| `exchange-notify` | 8106 | 通知 | 🅿️ 空骨架 |
| `exchange-monitor` | 8107 | 监控 | 🅿️ 空骨架 |

**模块间调用**：服务间用 Feign（如 auth→user 取用户、order/chain→asset 做资金操作）。内部接口统一走 `/internal/**` 前缀，**网关不路由**，只允许服务间调用，不对外暴露。

---

## 三、资产域 exchange-asset（本轮核心新增）

### 3.1 这个功能是做什么的？

资产域管的是**每个用户在每个币种下有多少钱**。核心能力：

- **钱包账户**：每个用户自动拥有 BTC/ETH/USDT 等多个账户（可用余额、冻结余额、总余额）。
- **充值入账**：链上到账后给用户加钱。
- **冻结 / 解冻**：下单时把可用余额冻结（防止一边花一边用），撤销时解冻。
- **过户**：撮合成交时，把 A 的冻结余额转给 B（A 扣、B 加）。
- **提现**：出金（本模块已建表，业务流后续完善）。
- **资产流水**：每一笔资金变动都写一条不可变的流水，用于**对账**和**审计**。

### 3.2 为什么需要它？

这是**资金托管**的核心，有三件事必须保证，否则平台会出事：

1. **钱不能算错**：用户充值 100 BTC，就得有 100 BTC，一分不能多不能少。→ 用"流水 + 余额不变式"保证。
2. **钱不能重复扣/重复加**：网络超时、Feign 重试、消息重复投递，都可能让同一笔操作执行两次。→ 用"幂等键"保证。
3. **同一账户并发操作不能乱**：用户同时下单冻结、又充值入账，如果并发没控制好，余额会错。→ 用"行锁 + 乐观锁"保证。

### 3.3 核心设计：三大保障

#### (1) 余额不变式（账实一致）

每个账户：**`可用余额 available + 冻结余额 frozen == 总余额 total`**，永远成立。

所有资金变动都只改 `available` 和 `frozen`，`total` 由它俩算出，从不单独设置。这样对账时只要校验 `Σ流水 == 余额变化` 即可发现任何错账。

#### (2) 资金方向 Direction（5 种）

`LedgerService.doChange` 按方向计算变动前后余额：

| 方向 | 含义 | 对余额的影响 |
|------|------|--------------|
| `IN` | 流入（充值/过户入） | 可用增加 |
| `OUT` | 流出 | 可用减少 |
| `FROZEN` | 冻结 | 可用减、冻结加 |
| `UNFROZEN` | 解冻 | 冻结减、可用加 |
| `FROZEN_OUT` | 过户转出 | 仅冻结减少 |

#### (3) 幂等 + 并发控制（资金安全的核心）

`LedgerService.doChange`（`LedgerServiceImpl.java`）是资金变动的**唯一入口**，流程：

```
1. 幂等回读：同 requestId 已有流水 → 直接返回首次结果（不重复执行）
2. 按方向算 before/after 余额，余额不足抛 409 业务错误
3. 写流水 t_asset_ledger（append-only，唯一索引 request_id 兜底幂等）
4. 更新账户余额（已行锁串行化 + version 乐观锁兜底）
   - 行锁：SELECT ... FOR UPDATE 锁住该账户行，同账户操作串行
   - 乐观锁：UPDATE ... WHERE version=?，version+1，冲突则回滚
```

**幂等键**：调用方生成 `requestId`（如订单号派生），asset 靠 `t_asset_ledger.request_id` 唯一索引拦截重复请求——重复请求直接返回首次结果，**不重复扣减**。充值另有 `t_deposit.tx_hash` 唯一索引防同一笔链上交易重复入账。

**转账死锁防护**：`transfer` 先按 userId 升序加锁两个账户，避免并发互转时死锁。

### 3.4 数据表（`sql/asset.sql`，7 张）

| 表 | 作用 | 关键约束 |
|----|------|---------|
| `t_coin` | 币种（BTC/ETH/USDT…含精度 decimals） | UNIQUE(symbol) |
| `t_chain` | 区块链（BTC/ETH/TRON…RPC/确认数） | UNIQUE(chain_code) |
| `t_wallet_account` | 用户钱包账户（available/frozen/total） | UNIQUE(user_id, symbol) + version |
| `t_asset_ledger` | 资产流水（before/after 余额、requestId） | UNIQUE(request_id) |
| `t_deposit` | 充值记录 | UNIQUE(tx_hash) |
| `t_withdraw` | 提现记录 | UNIQUE(request_id) |
| `t_asset_address` | 充币地址 | UNIQUE(chain_code, address) |

> **金额精度**：全部用 **BIGINT 最小单位**（如 BTC 存 8 位小数的整数），避免浮点误差。由 `AmountUtil.toMinor/toMajor` 换算。

### 3.5 接口清单（`/internal/asset/**`，内部服务间调用）

| 方法 | 接口 | 说明 |
|------|------|------|
| 开户 | `POST /account/open?userId&symbol` | 幂等开户，自动为所有币种建账户 |
| 查余额 | `GET /account/balance?userId&symbol` | 查单个账户 |
| 列表 | `GET /account/list?userId` | 用户全部币种账户（钱包总览） |
| 冻结 | `POST /freeze` | 可用→冻结 |
| 解冻 | `POST /unfreeze` | 冻结→可用 |
| 过户 | `POST /transfer` | A冻结→B可用（单事务原子） |
| 入账 | `POST /credit` | 充值到账加可用 |
| 流水 | `GET /ledger/list?accountId&page&size` | 分页查流水（对账） |

**关键代码文件**（`exchange-asset`）：
- `service/impl/LedgerServiceImpl.java` — **资金核心**，`doChange` 统一封装资金变动
- `service/impl/AccountServiceImpl.java` — 账户开户/余额/`lockByUserAndSymbol`（行锁）
- `mapper/AccountMapper.java` — `selectByUserAndSymbolForUpdate`（FOR UPDATE 行锁）
- `controller/InternalAssetController.java` — 内部接口
- `entity/*.java` — 7 个实体（金额 Long、含 `@Version`）
- `util/AmountUtil.java` — 精度换算
- `exchange-common/.../asset/dto/*` — 跨模块复用的 DTO（`AccountVO`/`LedgerVO`/`FreezeRequest`/`UnfreezeRequest`/`TransferRequest`/`CreditRequest`）

### 3.6 资金闭环示例（已实测）

```
用户 A 充值 100 BTC → 冻结 30 → 过户 20 给 B → 解冻 10
结果：A 可用 80 / 冻结 0 / 总额 80，账实一致（80+0==80）
幂等：重复冻结返回首次结果，余额不重复扣减
余额不足：冻结超过可用 → 返回 409
```

---

## 四、增强域：用户/认证/网关增强

这是登录、注册、安全能力的基础，让系统"能用、够安全"。

### 4.1 用户服务增强（`exchange-user`，P3-A）

| 功能 | 做什么 | 为什么 |
|------|--------|--------|
| **注册** `POST /api/users/register` | 新用户注册，校验唯一性、BCrypt 编码密码、处理邀请码、生成自身邀请码、设默认等级 | 用户能自助开户；邀请裂变 |
| **资料修改** `PUT /api/users/{id}` | 改昵称/邮箱/手机/头像/真实姓名 | 完善资料 |
| **KYC 认证** `POST /api/users/{id}/kyc` | 提交实名信息（证件照），进入审核 | 合规要求，交易所必须实名 |
| **2FA 开启** `POST /api/users/{id}/2fa/enable` | 生成 Google Authenticator 密钥 | 登录二次验证，防盗号 |
| **用户等级** `GET /api/users/{id}/level` | 查询 NORMAL/VIP/SVIP 等级 | 差异化权益/费率 |

### 4.2 认证服务增强（`exchange-auth`，P3-B）

| 功能 | 做什么 | 为什么 |
|------|--------|--------|
| **图形验证码** `GET /api/auth/captcha` | 生成数学算式验证码存 Redis，登录前校验 | 防机器人爆破登录 |
| **注册接口** `POST /api/auth/register` | 验证码 → 调 user 注册 | 网关公开注册入口 |
| **2FA 登录校验** | 已开 2FA 的用户登录需输入 TOTP 动态码（RFC 6238，`TotpUtil`） | 登录二次验证 |
| **改密** `POST /api/auth/change-password` | 校验旧密码后改新密码 | 用户主动改密 |
| **重置密码** `POST /api/auth/reset-password` | 通过用户名/邮箱+验证码重置（需 `code`） | 忘记密码找回 |

### 4.3 网关增强（`exchange-gateway`，P3-C）

| 功能 | 做什么 | 为什么 |
|------|--------|--------|
| **JWT 鉴权**（AuthFilter） | 白名单放行，其他路径校验 Bearer token，失败 401 | 保护后端接口 |
| **Redis 限流**（RequestRateLimiter） | 按 IP 限流，超限返回 429 | 防恶意刷接口/攻击 |
| **CORS 跨域** | 允许前端 localhost 来源跨域访问 | 浏览器前端能调 |
| **请求日志**（RequestLogFilter） | 记录每个请求 method/path/status/耗时 | 排查问题/审计 |
| **WebSocket 路由** `/ws/**` | 预留行情/通知推送路由（market 上线后启用） | 实时行情推送 |

---

## 五、错误响应统一

**是什么**：让所有服务抛出的业务/认证/校验异常都返回统一的 `Result` 结构（`{code, message, data, ...}`），而不是 Spring 默认的原始错误体。

**为什么**：前端/调用方只要解析一种响应结构，错误码统一（如 401 未认证、409 余额不足、422 参数错误），联调体验一致。

**怎么实现**：`exchange-common/handler/GlobalExceptionHandler.java`（`@RestControllerAdvice`）集中处理各类异常。修复的关键点：服务主类（`AuthApplication`/`UserApplication`/`AssetApplication`）加 `@Import(GlobalExceptionHandler.class)` 引入这个 advice（否则裸 `@SpringBootApplication` 只扫自己包，扫不到 common 的 handler）。

**实测**：错误密码→`code:401 登录失败`、缺参→`code:422 含 errors`、余额不足→`code:409`。

---

## 六、核心流程速览

```
浏览器 / 客户端
   │
   ▼
[exchange-gateway 8080]  JWT鉴权 → 限流 → CORS → 日志
   │ 路由转发
   ├── /api/auth/**  ──▶ [exchange-auth 8102]  登录/验证码/注册/2FA/改密
   │                        │ Feign 取用户
   │                        ▼
   │                   [exchange-user 8101]  用户/注册/KYC/2FA/等级
   └── /api/asset/** ──▶ [exchange-asset 8103] 钱包/余额/冻结/过户/入账/流水
                            │ 内部接口 /internal/asset/**（服务间 Feign）
                            ▼
                       order/chain 后续接入（资金操作走 LedgerService 幂等）
```

**一次撮合成交的资金链路（设计目标）**：
订单成交 → order 调 asset `transfer`（买方冻结扣减、卖方可用增加）→ 写流水 → 账实一致可对账。这正是 `LedgerService` 幂等 + 行锁设计要支撑的场景。

---

## 附：常用开发命令

```bash
# 构建（必须用 temurin-17，否则 Lombok 报错）
export JAVA_HOME=/Users/yongzx/Library/Java/JavaVirtualMachines/temurin-17.0.17/Contents/Home
mvn -pl exchange-asset -am package -DskipTests

# 启动服务（后台）
java -jar exchange-asset/target/exchange-asset-1.0.0.jar &
# 端口：gateway 8080, user 8101, auth 8102, asset 8103
```
