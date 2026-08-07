-- ============================================================
-- Web3 钱包域（Phase 2.1）自托管钱包表
-- 库：web3_exchange；表前缀 t_；引擎 InnoDB；utf8mb4
-- 与既有 sql 风格一致：雪花主键 + BaseEntity 系统字段 + 中文注释
-- 设计见 docs/web3-wallet.md §二.2（自托管钱包新增表）
-- ============================================================

-- ------------------------------------------------------------
-- 用户自托管钱包表 t_user_wallet
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `t_user_wallet` (
  `id` bigint NOT NULL COMMENT '钱包ID',

  `user_id` bigint NOT NULL COMMENT '用户ID',
  `chain_code` varchar(32) NOT NULL COMMENT '链编码:ETH/BSC/BTC/TRON',
  `wallet_type` varchar(20) NOT NULL DEFAULT 'HD' COMMENT '钱包类型:HD=助记词派生,PRIVATE=导入私钥,READONLY=只读绑定',
  `mnemonic_enc` text DEFAULT NULL COMMENT '加密助记词(AES-GCM,仅HD)',
  `private_key_enc` text DEFAULT NULL COMMENT '加密私钥(AES-GCM)',
  `address` varchar(255) NOT NULL COMMENT '钱包地址',
  `address_type` varchar(20) NOT NULL DEFAULT 'SELF' COMMENT '地址类型:CUSTODIAL=托管(交易所持钥),SELF=自托管(用户持钥)',
  `name` varchar(64) DEFAULT NULL COMMENT '钱包备注名',
  `status` tinyint NOT NULL DEFAULT '1' COMMENT '状态:0=禁用,1=正常',

  -- 系统字段 --
  `create_by` varchar(64) DEFAULT '' COMMENT '创建者',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by` varchar(64) DEFAULT '' COMMENT '更新者',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `is_deleted` tinyint DEFAULT '0' COMMENT '逻辑删除:0=未删,1=已删',
  `version` int DEFAULT '0' COMMENT '乐观锁版本号',
  `tenant_id` bigint DEFAULT '0' COMMENT '租户ID',

  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_chain_address` (`user_id`, `chain_code`, `address`),
  KEY `idx_user_id` (`user_id`),
  KEY `idx_chain_code` (`chain_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户自托管钱包表';
