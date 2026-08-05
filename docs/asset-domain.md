# 资产域（Phase 1）落地设计：数据库 DDL 与内部 Feign 接口契约

> 版本：v1.0 · 作者：系统架构师 · 日期：2026-08-05
> 适用：`exchange-asset`（Nacos 服务名 `exchange-asset`，端口 **8103**）落地依据。
> 定位：本文件是 `docs/ARCHITECTURE.md` 附录 A1「资产域」的**落地细化**（具体 SQL、字段、接口契约），与架构蓝图保持一致，供 `/dev` 直接照此实现。不修改任何 Java 代码，不修改 `sql/user.sql`；新增独立 SQL 见下。
> 兼容基线：Spring Boot 3.2.5 / Spring Cloud Alibaba 2023.0.1.0 / MyBatis-Plus 3.5.7 / MySQL 8 / 统一 `Result<T>` / `BaseEntity`（id=雪花 + createBy/createTime/updateBy/updateTime + isDeleted 逻辑删除 + version 乐观锁 + tenantId 租户）。

---

## 目录

1. [总体设计要点](#一总体设计要点)
2. [金额精度取舍（BIGINT vs DECIMAL）](#二金额精度取舍)
3. [7 张表完整 DDL](#三七张表完整-ddl)
4. [内部 Feign 接口契约](#四内部-feign-接口契约)
5. [幂等与并发控制设计](#五幂等与并发控制设计)
6. [落地 Checklist（/dev 实施指引）](#六落地-checklistdev-实施指引)

---

## 一、总体设计要点

- **资金不变式（铁律）**：钱包账户的三余额（`available`/`frozen`/`total`）**不得被应用层直接 UPDATE**。所有变动必须：
  1. 以 `SELECT ... FOR UPDATE` 行锁锁定账户行；
  2. 同一数据库事务内先写 `t_asset_ledger` 流水，再更新 `t_wallet_account` 余额（流水为账、账户为汇总快照）；
  3. 携带 `version` 乐观锁兜底，防止跨实例并发。
- **幂等设计**：资金操作（冻结/解冻/过户/入账）**必须携带 `requestId`**，`t_asset_ledger.request_id` 唯一索引兜底。同一 `requestId` 重复到达直接返回首次结果，杜绝重复扣减/入账。
- **账实对账**：`t_asset_ledger` 为 **append-only** 不可变流水（业务上禁止 UPDATE/DELETE），凭 `before_available/after_available/before_frozen/after_frozen` 可与账户余额对账。
- **币种/链配置驱动**：所有链参数（确认数、RPC、Gas）与币种参数（精度、开关、手续费）集中在 `t_coin`/`t_chain`，业务代码不写死。
- **接口分层**：对外 REST 走网关（`/api/asset/**`）；**服务间调用统一走 `/internal/asset/**`**，仅注册到 Feign 客户端，不对外暴露（网关不路由 `/internal/**`）。
- **表结构规范**：继承 `BaseEntity` 系统字段（`create_by/create_time/update_by/update_time/is_deleted/version/tenant_id`），主键 `bigint` 雪花，`ENGINE=InnoDB`、`utf8mb4_unicode_ci`、每字段中文注释，索引命名 `uk_*`/`idx_*`，与 `sql/user.sql` 风格完全一致。

---

## 二、金额精度取舍

> 结论：**资产域全部金额字段采用 `BIGINT`（最小单位，整数）**。对外展示/入参由 `t_coin.decimals` 换算，应用层禁止使用 `double`/`float`。

| 方案 | 说明 | 优点 | 缺点 |
|------|------|------|------|
| **BIGINT（推荐）** | 金额以「最小单位」整数存储（如 USDT 用 1e6、ETH 用 1e18 wei），精度由 `t_coin.decimals` 决定 | ① 整数运算无任何浮点误差，加法/减法/比较精确；② 数据库与 Java `long` 一一对应，无 DECIMAL 精度溢出风险；③ 对账、审计直观，业界（币安等）主流做法 | 业务侧需维护 `decimals` 换算，展示层做除法 |
| DECIMAL(30,8) | 以小数直接存储，保留 8 位小数 | 对账/展示直观，不涉及换算 | ① DECIMAL 底层仍是定点运算，多笔累加/乘法（费率、撮合均价）易出现 0.00000001 级尾差；② Java 必须用 `BigDecimal`，序列化/比较/传参成本高；③ 多币种精度不统一时字段语义混乱 |
| DECIMAL(38,18)（架构图附录原始写法） | 极端宽松的定点数 | 兼容性最强 | ① 尾差问题同上；② 38 位精度远超实际，无意义且浪费；③ 多币种精度被「拍平」，丢失 per-coin 精度信息 |

**落地约定**
- 表内金额字段全部 `bigint`，单位为**该币种最小单位**（由 `t_coin.decimals` 定义）。`amount`/`fee`/`available`/`frozen` 等均为整数。
- `decimals` 取该链标准精度（USDT=6、ETH=18、BTC=8）；**原生币与代币都取 `t_coin.decimals` 一个口径**，不混用。
- 入参/出参 DTO 使用 `BigDecimal`（业务精度）或 `long`（最小单位）二选一并全链路统一——**本契约建议 DTO 全部用 `long`（最小单位），服务间 Feign 传整数，杜绝浮点**；对外 REST 再按需换算。
- 换算由 asset 内部工具类 `AmountUtil`（`toMinor(BigDecimal, decimals)` / `toMajor(long, decimals)`）统一完成，业务层禁止裸除/裸乘。

---

## 三、7 张表完整 DDL

> 独立 SQL 文件：`sql/asset.sql`（与 `sql/user.sql` 同级、同风格）。以下为全文。

```sql
-- ============================================================
-- 资产域（Phase 1）建表脚本
-- 库：web3_exchange；表前缀 t_；引擎 InnoDB；utf8mb4
-- 与 sql/user.sql 风格一致：雪花主键 + BaseEntity 系统字段 + 中文注释
-- ============================================================

-- ------------------------------------------------------------
-- 1. 币种表 t_coin
-- ------------------------------------------------------------
CREATE TABLE `t_coin` (
                          `id` bigint NOT NULL COMMENT '币种ID',

                          `symbol` varchar(32) NOT NULL COMMENT '币种符号:BTC/ETH/USDT',
                          `name` varchar(64) DEFAULT NULL COMMENT '币种名称',
                          `coin_type` varchar(20) NOT NULL DEFAULT 'TOKEN' COMMENT '币种类型:COIN=原生币,TOKEN=代币',
                          `chain_code` varchar(32) NOT NULL COMMENT '所属链编码(关联t_chain)',
                          `contract_address` varchar(255) DEFAULT NULL COMMENT '代币合约地址(原生币为空)',
                          `decimals` int NOT NULL DEFAULT '18' COMMENT '精度(最小单位位数)',
                          `withdraw_enabled` tinyint(1) NOT NULL DEFAULT '1' COMMENT '是否允许提现:0=否,1=是',
                          `deposit_enabled` tinyint(1) NOT NULL DEFAULT '1' COMMENT '是否允许充值:0=否,1=是',
                          `withdraw_fee` bigint NOT NULL DEFAULT '0' COMMENT '提现固定手续费(最小单位)',
                          `min_withdraw` bigint DEFAULT NULL COMMENT '最小提现额(最小单位)',
                          `max_withdraw` bigint DEFAULT NULL COMMENT '单笔最大提现额(最小单位)',
                          `min_deposit` bigint DEFAULT NULL COMMENT '最小充值额(最小单位)',
                          `daily_withdraw_limit` bigint DEFAULT NULL COMMENT '当日提现限额(最小单位)',
                          `status` tinyint NOT NULL DEFAULT '1' COMMENT '状态:0=禁用,1=正常',
                          `sort` int DEFAULT '0' COMMENT '排序',

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
                          KEY `idx_chain_code` (`chain_code`),
                          KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='币种表';

-- ------------------------------------------------------------
-- 2. 链配置表 t_chain
-- ------------------------------------------------------------
CREATE TABLE `t_chain` (
                           `id` bigint NOT NULL COMMENT '链ID',

                           `chain_code` varchar(32) NOT NULL COMMENT '链编码:ETH/BSC/TRON/POLYGON',
                           `chain_name` varchar(64) DEFAULT NULL COMMENT '链名称',
                           `chain_type` varchar(20) NOT NULL DEFAULT 'EVM' COMMENT '链类型:EVM/TRON/OTHER',
                           `chain_id` bigint DEFAULT NULL COMMENT '网络链ID(EIP-155,TRON为NULL)',
                           `rpc_url` varchar(500) DEFAULT NULL COMMENT 'RPC节点地址',
                           `explorer_url` varchar(500) DEFAULT NULL COMMENT '浏览器地址',
                           `currency` varchar(32) DEFAULT NULL COMMENT '原生币种(Gas币)',
                           `confirmations` int NOT NULL DEFAULT '12' COMMENT '充值入账所需确认数',
                           `withdraw_confirmations` int NOT NULL DEFAULT '12' COMMENT '提现成功确认数',
                           `scan_enabled` tinyint(1) NOT NULL DEFAULT '0' COMMENT '是否开启区块扫描:0=否,1=是',
                           `min_gas_price` bigint DEFAULT NULL COMMENT '最小Gas单价(wei)',
                           `max_gas_price` bigint DEFAULT NULL COMMENT '最大Gas单价(wei)',
                           `status` tinyint NOT NULL DEFAULT '1' COMMENT '状态:0=禁用,1=正常',
                           `sort` int DEFAULT '0' COMMENT '排序',

                           -- 系统字段 --
                           `create_by` varchar(64) DEFAULT '' COMMENT '创建者',
                           `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                           `update_by` varchar(64) DEFAULT '' COMMENT '更新者',
                           `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
                           `is_deleted` tinyint DEFAULT '0' COMMENT '逻辑删除:0=未删,1=已删',
                           `version` int DEFAULT '0' COMMENT '乐观锁版本号',
                           `tenant_id` bigint DEFAULT '0' COMMENT '租户ID',

                           PRIMARY KEY (`id`),
                           UNIQUE KEY `uk_chain_code` (`chain_code`),
                           KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='链配置表';

-- ------------------------------------------------------------
-- 3. 钱包账户表 t_wallet_account
-- ------------------------------------------------------------
CREATE TABLE `t_wallet_account` (
                                    `id` bigint NOT NULL COMMENT '账户ID',

                                    `user_id` bigint NOT NULL COMMENT '用户ID',
                                    `coin_id` bigint NOT NULL COMMENT '币种ID(关联t_coin)',
                                    `symbol` varchar(32) NOT NULL COMMENT '币种符号(冗余,便于查询)',
                                    `available` bigint NOT NULL DEFAULT '0' COMMENT '可用余额(最小单位)',
                                    `frozen` bigint NOT NULL DEFAULT '0' COMMENT '冻结余额(最小单位)',
                                    `total` bigint NOT NULL DEFAULT '0' COMMENT '总余额=available+frozen(最小单位)',
                                    `status` tinyint NOT NULL DEFAULT '1' COMMENT '账户状态:0=禁用,1=正常,2=冻结',

                                    -- 系统字段 --
                                    `create_by` varchar(64) DEFAULT '' COMMENT '创建者',
                                    `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                                    `update_by` varchar(64) DEFAULT '' COMMENT '更新者',
                                    `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
                                    `is_deleted` tinyint DEFAULT '0' COMMENT '逻辑删除:0=未删,1=已删',
                                    `version` int DEFAULT '0' COMMENT '乐观锁版本号(余额更新必带)',
                                    `tenant_id` bigint DEFAULT '0' COMMENT '租户ID',

                                    PRIMARY KEY (`id`),
                                    UNIQUE KEY `uk_user_symbol` (`user_id`, `symbol`),
                                    KEY `idx_user_id` (`user_id`),
                                    KEY `idx_coin_id` (`coin_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='钱包账户表';

-- ------------------------------------------------------------
-- 4. 资产流水表 t_asset_ledger（append-only 不可变）
-- ------------------------------------------------------------
CREATE TABLE `t_asset_ledger` (
                                  `id` bigint NOT NULL COMMENT '流水ID',

                                  `request_id` varchar(64) NOT NULL COMMENT '幂等请求号(业务方生成,全局唯一)',
                                  `user_id` bigint NOT NULL COMMENT '用户ID',
                                  `account_id` bigint NOT NULL COMMENT '账户ID(关联t_wallet_account)',
                                  `coin_id` bigint NOT NULL COMMENT '币种ID',
                                  `symbol` varchar(32) NOT NULL COMMENT '币种符号',
                                  `biz_type` varchar(32) NOT NULL COMMENT '业务类型:FREEZE冻结/UNFREEZE解冻/TRANSFER_IN过户入/TRANSFER_OUT过户出/DEPOSIT充值入账/WITHDRAW提现/FEE手续费/REBATE返佣',
                                  `direction` tinyint NOT NULL COMMENT '资金方向:1=流入IN(可用增加) 2=流出OUT(可用减少) 3=冻结FROZEN(可用减少,冻结增加) 4=解冻UNFROZEN(冻结减少,可用增加)',
                                  `amount` bigint NOT NULL COMMENT '变动金额(最小单位,恒正)',
                                  `before_available` bigint NOT NULL COMMENT '变动前可用余额',
                                  `after_available` bigint NOT NULL COMMENT '变动后可用余额',
                                  `before_frozen` bigint NOT NULL DEFAULT '0' COMMENT '变动前冻结余额',
                                  `after_frozen` bigint NOT NULL DEFAULT '0' COMMENT '变动后冻结余额',
                                  `ref_no` varchar(64) DEFAULT NULL COMMENT '业务单号(orderId/withdrawId/depositId)',
                                  `status` tinyint NOT NULL DEFAULT '1' COMMENT '状态:0=处理中,1=成功,2=失败,3=回滚',
                                  `remark` varchar(255) DEFAULT NULL COMMENT '备注',

                                  -- 系统字段 --
                                  `create_by` varchar(64) DEFAULT '' COMMENT '创建者',
                                  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                                  `update_by` varchar(64) DEFAULT '' COMMENT '更新者',
                                  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
                                  `is_deleted` tinyint DEFAULT '0' COMMENT '逻辑删除:0=未删,1=已删(业务禁止删除,仅保留字段兼容)',
                                  `version` int DEFAULT '0' COMMENT '乐观锁版本号',
                                  `tenant_id` bigint DEFAULT '0' COMMENT '租户ID',

                                  PRIMARY KEY (`id`),
                                  UNIQUE KEY `uk_request_id` (`request_id`),
                                  UNIQUE KEY `uk_biz_no` (`user_id`, `biz_type`, `ref_no`),
                                  KEY `idx_user_time` (`user_id`, `create_time`),
                                  KEY `idx_account_id` (`account_id`),
                                  KEY `idx_ref_no` (`ref_no`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='资产流水表';

-- ------------------------------------------------------------
-- 5. 充值记录表 t_deposit
-- ------------------------------------------------------------
CREATE TABLE `t_deposit` (
                             `id` bigint NOT NULL COMMENT '充值记录ID',

                             `request_id` varchar(64) NOT NULL COMMENT '幂等请求号(入账时生成)',
                             `user_id` bigint NOT NULL COMMENT '用户ID',
                             `coin_id` bigint NOT NULL COMMENT '币种ID',
                             `symbol` varchar(32) NOT NULL COMMENT '币种符号',
                             `chain_code` varchar(32) NOT NULL COMMENT '链编码',
                             `from_address` varchar(255) DEFAULT NULL COMMENT '来源地址',
                             `to_address` varchar(255) NOT NULL COMMENT '充值目标地址',
                             `amount` bigint NOT NULL COMMENT '充值金额(最小单位)',
                             `fee` bigint NOT NULL DEFAULT '0' COMMENT '网络手续费(最小单位)',
                             `tx_hash` varchar(255) NOT NULL COMMENT '链上交易哈希',
                             `block_height` bigint DEFAULT NULL COMMENT '所在区块高度',
                             `confirmations` int NOT NULL DEFAULT '0' COMMENT '已确认数',
                             `required_confirmations` int NOT NULL DEFAULT '0' COMMENT '入账所需确认数(冗余t_chain.confirmations)',
                             `ledger_id` bigint DEFAULT NULL COMMENT '入账流水ID(关联t_asset_ledger)',
                             `status` tinyint NOT NULL DEFAULT '0' COMMENT '状态:0=监听中,1=待确认,2=已入账,3=失败',
                             `remark` varchar(255) DEFAULT NULL COMMENT '备注',

                             -- 系统字段 --
                             `create_by` varchar(64) DEFAULT '' COMMENT '创建者',
                             `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                             `update_by` varchar(64) DEFAULT '' COMMENT '更新者',
                             `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
                             `is_deleted` tinyint DEFAULT '0' COMMENT '逻辑删除:0=未删,1=已删',
                             `version` int DEFAULT '0' COMMENT '乐观锁版本号',
                             `tenant_id` bigint DEFAULT '0' COMMENT '租户ID',

                             PRIMARY KEY (`id`),
                             UNIQUE KEY `uk_tx_hash` (`tx_hash`),
                             UNIQUE KEY `uk_request_id` (`request_id`),
                             KEY `idx_user_status` (`user_id`, `status`),
                             KEY `idx_chain_status` (`chain_code`, `status`),
                             KEY `idx_to_address` (`to_address`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='充值记录表';

-- ------------------------------------------------------------
-- 6. 提现记录表 t_withdraw
-- ------------------------------------------------------------
CREATE TABLE `t_withdraw` (
                              `id` bigint NOT NULL COMMENT '提现记录ID',

                              `request_id` varchar(64) NOT NULL COMMENT '幂等请求号(申请时生成)',
                              `user_id` bigint NOT NULL COMMENT '用户ID',
                              `coin_id` bigint NOT NULL COMMENT '币种ID',
                              `symbol` varchar(32) NOT NULL COMMENT '币种符号',
                              `chain_code` varchar(32) NOT NULL COMMENT '链编码',
                              `to_address` varchar(255) NOT NULL COMMENT '提现目标地址',
                              `amount` bigint NOT NULL COMMENT '提现金额(最小单位)',
                              `fee` bigint NOT NULL DEFAULT '0' COMMENT '手续费(最小单位)',
                              `real_amount` bigint NOT NULL COMMENT '实际到账=amount-fee(最小单位)',
                              `status` tinyint NOT NULL DEFAULT '0' COMMENT '状态:0=待审核,1=审核中,2=处理中(已冻结上链),3=成功,4=拒绝,5=失败回滚',
                              `audit_by` varchar(64) DEFAULT NULL COMMENT '审核人',
                              `audit_time` datetime DEFAULT NULL COMMENT '审核时间',
                              `audit_remark` varchar(255) DEFAULT NULL COMMENT '审核备注',
                              `freeze_ledger_id` bigint DEFAULT NULL COMMENT '冻结流水ID(关联t_asset_ledger)',
                              `tx_hash` varchar(255) DEFAULT NULL COMMENT '上链哈希',
                              `fail_reason` varchar(255) DEFAULT NULL COMMENT '失败原因',

                              -- 系统字段 --
                              `create_by` varchar(64) DEFAULT '' COMMENT '创建者',
                              `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                              `update_by` varchar(64) DEFAULT '' COMMENT '更新者',
                              `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
                              `is_deleted` tinyint DEFAULT '0' COMMENT '逻辑删除:0=未删,1=已删',
                              `version` int DEFAULT '0' COMMENT '乐观锁版本号',
                              `tenant_id` bigint DEFAULT '0' COMMENT '租户ID',

                              PRIMARY KEY (`id`),
                              UNIQUE KEY `uk_request_id` (`request_id`),
                              KEY `idx_user_status` (`user_id`, `status`),
                              KEY `idx_status_time` (`status`, `create_time`),
                              KEY `idx_tx_hash` (`tx_hash`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='提现记录表';

-- ------------------------------------------------------------
-- 7. 充币地址表 t_asset_address
-- ------------------------------------------------------------
CREATE TABLE `t_asset_address` (
                                   `id` bigint NOT NULL COMMENT '地址ID',

                                   `user_id` bigint NOT NULL COMMENT '用户ID',
                                   `chain_code` varchar(32) NOT NULL COMMENT '链编码',
                                   `coin_id` bigint NOT NULL COMMENT '币种ID',
                                   `symbol` varchar(32) NOT NULL COMMENT '币种符号',
                                   `address` varchar(255) NOT NULL COMMENT '充币地址',
                                   `memo` varchar(64) DEFAULT NULL COMMENT '备注/Tag(如TRON地址标签)',
                                   `address_type` tinyint NOT NULL DEFAULT '1' COMMENT '地址类型:1=用户充币地址,2=热钱包,3=冷钱包',
                                   `is_active` tinyint(1) NOT NULL DEFAULT '1' COMMENT '是否启用:0=否,1=是',
                                   `last_used_time` datetime DEFAULT NULL COMMENT '最近使用时间',

                                   -- 系统字段 --
                                   `create_by` varchar(64) DEFAULT '' COMMENT '创建者',
                                   `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                                   `update_by` varchar(64) DEFAULT '' COMMENT '更新者',
                                   `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
                                   `is_deleted` tinyint DEFAULT '0' COMMENT '逻辑删除:0=未删,1=已删',
                                   `version` int DEFAULT '0' COMMENT '乐观锁版本号',
                                   `tenant_id` bigint DEFAULT '0' COMMENT '租户ID',

                                   PRIMARY KEY (`id`),
                                   UNIQUE KEY `uk_chain_address` (`chain_code`, `address`),
                                   KEY `idx_user_id` (`user_id`),
                                   KEY `idx_symbol` (`symbol`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='充币地址表';
```

**索引与约束要点**
| 表 | 关键约束 | 用途 |
|----|---------|------|
| t_coin | `UNIQUE(symbol)` | 币种符号全局唯一 |
| t_chain | `UNIQUE(chain_code)` | 链编码唯一 |
| t_wallet_account | `UNIQUE(user_id, symbol)` | **一人一币一账户**；`idx_user_id` 支撑按用户列出 |
| t_asset_ledger | `UNIQUE(request_id)` **+** `UNIQUE(user_id,biz_type,ref_no)` | ① request_id 幂等兜底；② 业务单号级幂等（防同单重复入账/重复冻结） |
| t_deposit | `UNIQUE(tx_hash)` **+** `UNIQUE(request_id)` | ① **同一笔链上交易只能入账一次**（防重复入账）；② 入账幂等 |
| t_withdraw | `UNIQUE(request_id)` + `idx(status,create_time)` | 申请幂等；风控按状态+时间扫描 |
| t_asset_address | `UNIQUE(chain_code, address)` | 同一链地址只能归属一次（防地址复用冲突） |

---

## 四、内部 Feign 接口契约

> 服务：`exchange-asset`；**仅服务间调用，走 `/internal/asset/**`，不对外暴露**（网关不路由 `/internal/**`）。
> 统一返回 `com.web3.exchange.common.model.Result<T>`（`code=200` 成功，`error(...)` 失败）。
> 所有资金操作请求头（DTO）**必须携带 `requestId`** 保证幂等；金额单位一律 `long`（最小单位）。
> 调用方：`exchange-order`（冻结/解冻/过户）、`exchange-chain`（充值入账 credit）、`exchange-user`（可选查询）。

### 4.1 DTO 定义（放在 `exchange-common` 的 asset dto 包，供多模块复用）

```java
// 钱包账户视图
public class AccountVO {
    private Long   accountId;   // 账户ID
    private Long   userId;      // 用户ID
    private Long   coinId;      // 币种ID
    private String symbol;      // 币种符号
    private Long   available;   // 可用余额(最小单位)
    private Long   frozen;      // 冻结余额(最小单位)
    private Long   total;       // 总余额(最小单位)
    private Integer status;     // 账户状态:0=禁用,1=正常,2=冻结
    private Integer version;    // 乐观锁版本(查询用)
}

// 资金流水视图
public class LedgerVO {
    private Long    id;            // 流水ID
    private String  requestId;     // 幂等请求号
    private Long    userId;
    private Long    accountId;
    private Long    coinId;
    private String  symbol;
    private String  bizType;       // FREEZE/UNFREEZE/TRANSFER_IN/TRANSFER_OUT/DEPOSIT/WITHDRAW/FEE/REBATE
    private Integer direction;     // 1=IN 2=OUT 3=FROZEN 4=UNFROZEN
    private Long    amount;        // 变动金额(最小单位)
    private Long    beforeAvailable;
    private Long    afterAvailable;
    private Long    beforeFrozen;
    private Long    afterFrozen;
    private String  refNo;         // 业务单号
    private Integer status;        // 1=成功
    private String  remark;
    private LocalDateTime createTime;
}

// 冻结请求
public class FreezeRequest {
    @NotBlank private String requestId;   // 幂等号（order 下单号）
    @NotNull  private Long    userId;     // 冻结谁的余额
    @NotBlank private String symbol;      // 币种
    @NotNull  private Long    amount;     // 冻结金额(最小单位)
    @NotBlank private String bizType;     // 建议固定 FREEZE
    private String refNo;                 // 业务单号(orderId)
    private String remark;
}

// 解冻请求
public class UnfreezeRequest {
    @NotBlank private String requestId;   // 幂等号（order 撤单号）
    @NotNull  private Long    userId;
    @NotBlank private String symbol;
    @NotNull  private Long    amount;     // 解冻金额(<=冻结余额)
    @NotBlank private String bizType;     // UNFREEZE
    private String refNo;
    private String remark;
}

// 过户请求（成交结算：买卖双方同币种划转）
public class TransferRequest {
    @NotBlank private String requestId;   // 幂等号（t_trade.id）
    @NotNull  private Long    fromUserId; // 转出方(冻结余额→转入方可用)
    @NotNull  private Long    toUserId;   // 转入方
    @NotBlank private String symbol;      // 过户币种
    @NotNull  private Long    amount;     // 过户金额(最小单位)
    @NotBlank private String bizType;     // TRANSFER_OUT(转出)/TRANSFER_IN(转入) 由服务生成
    private String refNo;                 // 业务单号(orderId)
    private String remark;
}

// 充值入账请求（chain→asset）
public class CreditRequest {
    @NotBlank private String requestId;   // 幂等号（可由 depositId 派生）
    @NotNull  private Long    userId;
    @NotBlank private String symbol;
    @NotNull  private Long    amount;     // 入账金额(最小单位,已扣链上手续费)
    @NotBlank private String bizType;     // DEPOSIT
    private String refNo;                 // depositId
    private String remark;
}
```

### 4.2 接口清单

| 方法 | 接口 | 请求 | 返回 | 说明 |
|------|------|------|------|------|
| 开户 | `POST /internal/asset/account/open` | `{userId, symbol}`（可加 `coinId`） | `Result<AccountVO>` | 幂等开户：已存在则直接返回现有账户（`uk_user_symbol` 兜底），首次为所有币种各建一行 |
| 查余额 | `GET /internal/asset/account/balance?userId={}&symbol={}` | — | `Result<AccountVO>` | 查询单账户；不存在返回 `Result.notFound` |
| 按用户列出 | `GET /internal/asset/account/list?userId={}` | — | `Result<List<AccountVO>>` | 查询用户全部币种账户（钱包总览） |
| 冻结 | `POST /internal/asset/freeze` | `FreezeRequest` | `Result<LedgerVO>` | 可用→冻结；`available` 不足返回失败 |
| 解冻 | `POST /internal/asset/unfreeze` | `UnfreezeRequest` | `Result<LedgerVO>` | 冻结→可用；`frozen` 不足返回失败 |
| 过户 | `POST /internal/asset/transfer` | `TransferRequest` | `Result<LedgerVO>` | 单事务内：from 冻结额减少 + 写 TRANSFER_OUT 流水 + to 可用额增加 + 写 TRANSFER_IN 流水 |
| 充值入账 | `POST /internal/asset/credit` | `CreditRequest` | `Result<LedgerVO>` | chain 扫描确认后调用；写 DEPOSIT 流水 + 可用增加 |
| 流水查询 | `GET /internal/asset/ledger/list?accountId={}&page={}&size={}` | — | `Result<Page<LedgerVO>>` | 分页查流水，供对账/审计 |

> **执行语义**：所有写接口（open/freeze/unfreeze/transfer/credit）内部为**同一本地事务**，含「写流水 + 更新余额」，配 `SELECT ... FOR UPDATE` 行锁 + `version` 乐观锁，失败整体回滚并返回 `Result.error`（携带业务码，如余额不足 `409`、重复请求幂等命中返回 `200` + 首次结果）。

### 4.3 Feign 客户端示例（供 order/chain 引用）

```java
@FeignClient(name = "exchange-asset", path = "/internal/asset")
public interface AssetClient {
    @PostMapping("/freeze")
    Result<LedgerVO> freeze(@RequestBody FreezeRequest req);

    @PostMapping("/unfreeze")
    Result<LedgerVO> unfreeze(@RequestBody UnfreezeRequest req);

    @PostMapping("/transfer")
    Result<LedgerVO> transfer(@RequestBody TransferRequest req);

    @PostMapping("/credit")
    Result<LedgerVO> credit(@RequestBody CreditRequest req);

    @GetMapping("/account/balance")
    Result<AccountVO> getBalance(@RequestParam("userId") Long userId,
                                 @RequestParam("symbol") String symbol);
}
```

---

## 五、幂等与并发控制设计

**问题**：Feign 超时重试、MQ 重复投递、并发撮合都会导致**同一笔资金操作被重复执行**，造成资产错账。

### 5.1 幂等键设计（双层）

| 层 | 键 | 存储 | 兜底机制 |
|----|----|------|---------|
| 请求级 | `request_id`（调用方生成，如 `tradeId/orderId/withdrawId` 派生） | `t_asset_ledger.request_id` **唯一索引** | 重复 `request_id` 插入唯一索引冲突 → 捕获后查既有流水原样返回（`Result.success` + 已有 LedgerVO），不重放操作 |
| 业务级 | `(user_id, biz_type, ref_no)` | `t_asset_ledger.uk_biz_no` **唯一索引** | 同一业务单号（如某笔成交、某笔充值）重复入账被拒绝；`t_deposit.uk_tx_hash` 防**同一笔链上交易重复入账** |

> 约定：`request_id` 由**调用方**生成并保证其语义确定性（同一业务重复发起时 request_id 相同），asset 侧只做唯一性校验与回读。

### 5.2 并发控制（防超卖/重复扣减）

```
资金变更伪代码（freeze 为例）：
1. begin tx
2. account = SELECT * FROM t_wallet_account WHERE user_id=? AND symbol=? FOR UPDATE  // 行锁,串行化同账户并发
3. if account.version != req.version(optional) → 乐观锁冲突, 报错
4. if account.available < amount → 余额不足, 回滚返回失败
5. 先尝试插入 t_asset_ledger(request_id, ...)   // 唯一索引兜底幂等; 冲突→回滚→查询已存在流水返回
6. UPDATE t_wallet_account SET available=available-amount, frozen=frozen+amount,
                                total=available+frozen, version=version+1 WHERE id=? AND version=?
7. commit
```

- **行锁（悲观）**：`SELECT ... FOR UPDATE` 保证同账户的冻结/解冻/过户/入账**串行执行**，是资金安全的第一道防线。
- **乐观锁（version）**：MyBatis-Plus `@Version` 兜底，防止多实例间 `FOR UPDATE` 未覆盖到的路径（如余额归零判断），冲突则重试或失败。
- **唯一索引（幂等）**：`request_id`/`uk_biz_no`/`uk_tx_hash` 是重复请求的最终防线。
- **余额不变式**：任何分支都不允许 `available + frozen != total`，`after` 余额由 `before` 与 `amount`、`direction` 计算得出并写入流水，供对账脚本校验。

### 5.3 与外部系统的一致性

- Feign 同步调用内**已含本地事务**；跨服务（order↔asset、chain↔asset）的最终一致性建议叠加 **RocketMQ 事务消息**（Phase 1 落地时先以「Feign 同步 + 幂等」起步，Phase 中再引入事务消息，二者不冲突）。
- 失败/重试路径：调用方捕获 `Result` 非 200 后，可用**同一 request_id** 安全重试，asset 幂等返回。

---

## 六、落地 Checklist（/dev 实施指引）

- [ ] 执行 `sql/asset.sql` 建表（库 `web3_exchange`）。
- [ ] `exchange-asset`：`CoinService`/`ChainService`/`AccountService`/`LedgerService`/`DepositService`/`WithdrawService`/`AssetAddressService`，实体继承 `BaseEntity`，金额字段 `Long`。
- [ ] 内部控制器 `/internal/asset/**`，统一 `Result<T>`；开户在首次查询/入账时按 `t_coin` 自动建账户。
- [ ] 资金变动统一封装 `LedgerService.change(requestId, ...)`：写流水 + 行锁 + 更新余额 + 幂等回读（见 §5.2 伪码）。
- [ ] `AmountUtil`（`toMinor/toMajor`）统一精度换算，业务层禁用 `double/float` 参与金额运算。
- [ ] 对外 REST `/api/asset/**`（balance/deposit/withdraw/address）在内部接口之上封装，经网关鉴权。
- [ ] 通过内部 Feign 跑通「开户 → 冻结 → 过户 → 解冻」闭环，写对账脚本校验 `Σledger == 账户余额`。

> 本文件为资产域落地依据；若实现中需调整字段/契约，请同步更新本文件与 `docs/ARCHITECTURE.md` 附录 A1，保持与 `PROJECT_MEMORY.md` 一致。
