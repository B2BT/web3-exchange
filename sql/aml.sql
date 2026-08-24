-- ============================================================
-- AML 黑名单/制裁名单（KYC-AML 合规：身份/证件/地址命中黑名单拦截交易）
-- ============================================================
CREATE TABLE IF NOT EXISTS t_aml_blacklist (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  match_type VARCHAR(20) NOT NULL COMMENT '匹配类型:PERSON_NAME=姓名 PERSON_ID_CARD=身份证 SANCTION_ADDRESS=制裁钱包地址',
  match_value VARCHAR(255) NOT NULL COMMENT '命中值(姓名/证件号/钱包地址)',
  reason VARCHAR(255) DEFAULT NULL COMMENT '命中原因(制裁/欺诈/风险)',
  source VARCHAR(50) DEFAULT 'admin' COMMENT '名单来源',
  status TINYINT NOT NULL DEFAULT 1 COMMENT '1=生效 0=失效',
  create_time DATETIME DEFAULT CURRENT_TIMESTAMP,
  update_time DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  KEY idx_type_value (match_type, match_value(64))
) ENGINE=InnoDB COMMENT='AML反洗钱黑名单/制裁名单';