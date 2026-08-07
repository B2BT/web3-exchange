# 交易 API 密钥管理（OpenAPI）契约 — Phase 3.1

> 作者：PM · 落点：exchange-user（端口 8101，用户级安全凭证同域）
> 定位：对标主流交易所（Binance/OKX）的用户级 OpenAPI 密钥体系

## 一、目标功能
- 用户创建**一对 API 密钥**：`apiKey`（公钥标识，用于查询）+ `secretKey`（私钥，用于签名，**明文仅展示一次**）
- 密钥绑定权限标签（只读 / 交易 / 提现），提现权限默认关闭（安全）
- 支持创建 / 列表（明文 Secret 脱敏）/ 删除
- 密钥用于程序化交易鉴权（签名校验在 gateway，见下述扩展）

## 二、表设计 `t_api_key`
| 列 | 类型 | 说明 |
|----|------|------|
| id | bigint PK | 雪花 |
| user_id | bigint | 所属用户 |
| api_key | varchar(64) | 公钥标识（唯一索引） |
| secret_key | varchar(128) | 私钥密文（AES-GCM 加密存储） |
| permission | varchar(32) | 权限：READ / TRADE / WITHDRAW（逗号分隔可组合） |
| status | tinyint | 1=启用 0=停用 |
| last_used_at | datetime | 最近使用 |
| 常规 | | create_by/time/update_time/is_deleted/version |

## 三、接口（网关 /api/user/**）
| 方法 | 路径 | 说明 |
|------|------|------|
| POST | /api/user/api-keys/create | 创建（body: {label, permission}），返回 {apiKey, secretKey明文, id} |
| GET | /api/user/api-keys/list | 列表（secretKey 脱敏为 ***） |
| POST | /api/user/api-keys/{id}/delete | 删除 |
| POST | /api/user/api-keys/{id}/toggle | 启用/停用 |

## 四、安全设计
- `secretKey` 生成：`Base64(32随机字节)`，仅创建时明文返回一次，落库用 AES-GCM 加密（复用 self-wallet 加密工具模式）
- `apiKey` 生成：`前缀(W3-)+UUID去横线`，全局唯一
- 权限校验：提现/交易权限默认不勾选，仅当用户明确选择才授予
- 落库 secretKey 一律密文，列表/详情不回传明文

## 五、签名校验（OpenAPI 请求鉴权，扩展）
程序化请求：header 带 `X-API-KEY` + `X-SIGNATURE`(HMAC-SHA256(secretKey, timestamp+method+path+query)) + `X-TIMESTAMP`。
网关新增 OpenApiFilter：解析 apiKey → 查 t_api_key → 校验签名 → 注入 X-User-Id → 放行。
> 本阶段先落地密钥 CRUD + 密钥体系；签名鉴权 filter 在 P3.5（合约）前接入网关。

## 六、里程碑
- [ ] 契约（本文档）
- [ ] 建表 + 后端 CRUD（加密存储/脱敏/权限）
- [ ] 前端「API 管理」页（用户中心）
- [ ] 实测（创建/明文一次/脱敏/删除）+ git 提交
