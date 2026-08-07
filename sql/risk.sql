-- ============================================================
-- 风控引擎（Phase 2.4）建表脚本
-- 库：web3_exchange；表前缀 t_；引擎 InnoDB；utf8mb4
-- 设计见 docs/risk-domain.md
-- ============================================================

-- 风控规则
CREATE TABLE IF NOT EXISTS `t_risk_rule` (
  `id` bigint NOT NULL COMMENT '规则ID',
  `rule_code` varchar(48) NOT NULL COMMENT '规则编码',
  `name` varchar(64) NOT NULL COMMENT '规则名称',
  `rule_type` varchar(32) NOT NULL COMMENT '类型:ORDER_SLIPPAGE/ORDER_AMOUNT/ORDER_DAILY',
  `scope` varchar(16) NOT NULL DEFAULT 'GLOBAL' COMMENT '作用域:GLOBAL/USER',
  `symbol` varchar(32) DEFAULT NULL COMMENT '交易对(可空=全部)',
  `threshold` bigint NOT NULL DEFAULT '0' COMMENT '阈值(滑点bps/金额最小单位)',
  `status` tinyint NOT NULL DEFAULT '1' COMMENT '状态:0=停用,1=启用',
  `create_by` varchar(64) DEFAULT '' COMMENT '创建者',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by` varchar(64) DEFAULT '' COMMENT '更新者',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `is_deleted` tinyint DEFAULT '0' COMMENT '逻辑删除',
  `version` int DEFAULT '0' COMMENT '乐观锁',
  `tenant_id` bigint DEFAULT '0' COMMENT '租户',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_rule_code` (`rule_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='风控规则表';

-- 反钓鱼码
CREATE TABLE IF NOT EXISTS `t_anti_phishing` (
  `id` bigint NOT NULL COMMENT 'ID',
  `user_id` bigint NOT NULL COMMENT '用户ID',
  `phrase` varchar(128) NOT NULL COMMENT '反钓鱼短语',
  `create_by` varchar(64) DEFAULT '' COMMENT '创建者',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by` varchar(64) DEFAULT '' COMMENT '更新者',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `is_deleted` tinyint DEFAULT '0' COMMENT '逻辑删除',
  `version` int DEFAULT '0' COMMENT '乐观锁',
  `tenant_id` bigint DEFAULT '0' COMMENT '租户',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='反钓鱼码表';

-- 登录日志（风控独立表，避免与既有 t_login_log 旧 schema 冲突）
CREATE TABLE IF NOT EXISTS `t_risk_login_log` (
  `id` bigint NOT NULL COMMENT '日志ID',
  `user_id` bigint NOT NULL COMMENT '用户ID',
  `username` varchar(64) DEFAULT '' COMMENT '用户名',
  `ip` varchar(64) DEFAULT '' COMMENT '登录IP',
  `user_agent` varchar(512) DEFAULT '' COMMENT 'UA',
  `device` varchar(64) DEFAULT '' COMMENT '设备(粗略)',
  `result` tinyint NOT NULL DEFAULT '0' COMMENT '结果:0=成功,1=失败',
  `risk` tinyint NOT NULL DEFAULT '0' COMMENT '风险:0=正常,1=异常(异地/新设备)',
  `create_by` varchar(64) DEFAULT '' COMMENT '创建者',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by` varchar(64) DEFAULT '' COMMENT '更新者',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `is_deleted` tinyint DEFAULT '0' COMMENT '逻辑删除',
  `version` int DEFAULT '0' COMMENT '乐观锁',
  `tenant_id` bigint DEFAULT '0' COMMENT '租户',
  PRIMARY KEY (`id`),
  KEY `idx_user_time` (`user_id`,`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='风控登录日志表';

-- 提现二次验证记录
CREATE TABLE IF NOT EXISTS `t_withdraw_verify` (
  `id` bigint NOT NULL COMMENT 'ID',
  `user_id` bigint NOT NULL COMMENT '用户ID',
  `withdraw_id` bigint NOT NULL COMMENT '提现ID',
  `verify_code_hash` varchar(128) NOT NULL COMMENT '验证码哈希',
  `channel` varchar(16) NOT NULL DEFAULT 'EMAIL' COMMENT '渠道:EMAIL/SMS',
  `status` tinyint NOT NULL DEFAULT '0' COMMENT '状态:0=待验证,1=已通过',
  `expire_time` datetime DEFAULT NULL COMMENT '过期时间',
  `create_by` varchar(64) DEFAULT '' COMMENT '创建者',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by` varchar(64) DEFAULT '' COMMENT '更新者',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `is_deleted` tinyint DEFAULT '0' COMMENT '逻辑删除',
  `version` int DEFAULT '0' COMMENT '乐观锁',
  `tenant_id` bigint DEFAULT '0' COMMENT '租户',
  PRIMARY KEY (`id`),
  KEY `idx_withdraw` (`withdraw_id`),
  KEY `idx_user` (`user_id`,`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='提现二次验证表';
