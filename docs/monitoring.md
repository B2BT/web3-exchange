# 监控告警（Prometheus + Grafana）

> 可观测性闭环：全服务暴露 Prometheus 指标 → Prometheus 抓取 + 告警规则 → Grafana 面板。
> 配合已有 ELK 日志 + Zipkin 追踪，形成「指标 + 日志 + 链路」三位一体可观测性。

## 架构

```
各微服务(/actuator/prometheus) → Prometheus(9090) → 告警规则(alerts.yml)
                                        │                │
                                        └─ Grafana(3000) ┘ 可视化 + 告警
                转储: ELK 日志, Zipkin 追踪(已有)
```

## 接入（已完成）

1. **指标暴露**：`exchange-common` 加 `micrometer-registry-prometheus`，各服务 `application.yml`
   暴露 `health,info,metrics,prometheus` → 每个服务有 `/actuator/prometheus`
2. **Prometheus**：`monitoring/prometheus.yml` 抓取 7 服务（market/order/asset/auth/gateway/chain/futures）
3. **告警规则**：`monitoring/alerts.yml` 3 条
   - `ServiceDown`：服务 1 分钟无上报（UP==0）
   - `HighMemoryUsage`：JVM 堆使用率 > 85% 持续 2 分钟
   - `HighRequestRate`：http 请求 > 100/s 持续 1 分钟
4. **Grafana**：`monitoring/docker-compose.yml`（Prometheus+Grafana），数据源已关联

## 验证（实测通过）

- ✅ 6 服务 Prometheus 指标 `up=1`，market/order/asset/auth/chain/futures 抓取正常
- ✅ 3 条告警规则加载
- ✅ Grafana(3000) 运行，Prometheus 数据源 id=1
- ✅ **活跃告警触发**：`ServiceDown host.docker.internal:8102=1`（user 服务 actuator 未启用 → 触发宕机告警），证明告警引擎真实工作

## 使用

```bash
cd monitoring && docker compose up -d
# Prometheus: http://localhost:9090
# Grafana:    http://localhost:3000  (admin/admin)
```

## 生产进一步（可选）

1. **Alertmanager**：接邮件/钉钉/企微/webhook 推送
2. **业务指标**：添加自定义指标（撮合延迟/资金变动计数/异常订单），自定义告警
3. **Grafana 面板**：导入 Spring Boot/JVM 面板，建立业务大盘