-- ============================================================
-- 杠杆现货（Phase 2.2）建表脚本
-- 库：web3_exchange；表前缀 t_；引擎 InnoDB；utf8mb4
-- 设计见 docs/margin-domain.md
-- ============================================================

-- 杠杆账户
CREATE TABLE IF NOT EXISTS `t_margin_account` (
  `id` bigint NOT NULL COMMENT '账户ID',
  `user_id` bigint NOT NULL COMMENT '用户ID',
  `symbol` varchar(32) NOT NULL COMMENT '币种符号',
  `collateral` bigint NOT NULL DEFAULT '0' COMMENT '抵押(计价币最小单位)',
  `borrowed` bigint NOT NULL DEFAULT '0' COMMENT '借入本金(最小单位)',
  `interest_accrued` bigint NOT NULL DEFAULT '0' COMMENT '未还利息(最小单位)',
  `status` tinyint NOT NULL DEFAULT '1' COMMENT '状态:0=禁用,1=正常',
  `create_by` varchar(64) DEFAULT '' COMMENT '创建者',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by` varchar(64) DEFAULT '' COMMENT '更新者',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `is_deleted` tinyint DEFAULT '0' COMMENT '逻辑删除',
  `version` int DEFAULT '0' COMMENT '乐观锁',
  `tenant_id` bigint DEFAULT '0' COMMENT '租户',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_symbol` (`user_id`,`symbol`),
  KEY `idx_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='杠杆账户表';

-- 借币记录
CREATE TABLE IF NOT EXISTS `t_margin_loan` (
  `id` bigint NOT NULL COMMENT '借单ID',
  `user_id` bigint NOT NULL COMMENT '用户ID',
  `symbol` varchar(32) NOT NULL COMMENT '币种符号',
  `request_id` varchar(64) NOT NULL COMMENT '幂等号',
  `amount` bigint NOT NULL COMMENT '借入本金(最小单位)',
  `rate_daily` bigint NOT NULL DEFAULT '0' COMMENT '日利率(基点,10000=100%)',
  `principal_remain` bigint NOT NULL COMMENT '剩余本金',
  `interest_accrued` bigint NOT NULL DEFAULT '0' COMMENT '该笔累计利息',
  `status` tinyint NOT NULL DEFAULT '0' COMMENT '状态:0=借出中,1=已还清',
  `open_time` datetime DEFAULT NULL COMMENT '借出时间',
  `repay_time` datetime DEFAULT NULL COMMENT '还清时间',
  `create_by` varchar(64) DEFAULT '' COMMENT '创建者',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by` varchar(64) DEFAULT '' COMMENT '更新者',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `is_deleted` tinyint DEFAULT '0' COMMENT '逻辑删除',
  `version` int DEFAULT '0' COMMENT '乐观锁',
  `tenant_id` bigint DEFAULT '0' COMMENT '租户',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_request_id` (`request_id`),
  KEY `idx_user_status` (`user_id`,`status`),
  KEY `idx_user_symbol` (`user_id`,`symbol`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='借币记录表';

-- 币种日利率配置
CREATE TABLE IF NOT EXISTS `t_margin_interest_rate` (
  `id` bigint NOT NULL COMMENT 'ID',
  `symbol` varchar(32) NOT NULL COMMENT '币种符号',
  `rate_daily_bp` int NOT NULL DEFAULT '10' COMMENT '日利率(基点,10000=100%;10=0.1%/日)',
  `maintenance_ratio` int NOT NULL DEFAULT '120' COMMENT '维持保证金率(百分数,120=120%)',
  `status` tinyint NOT NULL DEFAULT '1' COMMENT '状态:0=禁用,1=正常',
  `create_by` varchar(64) DEFAULT '' COMMENT '创建者',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by` varchar(64) DEFAULT '' COMMENT '更新者',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `is_deleted` tinyint DEFAULT '0' COMMENT '逻辑删除',
  `version` int DEFAULT '0' COMMENT '乐观锁',
  `tenant_id` bigint DEFAULT '0' COMMENT '租户',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_symbol` (`symbol`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='杠杆利率配置表';
