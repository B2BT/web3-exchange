-- ============================================================
-- 通知域（Phase 4）建表脚本
-- 库：web3_exchange；表前缀 t_；引擎 InnoDB；utf8mb4
-- 与 sql/user.sql、sql/asset.sql、sql/order.sql 风格一致：雪花主键 + BaseEntity 系统字段 + 中文注释
-- 落地依据：docs/notify-domain.md
-- ============================================================

CREATE TABLE IF NOT EXISTS `t_notification` (
  `id` bigint NOT NULL COMMENT '通知ID',
  `user_id` bigint NOT NULL COMMENT '接收用户ID',
  `type` varchar(32) NOT NULL COMMENT '通知类型:DEPOSIT_CONFIRMED/WITHDRAW_SUCCESS/TRADE_FILLED',
  `title` varchar(128) NOT NULL COMMENT '标题',
  `content` varchar(1024) NOT NULL COMMENT '内容(含业务详情)',
  `biz_type` varchar(32) NOT NULL DEFAULT '' COMMENT '源事件业务类型:DEPOSIT/WITHDRAW/TRADE',
  `biz_ref` varchar(64) NOT NULL COMMENT '关联业务单号(幂等键组分):depositId/withdrawId/tradeNo:BUY|:SELL',
  `symbol` varchar(32) DEFAULT NULL COMMENT '关联币种/交易对(冗余检索)',
  `amount` bigint DEFAULT NULL COMMENT '关联金额(最小单位,冗余展示)',
  `is_read` tinyint NOT NULL DEFAULT '0' COMMENT '已读状态:0=未读,1=已读',
  `read_time` datetime DEFAULT NULL COMMENT '已读时间',
  `channel` varchar(20) NOT NULL DEFAULT 'INBOX' COMMENT '通知渠道:INBOX站内信(本期仅此)',
  -- 系统字段 --
  `create_by` varchar(64) DEFAULT '' COMMENT '创建者',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by` varchar(64) DEFAULT '' COMMENT '更新者',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `is_deleted` tinyint DEFAULT '0' COMMENT '逻辑删除:0=未删,1=已删',
  `version` int DEFAULT '0' COMMENT '乐观锁版本号',
  `tenant_id` bigint DEFAULT '0' COMMENT '租户ID',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_type_bizref` (`user_id`,`type`,`biz_ref`),
  KEY `idx_user_read_time` (`user_id`,`is_read`,`create_time`),
  KEY `idx_user_time` (`user_id`,`create_time`),
  KEY `idx_biz_ref` (`biz_ref`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='站内通知表';
