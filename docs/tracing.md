# 分布式链路追踪（Micrometer Tracing + Brave + Zipkin）

> 为 web3-exchange 微服务接入分布式链路追踪，实现跨服务调用链的 traceId 串联与日志关联，配合 ELK 排查调用链。

## 架构

```
客户端 → Gateway(创建traceId) → 微服务A → Feign → 微服务B
              │                         │            │
              └──────── traceId 通过 X-B3 头传播 ──────┘
                                                    │
                                    每次调用产生 span，上报
                                                    ▼
                                            Zipkin(9411)
```

- **traceId**：一次完整请求贯穿所有服务的唯一标识
- **span**：每次跨服务调用/内部操作的耗时单元，通过 parentId 关联成树
- **日志关联**：traceId 写入 MDC → logback JSON 日志带 `traceId` 字段 → ELK 可检索

## 依赖（exchange-common 统一，所有服务共享）

- `micrometer-tracing-bridge-brave`：Micrometer Tracing 的 Brave 实现
- `zipkin-reporter-brave` + `zipkin-sender-okhttp3`：上报 Zipkin
- `spring-boot-starter-actuator`：⚠️ 必需，Tracing 自动配置的前置条件
- `feign-micrometer`：⚠️ OpenFeign 透传 trace 头必需（否则 Feign 调用独立起 trace）

## 配置

```yaml
# 各服务 application.yml
management:
  tracing:
    sampling:
      probability: 1.0   # 全采样（演示/排查；生产可降如 0.1）
```

Zipkin 默认上报 `http://localhost:9411/api/v2/spans`，无需额外配置。

## 启动 Zipkin

```bash
docker run -d --name dev-zipkin --network dev-env_dev-net -p 9411:9411 openzipkin/zipkin:latest
# UI: http://localhost:9411
```

## 验证

```bash
# 触发请求
curl http://localhost:8080/api/market/ticker/list
# 查 Zipkin 跨服务 trace（应含 gateway + market 多 span）
curl "http://localhost:9411/api/v2/traces?limit=10"
# 日志关联：ES/日志中 traceId 字段
grep traceId /tmp/market.log
```

## 已验证

- ✅ gateway → market/order 跨服务 HTTP trace 串联（多 span，同一 traceId）
- ✅ 各服务 HTTP 请求 / 定时任务产生 trace
- ✅ 日志 JSON 带 `traceId`/`spanId` 字段，与 Zipkin 关联
- ✅ asset 正确接受透传的 X-B3 trace 头
- ✅ 全量回归 50/50 PASS

## 边界说明

- **MQ 异步消费**（如 order→asset 资金结算走 RocketMQ）：异步消息消费者**不共享 HTTP 的 traceId**（新起独立 trace），这是异步解耦的设计使然。如需串联需在 MQ 消息头传 trace 上下文。
- **Feign HTTP 调用**（freeze/transfer）：需 `feign-micrometer` 依赖透传（已配置）。

## 排坑记录

1. **没有 actuator → Tracing 自动配置不生效**：只有定时任务有 trace、HTTP 请求没有 → common 加 actuator。
2. **OpenFeign trace 透传需 `feign-micrometer`**：否则下游 Feign 调用独立起 trace。
3. **gateway(WebFlux) 默认创建 trace**：验证 gateway→下游正常串联，无需额外配置。
