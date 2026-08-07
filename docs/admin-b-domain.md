# Admin B 契约 — Phase 2.5

> 作者：PM · 在既有 `exchange-admin`（端口 8109，已含 ADMIN 角色拦截器）上扩展
> 定位：管理后台增强 — 公告管理、服务监控、管理员审计日志、交易对管理
> 落地：全部在 exchange-admin 模块新增 controller/service/entity/mapper + 新表

## 一、目标功能（MVP 范围）
1. **公告 CRUD**：运营发布/编辑/上下线/删除平台公告，用户端可查列表。
2. **服务监控**：聚合各微服务健康状态（内存/端口/注册），后台仪表盘展示。
3. **管理员审计日志**：记录后台敏感操作（审核提现/封禁/改公告等），防抵赖。
4. **交易对管理**：新增/编辑/停牌/上牌交易对（t_symbol CRUD）。

## 二、后端设计（exchange-admin 模块扩展）

### 2.1 新表
```
t_announcement   -- 公告
  title, content, type(0=公告 1=活动 2=系统), status(0=草稿 1=已发布 2=已下线),
  publish_time, publisher_id, view_count
  uk_title 唯一

t_admin_audit    -- 管理员审计日志
  admin_user_id, admin_username, action(操作类型), target_type, target_id,
  detail(JSON/文本), ip, create_time

t_service_health -- 服务健康快照(定时采集)
  service_name, instance_ip, port, status(0=DOWN 1=UP), memory_used, memory_total,
  last_heartbeat
```

### 2.2 接口（/api/admin/**，需 ADMIN 角色，拦截器已鉴权）
| 方法 | 接口 | 说明 |
|------|------|------|
| GET  | /api/admin/announcement/list | 公告分页 |
| POST | /api/admin/announcement/create | 新建公告 |
| POST | /api/admin/announcement/update | 编辑公告 |
| POST | /api/admin/announcement/{id}/publish | 发布/下线 |
| POST | /api/admin/announcement/{id}/delete | 删除公告 |
| GET  | /api/admin/health/list | 服务健康列表 |
| GET  | /api/admin/audit/list | 审计日志分页 |
| GET  | /api/admin/symbol/list | 交易对分页 |
| POST | /api/admin/symbol/create | 新增交易对 |
| POST | /api/admin/symbol/update | 编辑交易对 |
| POST | /api/admin/symbol/{id}/toggle | 上牌/停牌 |

### 2.3 审计记录触发
- 公告创建/编辑/发布/删除、交易对变更、提现审核（复用 AuditRequest 处）均写 t_admin_audit。
- 管理员身份取 X-User-Id 头 → t_user 查 username。

### 2.4 服务监控数据源
- 定时任务扫描 t_service_health 表，经各服务注册信息更新心跳；健康状态由各服务自身上报或监控探活（MVP：静态表 + 手动上报接口 /internal/admin/health/report 由各服务 Feign 调）。

## 三、前端（admin 后台导航 + 4 页）
- 公告管理页、服务监控页、审计日志页、交易对管理页
- 复用既有 /admin 布局与路由守卫

## 四、测试（api_test.py 增 admin 用例）
- 公告 CRUD + 发布、交易对新增/停牌、审计日志记录、健康上报
- 报告 docs/test-reports/
