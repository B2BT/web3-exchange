# Layer2 方案原理（ZK-Rollup / Optimistic Rollup）

> 区块链扩容方案学习笔记，贴合 JD「了解 ZK-Rollup / Optimistic Rollup 等 Layer2 方案技术原理」。
> 阐述 Layer2 核心概念、两大 Rollup 方案原理与对比、以及与本 CEX 项目的结合点。

## 为什么需要 Layer2

以太坊主网（Layer1）吞吐有限（~15-30 TPS）、Gas 高，无法承载大规模交易。Layer2 是构建在 L1 之上的二层网络，把大量交易**批量处理后在 L1 结算**，既继承 L1 安全性又大幅提升吞吐（数千 TPS）、降低费用。

## Rollup 核心：状态 + 批量 + 验证

所有 Rollup 的共同骨架：

```
用户交易 → 排序器(Sequencer) 批量打包 → 压缩成 Rollup 块
        → 提交到 L1（状态根 + 交易数据 / 证明）
        → L1 合约验证并更新 L2 状态根
```

- **L2 状态**：账户余额、合约存储等，维护在 L2 上
- **L1 存储状态根**：L2 状态的哈希承诺，保存在 L1 合约
- **交易数据可用性**：所有交易数据必须发布到 L1（calldata），保证任何人可重建 L2 状态

## ZK-Rollup（零知识证明）

### 原理
排序器执行交易后生成一个**零知识证明**（如 SNARK/STARK），证明「这批交易在 L1 上记录的状态根转换是正确执行的」。L1 验证合约**只验证证明**，不重放交易，即「**验证成本与交易数无关**」。

```
L2 执行交易 → 生成 ZK 证明(证明状态转换正确) → L1 验证证明 → 接受新状态根
```

### 关键特性
| 特性 | 说明 |
|------|------|
| **有效性证明** | 证明本身就是最终确认，**无挑战期**，即时终局 |
| **提现快** | 无需等待挑战期，几分钟内可提现回 L1 |
| **证明生成贵** | 生成 ZK 证明需大量计算（GPU/专用硬件），Sequencer 成本高 |
| **代表项目** | zkSync、StarkNet、Scroll、Polygon zkEVM |

## Optimistic Rollup（乐观 / 欺诈证明）

### 原理
默认假设「提交的交易是正确的」（**乐观**），不立即验证。任何人可在**挑战期**（如 7 天）内提交**欺诈证明**，指出某个状态转换是错的，L1 重放该交易验证。

```
L2 执行交易 → 提交状态根(不验证,乐观) → 挑战期内可质疑 → 无质疑则确认
                                         │ 有质疑 → L1 重放验证 → 惩罚作恶者
```

### 关键特性
| 特性 | 说明 |
|------|------|
| **欺诈证明** | 假设正确，靠挑战机制保证安全 |
| **挑战期** | 提现需等待挑战期（~7 天），确认慢 |
| **证明成本低** | 不生成 ZK 证明，Sequencer 便宜 |
| **EVM 兼容好** | 直接兼容 Solidity，迁移成本低 |
| **代表项目** | Optimism、Arbitrum、Base |

## ZK vs Optimistic 对比

| 维度 | ZK-Rollup | Optimistic Rollup |
|------|-----------|-------------------|
| **验证机制** | 有效性证明（零知识） | 欺诈证明（挑战） |
| **终局速度** | 快（即时） | 慢（挑战期 ~7 天） |
| **提现时间** | 分钟级 | 天级 |
| **证明生成成本** | 高（Sequencer 贵） | 低 |
| **吞吐** | 更高（证明压缩） | 高 |
| **EVM 兼容** | 逐渐完善（zkEVM） | 原生兼容 |
| **安全性假设** | 密码学假设 | 至少一个诚实节点 |

## 与本 CEX 项目的结合点

web3-exchange 作为中心化交易所，可从以下角度理解/接入 Layer2：

1. **降低充提成本**：把充提的链从 L1 迁移到 L2（如 Arbitrum/Optimism/zkSync），Gas 大幅降低，支持小额充提
2. **t_chain 扩展**：现有 `t_chain` 表（chain_type=EVM）天然可加 L2 链，`ChainRegistry` 注册多个 L2 RPC 即可支持 L2 充提（与现有 EVM 扫描逻辑复用）
3. **DeFi 价格源扩展**：DefiPriceSource 可接 L2 上的 DEX（如 Arbitrum Uniswap），读储备算价
4. **跨链资产**：理解 L1↔L2 桥的「锁定/铸造」模型，为跨链充提打基础

### 接入 L2 的实际步骤（基于现有架构）
```
1. t_chain 加一条 L2 链记录（chain_code=ARB, rpc_url=Arbitrum RPC, chain_type=EVM, scan_enabled=1）
2. t_coin 加该链上的 USDT/ETH 币种（contract_address 为 L2 上合约地址）
3. ChainRegistry 启动时自动注册 L2 的 Web3j 实例
4. DepositScanner 扫描 L2 链的 Transfer 事件（逻辑完全复用，仅 RPC 不同）
```

## 参考学习资源
- ZK-Rollup：zkSync 白皮书、StarkWare 文档
- Optimistic：Optimism 规范、Arbitrum 架构
- 通用：Vitalik《An Incomplete Guide to Rollups》
