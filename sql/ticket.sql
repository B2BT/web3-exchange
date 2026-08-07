-- ============================================================
-- Phase 3.3 客服工单建表脚本
-- 库：web3_exchange；模块：exchange-ticket（端口 8116）
-- 设计见 docs/ticket-domain.md
-- ============================================================

-- 工单主表
CREATE TABLE IF NOT EXISTS `t_ticket` (
  `id` bigint NOT NULL COMMENT '主键',
  `user_id` bigint NOT NULL COMMENT '提交用户ID',
  `category` varchar(32) NOT NULL DEFAULT 'OTHER' COMMENT '分类:DEPOSIT/WITHDRAW/TRADE/ACCOUNT/OTHER',
  `title` varchar(128) NOT NULL COMMENT '标题',
  `content` text COMMENT '问题描述',
  `status` tinyint NOT NULL DEFAULT 0 COMMENT '0开放 1处理中 2已解决 3已关闭',
  `priority` tinyint NOT NULL DEFAULT 1 COMMENT '0低 1中 2高',
  `assignee_id` bigint DEFAULT NULL COMMENT '处理管理员ID',
  `resolved_at` datetime DEFAULT NULL COMMENT '解决时间',
  `is_deleted` tinyint NOT NULL DEFAULT 0,
  `version` int NOT NULL DEFAULT 0,
  `tenant_id` bigint NOT NULL DEFAULT 0,
  `create_by` bigint DEFAULT NULL,
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_by` bigint DEFAULT NULL,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_user` (`user_id`),
  KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='客服工单表';

-- 工单回复表
CREATE TABLE IF NOT EXISTS `t_ticket_reply` (
  `id` bigint NOT NULL COMMENT '主键',
  `ticket_id` bigint NOT NULL COMMENT '工单ID',
  `user_id` bigint NOT NULL COMMENT '回复人ID',
  `is_staff` tinyint NOT NULL DEFAULT 0 COMMENT '1管理员 0用户',
  `content` text COMMENT '回复内容',
  `is_deleted` tinyint NOT NULL DEFAULT 0,
  `version` int NOT NULL DEFAULT 0,
  `tenant_id` bigint NOT NULL DEFAULT 0,
  `create_by` bigint DEFAULT NULL,
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_by` bigint DEFAULT NULL,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_ticket` (`ticket_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='工单回复表';
