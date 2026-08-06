-- ============================================================
-- 行情域 K线落库（Phase: K线持久化）建表脚本
-- 库：web3_exchange；表前缀 t_；引擎 InnoDB；utf8mb4
-- 与 sql/user.sql、sql/asset.sql、sql/order.sql、sql/notify.sql 风格一致：
-- 雪花主键 + BaseEntity 系统字段 + 中文注释
-- 落地依据：docs/market-domain.md（内存聚合 + 可选落库双轨持久化）
--
-- 设计要点：
--  * uk_symbol_period_window(symbol,period,window_start) 唯一索引 = 幂等 upsert 关键：
--    同 symbol×period×窗口 重复成交只更新同一行，不产生重复行。
--  * 只对「已关闭窗口」（window_start + period 时长 < now）落库；当前打开窗口仅内存。
--  * window_start 存 epoch millis(UTC 整点对齐)，与内存 Kline.openTime 直接对应，免时区换算。
--  * open/high/low/close/volume/quote_volume 一律 bigint(Long 最小单位)。
-- ============================================================

CREATE TABLE IF NOT EXISTS `t_kline` (
  `id` bigint NOT NULL COMMENT 'K线ID(雪花算法)',

  `symbol` varchar(32) NOT NULL COMMENT '交易对:BTC/USDT 等(与内存 store key 一致)',
  `period` varchar(8) NOT NULL COMMENT 'K线周期:1m/5m/15m/1h/4h/1d',
  `window_start` bigint NOT NULL COMMENT '窗口开始时间(epoch millis, UTC 整点对齐)',
  `open` bigint NOT NULL COMMENT '开盘价(计价币最小单位)',
  `high` bigint NOT NULL COMMENT '最高价(计价币最小单位)',
  `low` bigint NOT NULL COMMENT '最低价(计价币最小单位)',
  `close` bigint NOT NULL COMMENT '收盘价(计价币最小单位)',
  `volume` bigint NOT NULL COMMENT '成交量(基础币最小单位)',
  `quote_volume` bigint NOT NULL COMMENT '成交额(计价币最小单位)',

  -- 系统字段 --
  `create_by` varchar(64) DEFAULT '' COMMENT '创建者',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_by` varchar(64) DEFAULT '' COMMENT '更新者',
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `is_deleted` tinyint DEFAULT '0' COMMENT '逻辑删除:0=未删,1=已删',
  `version` int DEFAULT '0' COMMENT '乐观锁版本号(upsert 时自增)',
  `tenant_id` bigint DEFAULT '0' COMMENT '租户ID',

  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_symbol_period_window` (`symbol`,`period`,`window_start`),
  KEY `idx_period_window` (`period`,`window_start`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='K线聚合表(内存+DB双轨持久化)';
