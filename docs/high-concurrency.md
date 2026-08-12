# 高并发优化实践

> web3-exchange 微服务高并发处理：限流、线程池、连接池、本地缓存、多实例扩展。

## 1. 网关限流（RequestRateLimiter + Redis）

所有业务路由已加 `RequestRateLimiter` 限流（KeyResolver 按客户端 IP，`KeyResolverConfig`）：

| 路由 | replenishRate(/s) | burstCapacity | 说明 |
|------|------|------|------|
| auth | 5 | 10 | 登录/验证码，防爆破 |
| user | 10 | 20 | 用户接口 |
| order | 20 | 40 | 下单/撮合，防刷单 |
| futures | 20 | 40 | 合约下单，防刷单 |
| chain | 20 | 40 | 钱包/充提 |
| asset | 30 | 60 | 资金操作 |
| margin/staking/risk/ticket | 30 | 60 | 借贷/理财/风控/工单 |
| admin | 20 | 40 | 管理后台 |
| market | 200 | 400 | 行情读，高吞吐 |
| notify/monitor | 50 | 100 | 通知/健康 |

**原理**：令牌桶算法，令牌按 replenishRate 匀速补充，burstCapacity 允许突发。超限返回 429。

## 2. Tomcat 线程池调优

核心服务（order/futures/asset/chain/margin/staking/market/admin）已配置：
```yaml
server:
  tomcat:
    threads:
      max: 300        # 最大工作线程（默认200）
      min-spare: 20   # 常驻最小线程
    accept-count: 200 # 等待队列（高峰排队不拒绝）
    max-connections: 10000
    connection-timeout: 10000
```
> ⚠️ **Java 17 不支持完整虚拟线程**（需 Java 21+）。Spring Boot 的 `spring.threads.virtual.enabled` 在 21 才完全生效，故本环境用传统线程池调优。

## 3. DB 连接池调优

- **Druid 服务**（order/asset/chain/margin/staking/market/admin）：`max-active: 20→50`，`initial-size/min-idle: 5→10`
- **HikariCP 服务**（futures）：`maximum-pool-size: 50`，`minimum-idle: 10`

## 4. Caffeine 本地缓存（读多写少）

高频下单路径的**低频变化配置**加本地缓存，减少 DB 查询：
- `SymbolService.requireActive`（order 交易对配置，5 分钟过期，1000 条）
- `FuturesTradeServiceImpl.getContract`（futures 合约配置，5 分钟过期，500 条）

**一致性**：缓存 5 分钟过期自动回源；交易对/合约状态变更后调用 `evict()` 失效。

## 5. 多实例部署（Nacos 负载均衡）

项目已用 Nacos 注册中心 + 网关 `lb://` 负载均衡，**同服务启动多实例即自动横向扩展**：

```bash
# 启动 order 服务第 2 个实例（不同端口）
java -jar exchange-order.jar --server.port=8104
java -jar exchange-order.jar --server.port=8104-2   # 第二实例
```

网关通过 `lb://exchange-order` 在 Nacos 注册的多个实例间**轮询分发**，自动负载均衡 + 故障转移。

### ⚠️ 多实例注意点

| 关注点 | 现状 | 说明 |
|--------|------|------|
| **撮合引擎内存态** | `MatchingEngine` 内存订单簿 | 多实例时订单簿分散，需按 symbol 粘性路由（如网关按 symbol 哈希到固定实例）或改用共享撮合 |
| **Caffeine 本地缓存** | 每实例独立 | 配置缓存可容忍 5 分钟不一致；强一致需用 Redis 分布式缓存 |
| **Redis 幂等** | 全局 Redis | 天然多实例安全（SETNX 全局唯一） |
| **RocketMQ/Kafka 消费** | 消费者组 | 多实例同 group 自动分摊分区，天然并行 |

### 关键：撮合引擎多实例方案

1. **粘性路由**（简单）：网关按 symbol 哈希，同 symbol 请求固定到同一实例 → 订单簿一致。可用自定义 KeyResolver 或 LoadBalancer 策略。
2. **共享撮合**（复杂）：撮合状态外置（Redis/内存网格），多实例共同维护订单簿。
3. **单实例撮合 + 只读多实例**：撮合实例唯一，查询/行情实例可扩展。

**演示环境建议方案 1**：网关 `lb://` + 按 symbol 哈希的 LoadBalancer，最简单且能扩展读能力。

## 验证

- 网关限流：超阈值请求返回 429（`curl` 连续请求验证）
- 线程池/连接池：日志确认 Druid/Hikari 生效
- Caffeine：下单二次调用命中缓存（日志无 DB 查询）
- 多实例：Nacos 控制台可见多实例注册，网关轮询分发
