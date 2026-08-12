# ELK 日志收集系统

> 为 web3-exchange 微服务集成 ELK（Elasticsearch + Filebeat + Kibana）日志收集，实现多服务日志集中存储与检索。

## 架构

```
各微服务(Logback) → 结构化JSON日志 → /Users/yongzx/logs/<service>/app.log
        │  logstash-logback-encoder
        ▼
Filebeat(采集JSON日志文件) → Elasticsearch(存储+索引) → Kibana(可视化检索)
```

## 组件

| 组件 | 镜像 | 端口 | 说明 |
|------|------|------|------|
| Elasticsearch | 8.13.4 | 9200 | 日志存储+全文索引（单节点，无安全） |
| Kibana | 8.13.4 | 5601 | 日志可视化/检索/看板 |
| Filebeat | 8.13.4 | - | 采集各服务 JSON 日志文件 → ES |

## 微服务侧改造

- `exchange-common` 加入 `logstash-logback-encoder` 依赖（7.4）
- 提供统一 `logback-spring.xml`（所有服务共享）：
  - 控制台 + 文件双输出，结构化 JSON
  - 字段：`@timestamp`/`message`/`level`/`logger_name`/`thread_name`/`serviceName`
  - 文件：`/logs/<service>/app.log`（滚动，按天，单文件 100MB，保留 30 天）
  - `serviceName` 区分服务（避免与 filebeat ECS 模板的 `service` object 冲突）

## 启动

```bash
# ELK（Docker，在 dev-env 下）
docker compose up -d elasticsearch kibana filebeat
# 或单独
docker run -d --name dev-elasticsearch --network dev-env_dev-net \
  -e discovery.type=single-node -e xpack.security.enabled=false \
  -p 9200:9200 docker.elastic.co/elasticsearch/elasticsearch:8.13.4

docker run -d --name dev-kibana --network dev-env_dev-net \
  -e ELASTICSEARCH_HOSTS=http://dev-elasticsearch:9200 \
  -e XPACK_SECURITY_ENABLED=false \
  -p 5601:5601 docker.elastic.co/kibana/kibana:8.13.4

# 微服务（LOG_PATH 写日志文件）
LOG_PATH=/Users/yongzx/logs java -jar exchange-order/target/exchange-order-1.0.0.jar
```

> ⚠️ macOS Docker Desktop 只共享 `/Users` 下目录，日志目录用 `~/logs`，filebeat 挂载 `~/logs:/logs`。

## 验证

```bash
# 查看 ES 日志总量
curl "http://localhost:9200/.ds-web3-logs-*/_count"
# 按服务查询
curl "http://localhost:9200/.ds-web3-logs-*/_search" -d '{"query":{"query_string":{"query":"serviceName:exchange-order"}}}'
# 按级别查询
curl "http://localhost:9200/.ds-web3-logs-*/_search" -d '{"query":{"query_string":{"query":"level:ERROR"}}}'
```

## 已验证

- ✅ 14 个微服务全部输出结构化 JSON 日志到文件
- ✅ Filebeat 采集 → ES 存储（4672+ 条日志）
- ✅ 按 serviceName / level / message 可检索
- ⚠️ Kibana 首次访问需在浏览器 http://localhost:5601 完成配置（创建 data view `web3-logs-*`）

## Kibana 配置（Data View）

Kibana 8.x 在 ES 无安全时必须设 `XPACK_SECURITY_ENABLED=false` 才能跳过 interactive setup。就绪后运行脚本创建 data view：

```bash
bash scripts/setup-kibana.sh
# 或手动: Kibana → Management → Data Views → Create → 输入 web3-logs-* → 时间字段 @timestamp
```

创建后打开 **Discover** 即可检索所有微服务日志（按 serviceName/level/关键字筛选）。

## 排坑记录

1. **`service` 字段冲突**：logstash 输出 `service` 字段与 filebeat ECS 模板（object 类型）冲突 → ES 400 拒绝。改用 `serviceName`。
2. **`/logs` 路径**：macOS Docker 不共享 `/logs`，改用 `~/logs`。
3. **旧 jar**：部分服务 jar 未含新 logback 配置 → `clean package` 重打包重启。
