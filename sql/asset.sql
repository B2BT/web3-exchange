-- ============================================================
-- 资产域（Phase 1）建表脚本
-- 库：web3_exchange；表前缀 t_；引擎 InnoDB；utf8mb4
-- 与 sql/user.sql 风格一致：雪花主键 + BaseEntity 系统字段 + 中文注释
-- 落地细节见 docs/asset-domain.md（含内部 Feign 契约与幂等设计）
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

-- NFT 标准扩展：ERC-721/ERC-1155（token_standard 默认 ERC-20，兼容既有币种）
ALTER TABLE `t_coin`
  ADD COLUMN `token_standard` varchar(20) NOT NULL DEFAULT 'ERC-20' COMMENT '代币标准:ERC-20=同质化,ERC-721=NFT,ERC-1155=半同质化' AFTER `coin_type`;

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

-- NFT 充值扩展：token_id（ERC-721 的 NFT 编号 / ERC-1155 的 id；ERC-20 为 NULL）
ALTER TABLE `t_deposit`
  ADD COLUMN `token_id` varchar(128) DEFAULT NULL COMMENT 'NFT代币ID(ERC-721/1155; ERC-20为空)' AFTER `amount`;

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

-- NFT 提现扩展：token_id（ERC-721/1155 提现指定 NFT；ERC-20 为空）
ALTER TABLE `t_withdraw`
  ADD COLUMN `token_id` varchar(128) DEFAULT NULL COMMENT 'NFT代币ID(ERC-721/1155; ERC-20为空)' AFTER `amount`;

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
