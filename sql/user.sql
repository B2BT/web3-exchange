CREATE TABLE `t_user` (
                          `id` bigint NOT NULL COMMENT '用户ID',

    -- 账户信息 --
                          `username` varchar(50) NOT NULL COMMENT '用户名',
                          `password` varchar(255) NOT NULL COMMENT '密码',
                          `email` varchar(100) DEFAULT NULL COMMENT '邮箱',
                          `phone` varchar(20) DEFAULT NULL COMMENT '手机号',
                          `nickname` varchar(50) DEFAULT NULL COMMENT '昵称',
                          `real_name` varchar(50) DEFAULT NULL COMMENT '真实姓名',
                          `avatar` varchar(500) DEFAULT NULL COMMENT '头像URL',

    -- 账户状态 --
                          `status` tinyint DEFAULT '1' COMMENT '账户状态:0=禁用,1=正常,2=锁定,3=冻结',
                          `account_non_expired` tinyint(1) DEFAULT '1' COMMENT '账户是否过期',
                          `account_non_locked` tinyint(1) DEFAULT '1' COMMENT '账户是否锁定',
                          `credentials_non_expired` tinyint(1) DEFAULT '1' COMMENT '凭证是否过期',
                          `enabled` tinyint(1) DEFAULT '1' COMMENT '是否启用',

    -- 安全信息 --
                          `last_login_time` datetime DEFAULT NULL COMMENT '最后登录时间',
                          `last_login_ip` varchar(50) DEFAULT NULL COMMENT '最后登录IP',
                          `login_fail_count` int DEFAULT '0' COMMENT '连续登录失败次数',
                          `lock_until` datetime DEFAULT NULL COMMENT '锁定截止时间',
                          `password_update_time` datetime DEFAULT NULL COMMENT '密码最后修改时间',
                          `password_expire_time` datetime DEFAULT NULL COMMENT '密码过期时间',
                          `secret_key` varchar(100) DEFAULT NULL COMMENT 'API密钥/谷歌验证密钥',
                          `two_factor_enabled` tinyint(1) DEFAULT '0' COMMENT '是否开启双因素认证',
                          `two_factor_type` varchar(20) DEFAULT NULL COMMENT '双因素类型:google/sms/email',

    -- 用户级别 --
                          `user_level` varchar(20) DEFAULT 'NORMAL' COMMENT '用户等级:NORMAL/VIP/SVIP',
                          `invite_code` varchar(20) DEFAULT NULL COMMENT '邀请码',
                          `invited_by` bigint DEFAULT NULL COMMENT '邀请人ID',
                          `register_source` varchar(50) DEFAULT NULL COMMENT '注册来源:web/app/API',
                          `register_ip` varchar(50) DEFAULT NULL COMMENT '注册IP',

    -- KYC认证 --
                          `kyc_status` tinyint DEFAULT '0' COMMENT 'KYC状态:0=未认证,1=审核中,2=已认证,3=拒绝',
                          `kyc_level` tinyint DEFAULT '0' COMMENT 'KYC等级:0=未认证,1=L1,2=L2,3=L3',
                          `id_card_type` varchar(20) DEFAULT NULL COMMENT '证件类型:ID_CARD/PASSPORT',
                          `id_card_no` varchar(50) DEFAULT NULL COMMENT '证件号码',
                          `id_card_front` varchar(500) DEFAULT NULL COMMENT '证件正面照',
                          `id_card_back` varchar(500) DEFAULT NULL COMMENT '证件背面照',
                          `kyc_verify_time` datetime DEFAULT NULL COMMENT 'KYC认证时间',

    -- 钱包信息 --
                          `wallet_address` varchar(255) DEFAULT NULL COMMENT '钱包地址',
                          `wallet_type` varchar(20) DEFAULT NULL COMMENT '钱包类型:METAMASK/TP',
                          `wallet_verified` tinyint(1) DEFAULT '0' COMMENT '钱包是否验证',

    -- 系统字段 --
                          `create_by` varchar(64) DEFAULT '' COMMENT '创建者',
                          `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                          `update_by` varchar(64) DEFAULT '' COMMENT '更新者',
                          `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
                          `is_deleted` tinyint DEFAULT '0' COMMENT '逻辑删除:0=未删,1=已删',
                          `version` int DEFAULT '0' COMMENT '乐观锁版本号',
                          `tenant_id` bigint DEFAULT '0' COMMENT '租户ID',

                          PRIMARY KEY (`id`),
                          UNIQUE KEY `uk_username` (`username`),
                          UNIQUE KEY `uk_email` (`email`),
                          UNIQUE KEY `uk_phone` (`phone`),
                          UNIQUE KEY `uk_invite_code` (`invite_code`),
                          UNIQUE KEY `uk_wallet_address` (`wallet_address`),
                          KEY `idx_status` (`status`),
                          KEY `idx_create_time` (`create_time`),
                          KEY `idx_user_level` (`user_level`),
                          KEY `idx_kyc_status` (`kyc_status`),
                          KEY `idx_invited_by` (`invited_by`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户表';

CREATE TABLE `t_role` (
                          `id` bigint NOT NULL COMMENT '角色ID',
                          `role_code` varchar(50) NOT NULL COMMENT '角色编码',
                          `role_name` varchar(50) NOT NULL COMMENT '角色名称',
                          `description` varchar(200) DEFAULT NULL COMMENT '描述',
                          `data_scope` tinyint DEFAULT '1' COMMENT '数据权限范围:1=全部,2=本部门,3=本部门及子部门,4=仅本人',
                          `role_type` varchar(20) DEFAULT 'SYSTEM' COMMENT '角色类型:SYSTEM=系统角色,CUSTOM=自定义',
                          `is_system` tinyint(1) DEFAULT '0' COMMENT '是否系统内置',
                          `sort` int DEFAULT '0' COMMENT '排序',
                          `status` tinyint DEFAULT '1' COMMENT '状态:0=禁用,1=正常',

    -- 系统字段 --
                          `create_by` varchar(64) DEFAULT '' COMMENT '创建者',
                          `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                          `update_by` varchar(64) DEFAULT '' COMMENT '更新者',
                          `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
                          `is_deleted` tinyint DEFAULT '0' COMMENT '逻辑删除:0=未删,1=已删',
                          `version` int DEFAULT '0' COMMENT '乐观锁版本号',
                          `tenant_id` bigint DEFAULT '0' COMMENT '租户ID',

                          PRIMARY KEY (`id`),
                          UNIQUE KEY `uk_role_code` (`role_code`),
                          KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='角色表';

CREATE TABLE `t_permission` (
                                `id` bigint NOT NULL COMMENT '权限ID',
                                `parent_id` bigint DEFAULT '0' COMMENT '父权限ID',
                                `perm_code` varchar(100) NOT NULL COMMENT '权限编码',
                                `perm_name` varchar(50) NOT NULL COMMENT '权限名称',
                                `perm_type` varchar(20) DEFAULT 'MENU' COMMENT '权限类型:MENU=菜单,BUTTON=按钮,API=接口',
                                `url` varchar(500) DEFAULT NULL COMMENT '请求URL',
                                `method` varchar(10) DEFAULT NULL COMMENT '请求方法:GET,POST,PUT,DELETE',
                                `icon` varchar(100) DEFAULT NULL COMMENT '图标',
                                `component` varchar(200) DEFAULT NULL COMMENT '前端组件',
                                `path` varchar(200) DEFAULT NULL COMMENT '路由路径',
                                `redirect` varchar(200) DEFAULT NULL COMMENT '重定向路径',
                                `is_external` tinyint(1) DEFAULT '0' COMMENT '是否外链',
                                `is_cache` tinyint(1) DEFAULT '0' COMMENT '是否缓存',
                                `is_visible` tinyint(1) DEFAULT '1' COMMENT '是否显示',
                                `permission` varchar(200) DEFAULT NULL COMMENT '权限标识',
                                `description` varchar(200) DEFAULT NULL COMMENT '描述',
                                `sort` int DEFAULT '0' COMMENT '排序',
                                `status` tinyint DEFAULT '1' COMMENT '状态:0=禁用,1=正常',

    -- 系统字段 --
                                `create_by` varchar(64) DEFAULT '' COMMENT '创建者',
                                `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                                `update_by` varchar(64) DEFAULT '' COMMENT '更新者',
                                `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
                                `is_deleted` tinyint DEFAULT '0' COMMENT '逻辑删除:0=未删,1=已删',
                                `version` int DEFAULT '0' COMMENT '乐观锁版本号',
                                `tenant_id` bigint DEFAULT '0' COMMENT '租户ID',

                                PRIMARY KEY (`id`),
                                UNIQUE KEY `uk_perm_code` (`perm_code`),
                                KEY `idx_parent_id` (`parent_id`),
                                KEY `idx_perm_type` (`perm_type`),
                                KEY `idx_sort` (`sort`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='权限表';

CREATE TABLE `t_user_role` (
                               `id` bigint NOT NULL COMMENT '主键ID',
                               `user_id` bigint NOT NULL COMMENT '用户ID',
                               `role_id` bigint NOT NULL COMMENT '角色ID',
                               `is_default` tinyint(1) DEFAULT '0' COMMENT '是否默认角色',
                               `expire_time` datetime DEFAULT NULL COMMENT '过期时间',
                               `status` tinyint DEFAULT '1' COMMENT '状态:0=禁用,1=正常',

    -- 系统字段 --
                               `create_by` varchar(64) DEFAULT '' COMMENT '创建者',
                               `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                               `update_by` varchar(64) DEFAULT '' COMMENT '更新者',
                               `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
                               `is_deleted` tinyint DEFAULT '0' COMMENT '逻辑删除:0=未删,1=已删',
                               `version` int DEFAULT '0' COMMENT '乐观锁版本号',
                               `tenant_id` bigint DEFAULT '0' COMMENT '租户ID',

                               PRIMARY KEY (`id`),
                               UNIQUE KEY `uk_user_role` (`user_id`, `role_id`),
                               KEY `idx_user_id` (`user_id`),
                               KEY `idx_role_id` (`role_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户角色关联表';

CREATE TABLE `t_role_permission` (
                                     `id` bigint NOT NULL COMMENT '主键ID',
                                     `role_id` bigint NOT NULL COMMENT '角色ID',
                                     `permission_id` bigint NOT NULL COMMENT '权限ID',
                                     `data_scope` varchar(20) DEFAULT NULL COMMENT '数据权限',

    -- 系统字段 --
                                     `create_by` varchar(64) DEFAULT '' COMMENT '创建者',
                                     `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                                     `update_by` varchar(64) DEFAULT '' COMMENT '更新者',
                                     `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
                                     `is_deleted` tinyint DEFAULT '0' COMMENT '逻辑删除:0=未删,1=已删',
                                     `version` int DEFAULT '0' COMMENT '乐观锁版本号',
                                     `tenant_id` bigint DEFAULT '0' COMMENT '租户ID',

                                     PRIMARY KEY (`id`),
                                     UNIQUE KEY `uk_role_permission` (`role_id`, `permission_id`),
                                     KEY `idx_role_id` (`role_id`),
                                     KEY `idx_permission_id` (`permission_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='角色权限关联表';


CREATE TABLE `t_dept` (
                          `id` bigint NOT NULL COMMENT '部门ID',
                          `parent_id` bigint DEFAULT '0' COMMENT '父部门ID',
                          `dept_code` varchar(50) NOT NULL COMMENT '部门编码',
                          `dept_name` varchar(50) NOT NULL COMMENT '部门名称',
                          `leader_id` bigint DEFAULT NULL COMMENT '负责人ID',
                          `leader_name` varchar(50) DEFAULT NULL COMMENT '负责人姓名',
                          `phone` varchar(20) DEFAULT NULL COMMENT '联系电话',
                          `email` varchar(100) DEFAULT NULL COMMENT '邮箱',
                          `ancestors` varchar(500) DEFAULT NULL COMMENT '祖级列表',
                          `order_num` int DEFAULT '0' COMMENT '显示顺序',
                          `status` tinyint DEFAULT '1' COMMENT '状态:0=禁用,1=正常',

    -- 系统字段 --
                          `create_by` varchar(64) DEFAULT '' COMMENT '创建者',
                          `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                          `update_by` varchar(64) DEFAULT '' COMMENT '更新者',
                          `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
                          `is_deleted` tinyint DEFAULT '0' COMMENT '逻辑删除:0=未删,1=已删',
                          `version` int DEFAULT '0' COMMENT '乐观锁版本号',
                          `tenant_id` bigint DEFAULT '0' COMMENT '租户ID',

                          PRIMARY KEY (`id`),
                          UNIQUE KEY `uk_dept_code` (`dept_code`),
                          KEY `idx_parent_id` (`parent_id`),
                          KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='部门表';

CREATE TABLE `t_post` (
                          `id` bigint NOT NULL COMMENT '岗位ID',
                          `post_code` varchar(50) NOT NULL COMMENT '岗位编码',
                          `post_name` varchar(50) NOT NULL COMMENT '岗位名称',
                          `description` varchar(200) DEFAULT NULL COMMENT '描述',
                          `order_num` int DEFAULT '0' COMMENT '显示顺序',
                          `status` tinyint DEFAULT '1' COMMENT '状态:0=禁用,1=正常',

    -- 系统字段 --
                          `create_by` varchar(64) DEFAULT '' COMMENT '创建者',
                          `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                          `update_by` varchar(64) DEFAULT '' COMMENT '更新者',
                          `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
                          `is_deleted` tinyint DEFAULT '0' COMMENT '逻辑删除:0=未删,1=已删',
                          `version` int DEFAULT '0' COMMENT '乐观锁版本号',
                          `tenant_id` bigint DEFAULT '0' COMMENT '租户ID',

                          PRIMARY KEY (`id`),
                          UNIQUE KEY `uk_post_code` (`post_code`),
                          KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='岗位表';


CREATE TABLE `t_user_dept_post` (
                                    `id` bigint NOT NULL COMMENT '主键ID',
                                    `user_id` bigint NOT NULL COMMENT '用户ID',
                                    `dept_id` bigint NOT NULL COMMENT '部门ID',
                                    `post_id` bigint NOT NULL COMMENT '岗位ID',
                                    `is_primary` tinyint(1) DEFAULT '1' COMMENT '是否主部门',
                                    `status` tinyint DEFAULT '1' COMMENT '状态:0=禁用,1=正常',

    -- 系统字段 --
                                    `create_by` varchar(64) DEFAULT '' COMMENT '创建者',
                                    `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                                    `update_by` varchar(64) DEFAULT '' COMMENT '更新者',
                                    `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
                                    `is_deleted` tinyint DEFAULT '0' COMMENT '逻辑删除:0=未删,1=已删',
                                    `version` int DEFAULT '0' COMMENT '乐观锁版本号',
                                    `tenant_id` bigint DEFAULT '0' COMMENT '租户ID',

                                    PRIMARY KEY (`id`),
                                    UNIQUE KEY `uk_user_dept_post` (`user_id`, `dept_id`, `post_id`),
                                    KEY `idx_user_id` (`user_id`),
                                    KEY `idx_dept_id` (`dept_id`),
                                    KEY `idx_post_id` (`post_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户部门岗位关联表';

CREATE TABLE `t_login_log` (
                               `id` bigint NOT NULL COMMENT '日志ID',
                               `user_id` bigint DEFAULT NULL COMMENT '用户ID',
                               `username` varchar(50) DEFAULT NULL COMMENT '用户名',
                               `login_ip` varchar(50) DEFAULT NULL COMMENT '登录IP',
                               `login_location` varchar(100) DEFAULT NULL COMMENT '登录地点',
                               `browser` varchar(100) DEFAULT NULL COMMENT '浏览器',
                               `os` varchar(100) DEFAULT NULL COMMENT '操作系统',
                               `device_type` varchar(20) DEFAULT NULL COMMENT '设备类型:PC/MOBILE',
                               `login_type` varchar(20) DEFAULT NULL COMMENT '登录类型:PASSWORD/SMS/GOOGLE',
                               `login_status` tinyint DEFAULT '1' COMMENT '登录状态:0=失败,1=成功',
                               `failure_reason` varchar(200) DEFAULT NULL COMMENT '失败原因',
                               `login_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '登录时间',
                               `user_agent` varchar(500) DEFAULT NULL COMMENT '用户代理',
                               `session_id` varchar(100) DEFAULT NULL COMMENT '会话ID',

    -- 系统字段 --
                               `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                               `tenant_id` bigint DEFAULT '0' COMMENT '租户ID',

                               PRIMARY KEY (`id`),
                               KEY `idx_user_id` (`user_id`),
                               KEY `idx_username` (`username`),
                               KEY `idx_login_time` (`login_time`),
                               KEY `idx_login_status` (`login_status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='登录日志表';

CREATE TABLE `t_operation_log` (
                                   `id` bigint NOT NULL COMMENT '日志ID',
                                   `user_id` bigint DEFAULT NULL COMMENT '用户ID',
                                   `username` varchar(50) DEFAULT NULL COMMENT '用户名',
                                   `operation` varchar(100) DEFAULT NULL COMMENT '操作',
                                   `module` varchar(50) DEFAULT NULL COMMENT '模块',
                                   `method` varchar(10) DEFAULT NULL COMMENT '请求方法',
                                   `url` varchar(500) DEFAULT NULL COMMENT '请求URL',
                                   `params` text COMMENT '请求参数',
                                   `result` text COMMENT '返回结果',
                                   `ip` varchar(50) DEFAULT NULL COMMENT 'IP地址',
                                   `location` varchar(100) DEFAULT NULL COMMENT '地址',
                                   `browser` varchar(100) DEFAULT NULL COMMENT '浏览器',
                                   `os` varchar(100) DEFAULT NULL COMMENT '操作系统',
                                   `status` tinyint DEFAULT '1' COMMENT '状态:0=异常,1=成功',
                                   `error_msg` text COMMENT '错误信息',
                                   `execute_time` bigint DEFAULT NULL COMMENT '执行时间(ms)',
                                   `operation_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '操作时间',

    -- 系统字段 --
                                   `create_time` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
                                   `tenant_id` bigint DEFAULT '0' COMMENT '租户ID',

                                   PRIMARY KEY (`id`),
                                   KEY `idx_user_id` (`user_id`),
                                   KEY `idx_operation_time` (`operation_time`),
                                   KEY `idx_module` (`module`),
                                   KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='操作日志表';


-- 初始化数据

-- 系统内置角色
INSERT INTO `t_role` (`id`, `role_code`, `role_name`, `description`, `data_scope`, `role_type`, `is_system`, `sort`, `status`) VALUES                                                                                                                                   (1, 'ROLE_SUPER_ADMIN', '超级管理员', '系统最高权限管理员', 1, 'SYSTEM', 1, 1, 1),
                                                                                                                                   (2, 'ROLE_ADMIN', '管理员', '系统管理员', 2, 'SYSTEM', 1, 2, 1),
                                                                                                                                   (3, 'ROLE_USER', '普通用户', '普通用户', 4, 'SYSTEM', 1, 3, 1),
                                                                                                                                   (4, 'ROLE_VIP', 'VIP用户', 'VIP用户', 4, 'SYSTEM', 1, 4, 1),
                                                                                                                                   (5, 'ROLE_TRADER', '交易员', '专业交易员', 4, 'SYSTEM', 1, 5, 1);

-- 系统管理权限
INSERT INTO `t_permission` (`id`, `parent_id`, `perm_code`, `perm_name`, `perm_type`, `url`, `method`, `permission`, `sort`, `status`) VALUES
-- 用户管理
(1, 0, 'system:user', '用户管理', 'MENU', NULL, NULL, NULL, 1, 1),
(2, 1, 'system:user:list', '用户查询', 'API', '/api/users', 'GET', 'user:list', 1, 1),
(3, 1, 'system:user:add', '用户新增', 'API', '/api/users', 'POST', 'user:add', 2, 1),
(4, 1, 'system:user:edit', '用户修改', 'API', '/api/users/{id}', 'PUT', 'user:edit', 3, 1),
(5, 1, 'system:user:delete', '用户删除', 'API', '/api/users/{id}', 'DELETE', 'user:delete', 4, 1),

-- 角色管理
(6, 0, 'system:role', '角色管理', 'MENU', NULL, NULL, NULL, 2, 1),
(7, 6, 'system:role:list', '角色查询', 'API', '/api/roles', 'GET', 'role:list', 1, 1),
(8, 6, 'system:role:add', '角色新增', 'API', '/api/roles', 'POST', 'role:add', 2, 1),
(9, 6, 'system:role:edit', '角色修改', 'API', '/api/roles/{id}', 'PUT', 'role:edit', 3, 1),
(10, 6, 'system:role:delete', '角色删除', 'API', '/api/roles/{id}', 'DELETE', 'role:delete', 4, 1),

-- 权限管理
(11, 0, 'system:permission', '权限管理', 'MENU', NULL, NULL, NULL, 3, 1),
(12, 11, 'system:permission:list', '权限查询', 'API', '/api/permissions', 'GET', 'permission:list', 1, 1),
(13, 11, 'system:permission:add', '权限新增', 'API', '/api/permissions', 'POST', 'permission:add', 2, 1),
(14, 11, 'system:permission:edit', '权限修改', 'API', '/api/permissions/{id}', 'PUT', 'permission:edit', 3, 1),
(15, 11, 'system:permission:delete', '权限删除', 'API', '/api/permissions/{id}', 'DELETE', 'permission:delete', 4, 1);


