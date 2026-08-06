-- ============================================================
-- 订单/撮合域（Phase 2）建表脚本
-- 库：web3_exchange；表前缀 t_；引擎 InnoDB；utf8mb4
-- 与 sql/user.sql、sql/asset.sql 风格一致：雪花主键 + BaseEntity 系统字段 + 中文注释
-- 金额一律 BIGINT 最小单位（与资产域 t_coin.decimals 一致，杜绝浮点）
-- 落地依据：docs/order-domain.md
-- ============================================================

-- ------------------------------------------------------------
-- 1. 交易对表 t_symbol
--    定义可交易对（如 BTC/USDT），价格/数量精度、最小下单、费率、开关。
--    由运营/管理员维护；撮合引擎按此表驱动（仅 status=1 可交易）。
-- ------------------------------------------------------------
CREATE TABLE `t_symbol` (
    `id` bigint NOT NULL COMMENT '交易对ID',

    `symbol` varchar(32) NOT NULL COMMENT '交易对符号:BTC/USDT',
    `base_coin` varchar(32) NOT NULL COMMENT '基础币(被交易资产,如BTC)',
    `quote_coin` varchar(32) NOT NULL COMMENT '计价币(用于标价,如USDT)',
    `base_coin_id` bigint DEFAULT NULL COMMENT '基础币ID(关联t_coin)',
    `quote_coin_id` bigint DEFAULT NULL COMMENT '计价币ID(关联t_coin)',

    `price_precision` int NOT NULL DEFAULT '0' COMMENT '价格精度(小数位数)',
    `amount_precision` int NOT NULL DEFAULT '0' COMMENT '数量精度(小数位数)',
    `price_tick` bigint NOT NULL DEFAULT '1' COMMENT '最小价格变动单位(计价币最小单位),限价必须为该整数倍',
    `min_amount` bigint NOT NULL DEFAULT '0' COMMENT '最小下单数量(基础币最小单位)',
    `max_amount` bigint DEFAULT NULL COMMENT '单笔最大下单数量(基础币最小单位)',
    `min_notional` bigint NOT NULL DEFAULT '0' COMMENT '最小下单名义值(计价币最小单位,price*quantity下限)',
    `taker_fee_rate` int NOT NULL DEFAULT '0' COMMENT '吃单费率(基点,bp;10=0.1%;本阶段默认0)',
    `maker_fee_rate` int NOT NULL DEFAULT '0' COMMENT '挂单费率(基点,bp;本阶段默认0)',

    `sort` int DEFAULT '0' COMMENT '排序',
    `status` tinyint NOT NULL DEFAULT '1' COMMENT '状态:0=停牌(禁止交易),1=交易中',

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
    KEY `idx_base_coin` (`base_coin`),
    KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='交易对表';

-- ------------------------------------------------------------
-- 2. 订单表 t_order
--    side:  1=BUY买入  2=SELL卖出
--    type:  1=GTC限价  2=MARKET市价
--    status:0=NEW待撮合(ACTIVE,挂单中) 1=PARTIAL_FILLED部分成交 2=FILLED全部成交
--           3=CANCELLED已撤单 4=REJECTED已拒绝
--    金额字段(BIGINT最小单位):price/quote_amount 为计价币;quantity/remaining/filled 为基础币。
--    freeze_* 记录本单在 asset 的冻结明细,用于成交过户/撤单解冻的尾差计算。
-- ------------------------------------------------------------
CREATE TABLE `t_order` (
    `id` bigint NOT NULL COMMENT '订单ID',

    `order_no` varchar(64) NOT NULL COMMENT '业务订单号(全局唯一,幂等基)',
    `client_oid` varchar(64) DEFAULT NULL COMMENT '客户端订单号(客户端幂等,防重复下单)',
    `user_id` bigint NOT NULL COMMENT '用户ID',
    `symbol` varchar(32) NOT NULL COMMENT '交易对',
    `base_coin` varchar(32) NOT NULL COMMENT '基础币(冗余,资金操作用)',
    `quote_coin` varchar(32) NOT NULL COMMENT '计价币(冗余,资金操作用)',

    `side` tinyint NOT NULL COMMENT '方向:1=BUY买入 2=SELL卖出',
    `order_type` tinyint NOT NULL COMMENT '类型:1=GTC限价 2=MARKET市价',
    `price` bigint NOT NULL DEFAULT '0' COMMENT '限价(计价币最小单位;市价为0)',
    `quantity` bigint NOT NULL DEFAULT '0' COMMENT '下单数量(基础币最小单位;市价买单为0,见quote_amount)',
    `quote_amount` bigint NOT NULL DEFAULT '0' COMMENT '市价买单预算额(计价币最小单位;限价/市价卖单为0)',

    `remaining` bigint NOT NULL DEFAULT '0' COMMENT '剩余未成交数量(基础币最小单位)',
    `filled_amount` bigint NOT NULL DEFAULT '0' COMMENT '已成交数量(基础币最小单位)',
    `filled_quote_amount` bigint NOT NULL DEFAULT '0' COMMENT '已成交名义值(计价币最小单位,Σ price*qty)',
    `avg_price` bigint NOT NULL DEFAULT '0' COMMENT '平均成交价(计价币最小单位,已成交单量加权)',
    `trade_count` int NOT NULL DEFAULT '0' COMMENT '成交笔数',
    `fee` bigint NOT NULL DEFAULT '0' COMMENT '累计手续费(计价币最小单位,本阶段0)',

    `freeze_request_id` varchar(64) DEFAULT NULL COMMENT '冻结幂等号(asset freeze的requestId)',
    `freeze_quote_amount` bigint NOT NULL DEFAULT '0' COMMENT '已冻结计价币金额(买单=price*quantity或市价预算;最小单位)',
    `freeze_base_amount` bigint NOT NULL DEFAULT '0' COMMENT '已冻结基础币数量(卖单=quantity;最小单位)',

    `status` tinyint NOT NULL DEFAULT '0' COMMENT '状态:0=NEW 1=PARTIAL_FILLED 2=FILLED 3=CANCELLED 4=REJECTED',
    `cancel_time` datetime DEFAULT NULL COMMENT '撤单/结束时间',
    `filled_time` datetime DEFAULT NULL COMMENT '全部成交时间',
    `remark` varchar(255) DEFAULT NULL COMMENT '备注(拒绝/失败原因)',

    -- 系统字段 --
    `create_by` varchar(64) DEFAULT '' COMMENT '创建者',
    `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_by` varchar(64) DEFAULT '' COMMENT '更新者',
    `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `is_deleted` tinyint DEFAULT '0' COMMENT '逻辑删除:0=未删,1=已删',
    `version` int DEFAULT '0' COMMENT '乐观锁版本号',
    `tenant_id` bigint DEFAULT '0' COMMENT '租户ID',

    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_order_no` (`order_no`),
    UNIQUE KEY `uk_client_oid` (`client_oid`),
    KEY `idx_user_time` (`user_id`, `create_time`),
    KEY `idx_symbol_status` (`symbol`, `status`),
    KEY `idx_symbol_side_status` (`symbol`, `side`, `status`),
    KEY `idx_status_time` (`status`, `create_time`),
    KEY `idx_freeze_req` (`freeze_request_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='订单表';

-- ------------------------------------------------------------
-- 3. 成交表 t_trade
--    taker=吃单(主动方,触发撮合),maker=挂单(被动方,订单簿中)
--    币种流向固定:买单方付计价币(quote)收基础币(base),卖单方反之;
--    每笔成交需 2 笔 asset 过户(计价币 + 基础币),见 docs/order-domain.md §4.3。
--    settle_status: 0=待结算 1=已结算 2=结算失败待补偿
-- ------------------------------------------------------------
CREATE TABLE `t_trade` (
    `id` bigint NOT NULL COMMENT '成交ID',

    `trade_no` varchar(64) NOT NULL COMMENT '成交单号(全局唯一)',
    `symbol` varchar(32) NOT NULL COMMENT '交易对',
    `price` bigint NOT NULL COMMENT '成交价(计价币最小单位)',
    `quantity` bigint NOT NULL COMMENT '成交量(基础币最小单位)',
    `quote_amount` bigint NOT NULL COMMENT '成交名义值=price*quantity(计价币最小单位)',

    `taker_order_no` varchar(64) NOT NULL COMMENT '吃单订单号',
    `maker_order_no` varchar(64) NOT NULL COMMENT '挂单订单号',
    `taker_order_id` bigint NOT NULL COMMENT '吃单订单ID',
    `maker_order_id` bigint NOT NULL COMMENT '挂单订单ID',
    `taker_user_id` bigint NOT NULL COMMENT '吃单用户ID',
    `maker_user_id` bigint NOT NULL COMMENT '挂单用户ID',
    `taker_side` tinyint NOT NULL COMMENT '吃单方向:1=BUY 2=SELL',
    `buy_user_id` bigint NOT NULL COMMENT '买方用户ID(冗余)',
    `sell_user_id` bigint NOT NULL COMMENT '卖方用户ID(冗余)',

    `taker_fee` bigint NOT NULL DEFAULT '0' COMMENT '吃单手续费(计价币最小单位,本阶段0)',
    `maker_fee` bigint NOT NULL DEFAULT '0' COMMENT '挂单手续费(计价币最小单位,本阶段0)',

    `settle_status` tinyint NOT NULL DEFAULT '0' COMMENT '结算状态:0=待结算 1=已结算 2=结算失败待补偿',
    `settle_quote_request_id` varchar(64) DEFAULT NULL COMMENT '计价币过户幂等号(tradeNo:Q)',
    `settle_base_request_id` varchar(64) DEFAULT NULL COMMENT '基础币过户幂等号(tradeNo:B)',
    `trade_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '成交时间',

    -- 系统字段 --
    `create_by` varchar(64) DEFAULT '' COMMENT '创建者',
    `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_by` varchar(64) DEFAULT '' COMMENT '更新者',
    `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `is_deleted` tinyint DEFAULT '0' COMMENT '逻辑删除:0=未删,1=已删',
    `version` int DEFAULT '0' COMMENT '乐观锁版本号',
    `tenant_id` bigint DEFAULT '0' COMMENT '租户ID',

    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_trade_no` (`trade_no`),
    KEY `idx_symbol_time` (`symbol`, `trade_time`),
    KEY `idx_taker_order` (`taker_order_id`),
    KEY `idx_maker_order` (`maker_order_id`),
    KEY `idx_symbol_settle` (`symbol`, `settle_status`),
    KEY `idx_buy_user` (`buy_user_id`),
    KEY `idx_sell_user` (`sell_user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='成交表';
