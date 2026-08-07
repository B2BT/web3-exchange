# 客服工单系统契约 — Phase 3.3

> 作者：PM · 落点：独立模块 `exchange-ticket`（端口 8116）
> 定位：对标主流交易所客服体系（Help Desk / 工单 Ticket），用户提交工单 → 管理员回复 → 用户跟进 → 关闭

## 一、目标功能
- **用户侧**：提交工单（分类/标题/内容）、查看我的工单列表/详情、追加工单回复、关闭工单
- **管理侧**（admin 域复用 /api/admin/ticket/**）：工单列表（按状态筛选）、回复工单、标记处理中/已解决/关闭
- **工单生命周期**：OPEN(0) 待处理 → PROCESSING(1) 处理中 → RESOLVED(2) 已解决 → CLOSED(3) 已关闭

## 二、表设计
### `t_ticket`（工单主表）
| 列 | 类型 | 说明 |
|----|------|------|
| id | bigint PK | 雪花 |
| user_id | bigint | 提交用户 |
| category | varchar(32) | 分类：DEPOSIT充值/WITHDRAW提现/TRADE交易/ACCOUNT账户/OTHER其他 |
| title | varchar(128) | 标题 |
| content | text | 问题描述 |
| status | tinyint | 0开放 1处理中 2已解决 3已关闭 |
| priority | tinyint | 0低 1中 2高 |
| assignee_id | bigint | 处理管理员（可空） |
| resolved_at | datetime | 解决时间 |
| 常规 | | create_by/time/update_time/is_deleted/version |

### `t_ticket_reply`（工单回复）
| 列 | 类型 | 说明 |
|----|------|------|
| id | bigint PK | 雪花 |
| ticket_id | bigint | 工单ID |
| user_id | bigint | 回复人（用户或管理员） |
| is_staff | tinyint | 1=管理员 0=用户 |
| content | text | 回复内容 |
| 常规 | | 时间/逻辑删除 |

## 三、接口
### 用户侧（网关 /api/ticket/**，JWT 鉴权）
| 方法 | 路径 | 说明 |
|------|------|------|
| POST | /api/ticket/create | 提交工单（category/title/content/priority） |
| GET | /api/ticket/list?page=&size=&status= | 我的工单分页 |
| GET | /api/ticket/{id} | 工单详情+回复列表 |
| POST | /api/ticket/{id}/reply | 追加工单回复 |
| POST | /api/ticket/{id}/close | 关闭工单 |

### 管理侧（网关 /api/admin/ticket/**，ADMIN 角色）
| 方法 | 路径 | 说明 |
|------|------|------|
| GET | /api/admin/ticket/list?page=&size=&status= | 全部工单分页 |
| POST | /api/admin/ticket/{id}/reply | 管理员回复（is_staff=1） |
| POST | /api/admin/ticket/{id}/status | 更新状态（处理中/已解决/关闭）+ 指派 |

## 四、里程碑
- [ ] 契约（本文档）+ 建表
- [ ] 后端：exchange-ticket 模块 + 用户/管理接口 + admin 域回复联动
- [ ] 前端：用户「客服工单」页 + admin「工单管理」页
- [ ] 实测（提交→管理员回复→用户跟进→关闭）+ git 提交
