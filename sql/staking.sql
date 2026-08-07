-- ============================================================
-- Staking/Earn（Phase 2.3）建表脚本
-- 库：web3_exchange；表前缀 t_；引擎 InnoDB；utf8mb4
-- 设计见 docs/staking-domain.md
-- ============================================================

-- 质押产品
CREATE TABLE IF NOT EXISTS `t_staking_product` (
  `id` bigint NOT NULL COMMENT '产品ID',
  `product_code` varchar(32) NOT NULL COMMENT '产品编码',
  `name` varchar(64) NOT NULL COMMENT '产品名称',
  `type` tinyint NOT NULL DEFAULT '0' COMMENT '类型:0=活期,1=锁仓',
  `symbol` varchar(32) NOT NULL COMMENT '币种符号',
  `annual_rate_bp` int NOT NULL DEFAULT '500' COMMENT '年化利率(基点,10000=100%,500=5%)',
  `min_amount` bigint NOT NULL DEFAULT '0' COMMENT '最小质押额(最小单位)',
  `lock_days` int NOT NULL DEFAULT '0' COMMENT '锁仓天数(活期为0)',
  `status` tinyint NOT NULL DEFAULT '1' COMMENT '状态:0=下架,1=上架',
  `create_by` varchar(64) DEFAULT '' COMMENT '创建者',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by` varchar(64) DEFAULT '' COMMENT '更新者',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `is_deleted` tinyint DEFAULT '0' COMMENT '逻辑删除',
  `version` int DEFAULT '0' COMMENT '乐观锁',
  `tenant_id` bigint DEFAULT '0' COMMENT '租户',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_product_code` (`product_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='质押产品表';

-- 用户质押持仓
CREATE TABLE IF NOT EXISTS `t_staking_position` (
  `id` bigint NOT NULL COMMENT '持仓ID',
  `user_id` bigint NOT NULL COMMENT '用户ID',
  `product_code` varchar(32) NOT NULL COMMENT '产品编码',
  `symbol` varchar(32) NOT NULL COMMENT '币种符号',
  `amount` bigint NOT NULL COMMENT '质押本金(最小单位)',
  `accrued_interest` bigint NOT NULL DEFAULT '0' COMMENT '累计未结收益',
  `total_interest` bigint NOT NULL DEFAULT '0' COMMENT '累计已结收益',
  `status` tinyint NOT NULL DEFAULT '0' COMMENT '状态:0=质押中,1=已赎回',
  `start_time` datetime DEFAULT NULL COMMENT '质押时间',
  `lock_end_time` datetime DEFAULT NULL COMMENT '锁仓到期时间(活期为null)',
  `redeem_time` datetime DEFAULT NULL COMMENT '赎回时间',
  `create_by` varchar(64) DEFAULT '' COMMENT '创建者',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by` varchar(64) DEFAULT '' COMMENT '更新者',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `is_deleted` tinyint DEFAULT '0' COMMENT '逻辑删除',
  `version` int DEFAULT '0' COMMENT '乐观锁',
  `tenant_id` bigint DEFAULT '0' COMMENT '租户',
  PRIMARY KEY (`id`),
  KEY `idx_user_status` (`user_id`,`status`),
  KEY `idx_user_product` (`user_id`,`product_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户质押持仓表';

-- 收益结算流水
CREATE TABLE IF NOT EXISTS `t_staking_interest` (
  `id` bigint NOT NULL COMMENT '流水ID',
  `user_id` bigint NOT NULL COMMENT '用户ID',
  `position_id` bigint NOT NULL COMMENT '持仓ID',
  `symbol` varchar(32) NOT NULL COMMENT '币种符号',
  `amount` bigint NOT NULL COMMENT '本次结算收益(最小单位)',
  `settle_date` varchar(16) NOT NULL COMMENT '结算日期(YYYYMMDD)',
  `request_id` varchar(64) NOT NULL COMMENT '幂等号',
  `remark` varchar(255) DEFAULT NULL COMMENT '备注',
  `create_by` varchar(64) DEFAULT '' COMMENT '创建者',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by` varchar(64) DEFAULT '' COMMENT '更新者',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `is_deleted` tinyint DEFAULT '0' COMMENT '逻辑删除',
  `version` int DEFAULT '0' COMMENT '乐观锁',
  `tenant_id` bigint DEFAULT '0' COMMENT '租户',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_request_id` (`request_id`),
  KEY `idx_user` (`user_id`,`settle_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='质押收益流水表';
