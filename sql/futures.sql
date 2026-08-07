-- ============================================================
-- Phase 3.5 永续合约建表脚本
-- 库：web3_exchange；模块：exchange-futures（端口 8117）
-- 设计见 docs/futures-domain.md
-- ============================================================

-- 合约交易对
CREATE TABLE IF NOT EXISTS t_swap_contract (
  id BIGINT PRIMARY KEY,
  symbol VARCHAR(32) NOT NULL COMMENT '交易对，如 BTC-USDT-SWAP',
  base VARCHAR(16) NOT NULL,
  quote VARCHAR(16) NOT NULL DEFAULT 'USDT',
  price_decimals INT DEFAULT 8 COMMENT '价格精度(最小单位位数)',
  qty_decimals INT DEFAULT 8 COMMENT '数量精度',
  max_leverage INT DEFAULT 100 COMMENT '最大杠杆',
  mmr INT DEFAULT 5000 COMMENT '维持保证金率(基点,1基点=0.01%,5000=50%)',
  imr INT DEFAULT 10000 COMMENT '初始保证金率(基点,10000=100%/即1x)',
  funding_interval_hours INT DEFAULT 8 COMMENT '资金费率结算周期(小时)',
  max_funding_rate INT DEFAULT 500 COMMENT '资金费率上限(基点/期)',
  status INT DEFAULT 0 COMMENT '0上架 1下架',
  create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_symbol (symbol)
) ENGINE=InnoDB COMMENT='永续合约交易对';

-- 合约账户(每个用户每币种一条,初始 USDT)
CREATE TABLE IF NOT EXISTS t_futures_account (
  id BIGINT PRIMARY KEY,
  user_id BIGINT NOT NULL,
  coin VARCHAR(16) NOT NULL DEFAULT 'USDT',
  margin_balance BIGINT NOT NULL DEFAULT 0 COMMENT '账户余额(最小单位,下同)',
  available_balance BIGINT NOT NULL DEFAULT 0 COMMENT '可用余额',
  position_margin BIGINT NOT NULL DEFAULT 0 COMMENT '占用保证金',
  unrealized_pnl BIGINT NOT NULL DEFAULT 0 COMMENT '未实现盈亏',
  realized_pnl BIGINT NOT NULL DEFAULT 0 COMMENT '累计已实现盈亏',
  version INT DEFAULT 0,
  create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_user_coin (user_id, coin)
) ENGINE=InnoDB COMMENT='永续合约账户';

-- 合约持仓
CREATE TABLE IF NOT EXISTS t_futures_position (
  id BIGINT PRIMARY KEY,
  user_id BIGINT NOT NULL,
  symbol VARCHAR(32) NOT NULL,
  side INT NOT NULL COMMENT '1多 2空',
  size BIGINT NOT NULL DEFAULT 0 COMMENT '持仓数量(最小单位)',
  entry_price BIGINT NOT NULL DEFAULT 0 COMMENT '开仓均价(最小单位)',
  leverage INT NOT NULL DEFAULT 10 COMMENT '当前杠杆',
  isolated_margin BIGINT NOT NULL DEFAULT 0 COMMENT '逐仓保证金',
  liq_price BIGINT NOT NULL DEFAULT 0 COMMENT '强平价格(最小单位)',
  unrealized_pnl BIGINT NOT NULL DEFAULT 0 COMMENT '未实现盈亏',
  realized_pnl BIGINT NOT NULL DEFAULT 0 COMMENT '该仓累计已实现盈亏',
  status INT NOT NULL DEFAULT 0 COMMENT '0持仓中 1已平仓',
  version INT DEFAULT 0,
  create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_user_symbol_side (user_id, symbol, side, status)
) ENGINE=InnoDB COMMENT='永续合约持仓';

-- 资金费率结算记录
CREATE TABLE IF NOT EXISTS t_funding_settle (
  id BIGINT PRIMARY KEY,
  symbol VARCHAR(32) NOT NULL,
  rate INT NOT NULL COMMENT '资金费率(基点)',
  mark_price BIGINT NOT NULL COMMENT '结算时标记价(最小单位)',
  base_price BIGINT NOT NULL COMMENT '基础价/现货价',
  funding_time DATETIME NOT NULL,
  create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
  KEY idx_symbol_time (symbol, funding_time)
) ENGINE=InnoDB COMMENT='资金费率结算';

-- 标记价格(每交易对一条,定时刷新)
CREATE TABLE IF NOT EXISTS t_mark_price (
  id BIGINT PRIMARY KEY,
  symbol VARCHAR(32) NOT NULL,
  mark_price BIGINT NOT NULL COMMENT '标记价(最小单位)',
  update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  UNIQUE KEY uk_symbol (symbol)
) ENGINE=InnoDB COMMENT='合约标记价格';
