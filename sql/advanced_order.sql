-- ============================================================
-- 进阶订单类型（Phase 1.2）批次A：订单实体扩展 DDL
-- 库：web3_exchange；表：t_order（ALTER TABLE 增量，兼容旧数据）
-- 落地依据：docs/advanced-orders.md §一
-- 说明：仅新增列并带默认值，不破坏既有列/索引；旧行新字段自动落到默认值。
-- ============================================================

ALTER TABLE `t_order`
    ADD COLUMN `time_in_force` tinyint NOT NULL DEFAULT '0'
        COMMENT '时间策略:0=GTC长期有效 1=IOC立即成交或取消剩余 2=FOK全部成交否则取消 3=PostOnly只挂单不吃单',
    ADD COLUMN `trigger_type` tinyint NOT NULL DEFAULT '0'
        COMMENT '条件单类型:0=非条件单 1=止盈(最新价>=触发价激活) 2=止损(最新价<=触发价激活)',
    ADD COLUMN `trigger_price` bigint NOT NULL DEFAULT '0'
        COMMENT '触发价(计价币最小单位;条件单必填,普通单为0)',
    ADD COLUMN `trigger_status` tinyint NOT NULL DEFAULT '0'
        COMMENT '触发状态:0=待触发 1=已触发(激活为普通单) 2=已取消',
    ADD COLUMN `oco_group` varchar(64) NOT NULL DEFAULT ''
        COMMENT 'OCO关联组号(同组两单一个触发/成交另一个自动取消)';

-- 条件单/触发任务与 OCO 联动查询索引（批次B 触发任务/OCO 使用）
ALTER TABLE `t_order`
    ADD INDEX `idx_trigger_status_symbol` (`trigger_status`, `symbol`),
    ADD INDEX `idx_oco_group` (`oco_group`);
