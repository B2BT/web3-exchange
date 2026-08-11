# Kafka 行情管道（真实行情流）

> 在 Binance WS 真实行情源与内存聚合器之间插入 Kafka，实现**生产/消费解耦、多消费者、可重放**。

## 架构

```
Binance WebSocket (data-stream.binance.vision)
        │  8币对 × (@ticker + @kline_1m..1d + @depth20)
        ▼
BinanceWsPriceSource ──► 写 Kafka topic ──► MarketKafkaConsumer ──► MarketAggregator
                          (binance-ticker)      (market-kline-group)    (K线/深度/ticker)
                          (binance-kline)       (market-depth-group)
                          (binance-depth)
                                │
                                ├──► 独立消费者组 (如风控/量化分析，随时接入)
                                └──► 重放 (从 topic 头部回溯历史行情)
```

## 设计要点

- **生产端** `BinanceWsPriceSource`：收到 ticker/kline/depth 后，除更新本地聚合器外，`MarketEventProducer` 同步写入 Kafka topic（key=symbol 保证同币顺序）。**失败静默降级**，不阻塞 WS 主链路。
- **消息体** `MarketEvent`：统一封装 ticker/kline/depth 三类事件（type 区分），金额/价格均 Long 最小单位。
- **消费端** `MarketKafkaConsumer`：`@KafkaListener` 从三个 topic 消费，更新 `MarketAggregator`。不同消费者组（market-kline-group / market-depth-group）并行消费。
- **Topic**：每个 topic 4 分区（支持多消费者并行），副本 1（单机）。
- **配置**：`server-settings.kafka.bootstrap-servers`（默认 `localhost:9092`）。

## Kafka 启动方式

### Docker（推荐，已加入 docker-compose）
```bash
cd dev-env && docker compose up -d kafka   # apache/kafka:3.8.0, KRaft 单机, 端口 9092
```
若 Docker Hub 拉取超时，可用镜像加速或改用 brew。

### Homebrew（macOS 备选，本项目实际采用）
```bash
brew install kafka
brew services start kafka   # KRaft 单机, 端口 9092
```

## 验证结果（实测）

| 能力 | 验证 |
|------|------|
| 生产写入 | 三 topic 消息持续增长（ticker/kline/depth） |
| 消费更新 | 经网关查行情 BTC=$64,300（Kafka 消费端更新聚合器） |
| **重放** | `kafka-console-consumer --from-beginning` 读到完整历史行情 |
| **多消费者** | 独立消费者组 `diag-downstream` 从 kline 独立消费，不影响原消费组 |

## 应用场景

- **行情高吞吐解耦**：一个行情源 → 多个下游（K线/深度/合约标记价/前端推送）各自独立消费。
- **横向扩展**：加分区 + 消费者实例即可提升消费吞吐。
- **历史回放**：重启行情模块后从 Kafka 重放 K 线（解决内存聚合重启丢失问题）。
- **审计/分析**：完整行情流留存，可接 Flink/Spark 做量化回测、风控建模。
