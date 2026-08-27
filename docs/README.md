# Web3-Exchange 文档总索引

> **唯一入口**：先读本页，按序深入。本项目是一个 **Web3 数字资产交易所后端**（Java 17 / Spring Boot 3.2 /
> Spring Cloud Alibaba / Nacos / MyBatis-Plus / MySQL / Redis / RocketMQ / Kafka / web3j），
> 已实现资金安全、现货/永续撮合、链上充提、合规 AML、高并发优化、监控告警、k8s 部署+CI/CD。

## 阅读路径（新读者按序）

```
① 总体认识:     README.md(本文) → docs/feature-guide.md(功能速览)
② 架构蓝图:     docs/ARCHITECTURE.md(拓扑) → docs/common-domain.md(公共基座)
③ 核心交易域:   docs/order-domain.md → docs/asset-domain.md → docs/chain-domain.md → docs/market-domain.md
④ 合约衍生品:   docs/futures-domain.md → docs/margin-domain.md
⑤ 用户/认证:    docs/feature-guide.md §四(用户/认证/网关增强)
⑥ 支撑域:       docs/notify-domain.md → docs/risk-domain.md → docs/staking-domain.md → docs/ticket-domain.md → docs/admin.md
```

---

## 一、项目现状（准确版）

**15 个微服务全部落地并验证**（20+ 轮迭代，全量测试 50/50 PASS）：

| 服务 | 端口 | 能力 |
|------|------|------|
| gateway | 8080 | 路由转发 + JWT 鉴权 + 限流 + CORS + 请求日志 |
| user | 8101 | 注册/资料/KYC 实名/2FA(TOTP)/邀请码/等级 |
| auth | 8102 | 登录/验证码/注册/2FA 校验/改密重置/JWT 签发 |
| asset | 8103 | 钱包账户/余额/冻结/解冻/过户/流水/充值入账 + 资金对账 |
| order | 8104 | 现货下单/撮合(价格-时间优先)/深度/成交/条件单 |
| chain | 8105 | 链上接收充提/扫描器(ERC-20/721/1155)/NFT/冷热钱包 |
| market | 8106 | 行情聚合(Binance/Kafka)/ticker/kline/WebSocket 推送 |
| notify | 8107 | 通知/站内信 |
| monitor | 8108 | 健康/监控 |
| admin | 8109 | 管理后台 |
| margin | 8110 | 杠杆/保证金交易 |
| staking | 8112 | 质押理财(含 Solidity 合约源码 contracts/) |
| risk | 8114 | 风控(下单限额/滑点/反钓鱼/AML 黑名单) |
| ticket | 8116 | 工单系统 |
| futures | 8117 | 永续合约(开平仓/持仓/强平/资金费率/成交落库) |
| common | — | 公共基座(Result/异常/DTO/配置) |

## 二、文档地图（47 篇）

### A. 入门/概览
| 文档 | 内容 |
|------|------|
| `docs/feature-guide.md` | **功能速览**（每个模块做什么/为什么/怎么实现）— 首选 |
| `docs/ARCHITECTURE.md` | 架构蓝图/模块拓扑（⚠️ 早期版本，域状态以 feature-guide 为准） |
| `docs/common-domain.md` | **公共基座**（Result/异常/DTO/统一分页/乐观锁）— 读代码风格入口 |
| `docs/roadmap.md` | 开发路线图（对标主流交易所的差距） |

### B. 核心交易域
| 文档 | 内容 |
|------|------|
| `docs/order-domain.md` | 现货订单域（下单/撮合/深度/成交/状态机） |
| `docs/asset-domain.md` | 资产域（钱包/流水/行锁/幂等/资金安全铁律） |
| `docs/chain-domain.md` | 链上域（充提/扫描/地址派生） |
| `docs/market-domain.md` | 行情域（聚合器/K 线/WebSocket） |

### C. 合约衍生品
| 文档 | 内容 |
|------|------|
| `docs/futures-domain.md` | 永续合约（撮合/持仓/强平/资金费率） |
| `docs/margin-domain.md` | 杠杆保证金 |

### D. 支撑域
| 文档 | 内容 |
|------|------|
| `docs/notify-domain.md` / `docs/ticket-domain.md` / `docs/admin.md` / `docs/admin-b-domain.md` | 通知/工单/管理 |
| `docs/risk-domain.md` | 风控 |
| `docs/staking-domain.md` | 质押 |

### E. 关键功能专题
| 文档 | 内容 |
|------|------|
| `docs/web3-wallet.md` | Web3 钱包目标/规划 |
| `docs/advanced-orders.md` | 高级订单（条件单/OCO/止盈止损） |
| `docs/futures-domain.md` | ... |

### F. 资金安全（⭐ 重点项目）
| 文档 | 内容 |
|------|------|
| `docs/cold-wallet.md` | 冷热钱包分离 + 离线多签 |
| `docs/key-management.md` | 密钥外部化 + 生产防呆 + KMS 方案 |
| `docs/withdraw-review.md` | 提现两阶段审核(初审+复核 SoD) |
| `docs/aml-compliance.md` | KYC + AML 制裁黑名单 |
| `docs/reconciliation.md` | 内部资金对账引擎 |
| `docs/chain-reconciliation.md` | 链上钱包余额核对 |

### G. 高并发/性能
| 文档 | 内容 |
|------|------|
| `docs/high-concurrency.md` | 高并发架构设计（分片/缓存/异步） |
| `docs/performance.md` | wrk 压测报告（读 3 万/写 1.6 千） |
| `docs/orderbook-persistence.md` | 撮合簿持久化(重启重建) |
| `docs/fill-persistence-hpa.md` | 成交落库 + HPA 扩缩容 |

### H. 可观测性/基础设施
| 文档 | 内容 |
|------|------|
| `docs/monitoring.md` | Prometheus + Grafana + 告警 |
| `docs/elk-logging.md` / `docs/tracing.md` | 日志(ELK) / 链路(Zipkin) |
| `docs/kubernetes-cicd.md` | k8s 部署 + ArgoCD + CI/CD |
| `docs/kafka-market-pipeline.md` / `docs/mq-topics.md` | 行情管道 / MQ 主题 |

### I. 接口/细节
| 文档 | 内容 |
|------|------|
| `docs/api-keys-domain.md` | 交易 API 密钥(OpenAPI 契约) |
| `docs/order-list.md` / `docs/ledger-pagination.md` | 订单/流水分页 |
| `docs/frontend-ui-redesign.md` / `docs/trade-ui-orderbook.md` / `docs/ws-realtime.md` | 前端/盘口/WS 实时 |
| `docs/layer2-notes.md` / `docs/solidity-contracts.md` / `docs/defi-price-source.md` / `docs/nft-support.md` | 合约/DeFi/NFT |

## 三、快速理解项目（3 句话）

1. **资金安全是生命线**：资产域用「流水 + 行锁 + 幂等」保证钱不多不少不重复扣；叠加冷热钱包/密钥托管/两阶段审核/AML/对账。
2. **撮合是核心**：DAG 内存撮合（价格-时间优先、按币对隔离加锁），成交持久化、撮合簿重启可重建。
3. **真实可运营**：接入 Binance 真实行情 → 全链路微服务 → k8s + ArgoCD 自动部署，全量测试稳定通过。

## 四、如何开始读代码

```
1. 看 Result/异常:      exchange-common/model + handler
2. 看下单主流程:        order 域 OrderService.placeOrder
3. 看资金安全:          asset 域 LedgerService.doChange(行锁+流水+幂等)
4. 看撮合:              order/futures 域 MatchingEngine
5. 看链上充提:          chain 域 DepositScanner/WithdrawServiceImpl
```

---

> 注：个别早期文档（ARCHITECTURE/PROJECT_MEMORY/roadmap）是早期迭代快照，域的能力状态以
> **feature-guide.md + 各最新 domain 文档** 为准。代码永远是最新真相（Text 纪律：文档辅助，代码为准）。