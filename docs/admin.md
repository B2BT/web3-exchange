# 管理平台（Admin）契约 — Phase Admin

> 作者：PM · 新增 exchange-admin 域（第 10 个域，端口 8109）
> 目的：对标主流交易所运营后台，提供用户/订单/提现/资产/公告/监控管理
> 分阶段：**Admin A（本批）**管理员体系+用户+订单+提现审核+资产汇总；**Admin B** 公告+监控+风控

## 一、管理员体系
- **t_user 加 `role` VARCHAR(16) 默认 'USER'**（'USER' 普通 / 'ADMIN' 管理员）。
- DDL：`sql/admin.sql`：`ALTER TABLE t_user ADD COLUMN role varchar(16) NOT NULL DEFAULT 'USER' COMMENT '角色:USER普通/ADMIN管理员';`
- 预置管理员：`UPDATE t_user SET role='ADMIN' WHERE username='e2e92443';`（现有测试账号升级为管理员，便于演示）。
- auth 域登录返回 userInfo 增加 `role` 字段（UserInfoResponse 加 role）。
- admin 域接口鉴权：校验 JWT 用户 role=ADMIN（可从网关 X-User-Id 查 t_user 或 JWT 带 role）。

## 二、接口（/api/admin/**，需 ADMIN 角色）
网关路由 `/api/admin/**` → `lb://exchange-admin`。admin 域连同一 mysql 直接查各表（t_user/t_order/t_withdraw/t_asset_ledger/t_asset_account）。

1. **用户管理**
   - `GET /api/admin/user/list?page=1&size=20&keyword=` → 分页用户（id/username/email/phone/role/status/registerTime），keyword 模糊匹配 username/phone。
   - `POST /api/admin/user/{id}/ban` → 封禁（status=2 DISABLED，不可登录）。
   - `POST /api/admin/user/{id}/unban` → 解封（status=1）。
2. **订单管理**
   - `GET /api/admin/order/list?page=1&size=20&symbol=&status=` → 全站订单分页（跨用户，OrderVO 同款字段）。
3. **提现审核**（对接 chain 域 WithdrawService.audit 逻辑；admin 域可直接调 chain 的 audit 接口或复制审计逻辑）
   - `GET /api/admin/withdraw/list?page=1&size=20&status=` → 提现申请分页。
   - `POST /api/admin/withdraw/{id}/audit` body `{"approved":true,"remark":"..."}` → 审核（复用 chain 审核 + 资产扣减/解冻）。
4. **资产汇总**
   - `GET /api/admin/asset/summary` → 各币种全站总余额/冻结（聚合 t_asset_account）：`[{symbol, totalAvailable, totalFrozen}]`。
5. **Admin B（下批）**：公告 CRUD、服务监控、风控/限额、管理员审计日志。

## 三、验证
- mvn 编译 exchange-admin -am BUILD SUCCESS；curl 经网关带 ADMIN token 调各接口返回正确。
- 普通用户 token 调 /api/admin/** 应被拒（403/401）。

## 四、交付
- exchange-admin 模块（pom/application.yml/nacos 注册/启动类/Controller/Service/Mapper/entity/VO）。
- gateway application.yml 加 `/api/admin/**` 路由；AuthFilter 对 admin 接口校验 role（或 admin 域内校验）。
- 网关路由需重启 gateway；admin 域启动后测试。
