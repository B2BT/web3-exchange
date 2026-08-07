# 风控引擎契约 — Phase 2.4

> 作者：PM · 对标 Binance/OKX 风控体系（MVP 聚焦可落地项）
> 定位：跨域统一风控。新建 `exchange-risk` 模块（端口 **8114**），向 order（下单）/chain（提现）/auth（登录）提供风控校验
> 落地：风险规则配置 + 下单风控（滑点/限额）+ 提现二次验证/反钓鱼码 + 异常登录检测

## 一、目标功能（MVP 范围）
1. **下单风控**：限价滑点上限、单笔金额上限、日累计成交限额 → order 下单前置校验。
2. **提现二次验证 + 反钓鱼码**：提现申请须通过二次验证（邮箱/短信验证码）；反钓鱼码（用户自定义短语）在提现时回显校验。
3. **异常登录检测**：登录记录 IP/设备/UA，检测异地登录、频繁失败、新设备，触发验证或告警。

## 二、后端设计（exchange-risk 模块，端口 8114）

### 2.1 新表
```
t_risk_rule        -- 风控规则
  rule_code(UNIQUE), name, rule_type(ORDER_SLIPPAGE/ORDER_AMOUNT/ORDER_DAILY/...),
  scope(GLOBAL/USER), symbol(可空), threshold(阈值, Long 最小单位或bps),
  status(0=停用 1=启用)

t_anti_phishing    -- 反钓鱼码（每用户一条）
  user_id(UNIQUE), phrase(自定义短语), update_time

t_login_log        -- 登录日志（异常登录检测）
  user_id, username, ip, user_agent, device(粗略), result(0=成功 1=失败),
  risk(0=正常 1=异常), create_time

t_withdraw_verify  -- 提现二次验证记录
  user_id, withdraw_id, verify_code_hash, channel(EMAIL/SMS), status(0=待验证 1=已通过),
  expire_time, create_time
```

### 2.2 接口
| 方法 | 接口 | 说明 |
|------|------|------|
| GET  | /api/risk/rules | 规则列表（用户可看通用规则） |
| POST | /api/risk/phishing/set | 设置反钓鱼码 {userId, phrase} |
| GET  | /api/risk/phishing | 查询反钓鱼码（提现时回显） |
| GET  | /api/risk/login-logs | 我的登录日志分页 |

**内部接口（/internal/risk/，Feign 调用，不对外）**
| POST | /internal/risk/order/precheck | 下单前置：滑点/单笔/日累计，返回 pass 或拦截原因 |
| POST | /internal/risk/withdraw/precheck | 提现前置：反钓鱼码校验 + 生成二次验证码 |
| POST | /internal/risk/withdraw/verify | 提现二次验证码校验 |
| POST | /internal/risk/login/record | 登录结果记录（成功/失败/异地） |
| GET  | /internal/risk/login/abnormal | 判断本次登录是否异常（新IP/新设备） |

### 2.3 规则默认值（种子）
- ORDER_SLIPPAGE：市价单相对盘口最优价滑点上限，GLOBAL，500bps=5%
- ORDER_AMOUNT：单笔下单金额上限，GLOBAL，默认不限(0)
- ORDER_DAILY：日累计成交额上限，GLOBAL，默认不限(0)

### 2.4 接入点（改动现有模块）
- **order**：`OrderService.placeOrder` 最前调用 `/internal/risk/order/precheck`，拦截超限单。
- **chain**：`WithdrawService.apply` 调用 `/internal/risk/withdraw/precheck`（校验反钓鱼码 + 生成二次验证码），提现进入「待二次验证」；`verify` 通过后进入正常审核流程。
- **auth**：`AuthService.login` 成功/失败调用 `/internal/risk/login/record`，异地登录记录 risk=1。

## 三、前端（/risk 页或并入用户中心）
- 反钓鱼码设置/查看
- 我的登录日志列表
- （管理端在 P2.5 规则管理）

## 四、测试（api_test.py 增 risk 用例）
- 反钓鱼码设置/查询、下单滑点超限拦截、提现二次验证、登录日志记录
- 报告 docs/test-reports/
