-- ============================================================
-- 管理平台（Admin）Phase Admin A 脚本
-- 库：web3_exchange；落地依据：docs/admin.md
-- ============================================================

-- 1. t_user 增加 role 字段（USER 普通 / ADMIN 管理员），用于管理平台鉴权
ALTER TABLE t_user ADD COLUMN role varchar(16) NOT NULL DEFAULT 'USER' COMMENT '角色:USER普通/ADMIN管理员';

-- 2. 预置管理员：将现有测试账号 e2e92443 升级为管理员（便于演示管理平台）
UPDATE t_user SET role='ADMIN' WHERE username='e2e92443';
