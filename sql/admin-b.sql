-- ============================================================
-- Admin B（Phase 2.5）建表脚本
-- 库：web3_exchange；表前缀 t_；引擎 InnoDB；utf8mb4
-- 设计见 docs/admin-b-domain.md
-- ============================================================

-- 公告
CREATE TABLE IF NOT EXISTS `t_announcement` (
  `id` bigint NOT NULL COMMENT '公告ID',
  `title` varchar(128) NOT NULL COMMENT '标题',
  `content` text COMMENT '内容',
  `type` tinyint NOT NULL DEFAULT '0' COMMENT '类型:0=公告,1=活动,2=系统',
  `status` tinyint NOT NULL DEFAULT '0' COMMENT '状态:0=草稿,1=已发布,2=已下线',
  `publish_time` datetime DEFAULT NULL COMMENT '发布时间',
  `publisher_id` bigint DEFAULT NULL COMMENT '发布人ID',
  `view_count` int NOT NULL DEFAULT '0' COMMENT '浏览量',
  `create_by` varchar(64) DEFAULT '' COMMENT '创建者',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by` varchar(64) DEFAULT '' COMMENT '更新者',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `is_deleted` tinyint DEFAULT '0' COMMENT '逻辑删除',
  `version` int DEFAULT '0' COMMENT '乐观锁',
  `tenant_id` bigint DEFAULT '0' COMMENT '租户',
  PRIMARY KEY (`id`),
  KEY `idx_status` (`status`),
  KEY `idx_type` (`type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='公告表';

-- 管理员审计日志
CREATE TABLE IF NOT EXISTS `t_admin_audit` (
  `id` bigint NOT NULL COMMENT '审计ID',
  `admin_user_id` bigint NOT NULL COMMENT '管理员用户ID',
  `admin_username` varchar(64) DEFAULT '' COMMENT '管理员用户名',
  `action` varchar(64) NOT NULL COMMENT '操作类型',
  `target_type` varchar(32) DEFAULT '' COMMENT '目标类型',
  `target_id` varchar(64) DEFAULT '' COMMENT '目标ID',
  `detail` varchar(2000) DEFAULT '' COMMENT '详情(JSON/文本)',
  `ip` varchar(64) DEFAULT '' COMMENT 'IP',
  `create_by` varchar(64) DEFAULT '' COMMENT '创建者',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by` varchar(64) DEFAULT '' COMMENT '更新者',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `is_deleted` tinyint DEFAULT '0' COMMENT '逻辑删除',
  `version` int DEFAULT '0' COMMENT '乐观锁',
  `tenant_id` bigint DEFAULT '0' COMMENT '租户',
  PRIMARY KEY (`id`),
  KEY `idx_admin` (`admin_user_id`),
  KEY `idx_time` (`create_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='管理员审计日志表';

-- 服务健康快照
CREATE TABLE IF NOT EXISTS `t_service_health` (
  `id` bigint NOT NULL COMMENT 'ID',
  `service_name` varchar(64) NOT NULL COMMENT '服务名',
  `instance_ip` varchar(64) DEFAULT '' COMMENT '实例IP',
  `port` int DEFAULT NULL COMMENT '端口',
  `status` tinyint NOT NULL DEFAULT '0' COMMENT '状态:0=DOWN,1=UP',
  `memory_used` bigint DEFAULT '0' COMMENT '已用内存(字节)',
  `memory_total` bigint DEFAULT '0' COMMENT '总内存(字节)',
  `last_heartbeat` datetime DEFAULT NULL COMMENT '最近心跳',
  `create_by` varchar(64) DEFAULT '' COMMENT '创建者',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by` varchar(64) DEFAULT '' COMMENT '更新者',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `is_deleted` tinyint DEFAULT '0' COMMENT '逻辑删除',
  `version` int DEFAULT '0' COMMENT '乐观锁',
  `tenant_id` bigint DEFAULT '0' COMMENT '租户',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_service_instance` (`service_name`,`instance_ip`,`port`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='服务健康快照表';
