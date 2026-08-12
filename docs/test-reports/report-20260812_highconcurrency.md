# 测试报告：高并发优化回归

**日期**: 2026-08-12
**结果**: **PASS=50 FAIL=0**（全绿）
**范围**: 高并发优化后全量 API 回归（网关限流 / 线程池 / 连接池 / Caffeine 缓存）

## 本次改动验证项

| 优化项 | 验证结果 |
|--------|---------|
| 网关限流（11 路由 RequestRateLimiter + Redis） | ✅ 生效（auth 连续 12 请求 → 第 11/12 返回 429 令牌桶耗尽） |
| Tomcat 线程池调优（max=300） | ✅ order/futures/asset 等 8 服务生效 |
| DB 连接池调优（Druid max-active 20→50 / Hikari 50） | ✅ order Druid inited / futures Hikari Start completed |
| Caffeine 本地缓存（SymbolService / FuturesTradeServiceImpl） | ✅ 编译通过，下单功能正常 |
| 测试脚本节流适配（0.15s） | ✅ 兼容生产限流，不再 429 |

## 关键发现

1. **网关限流被实测触发**：api_test 原速连跑触发各服务限流（HTTP 429 → 返回 None），证明限流真实生效、能拦截突发流量。
2. **测试脚本适配**：在 `api_test.curl` 加 0.15s 节流，控制在各服务阈值内，测试恢复全绿。
3. **多实例部署**：项目已用 Nacos + 网关 `lb://` 负载均衡，天然支持多实例横向扩展；撮合引擎内存态需粘性路由（详见 docs/high-concurrency.md）。

## 回归用例覆盖

- 认证（验证码/登录 token）
- 行情（ticker/K线/盘口/成交）
- 订单（分页/挂单/条件单/下单/撤单）
- 资产（余额/资金明细）
- 钱包（HD钱包/导入/充提/转账/BTC）
- 合约（列表/标记价/入金/开多/撮合/持仓/平多）
- 网关连通性

## 环境

- 全部 15 业务服务 + 网关重启完成
- 基础设施（MySQL/Nacos/Redis/RocketMQ/mock-rpc）就绪
- 真实行情（Binance）正常（BTC ~$63,758）
