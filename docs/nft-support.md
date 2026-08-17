# NFT（ERC-721/1155）充值提现支持

> 为 web3-exchange 链上域扩展 NFT 标准支持，在既有 ERC-20 链上充提体系上增加 ERC-721（NFT）与 ERC-1155（半同质化）代币的充值扫描与提现。

## 架构

```
链上(web3j) Transfer 事件
   ├── ERC-20  Transfer(from,to,value)         → 同质化代币充值
   ├── ERC-721 Transfer(from,to,tokenId)       → NFT 充值(amount=1)
   └── ERC-1155 TransferSingle/Batch           → 半同质化充值(含数量)
        │
        ▼
DepositScanner → handleNftTransfer → t_deposit(token_id) → NFT资产列表接口
```

## 数据模型扩展

| 表 | 字段 | 说明 |
|----|------|------|
| `t_coin` | `token_standard` | 代币标准：ERC-20（默认）/ ERC-721 / ERC-1155 |
| `t_deposit` | `token_id` | NFT 代币 ID（ERC-721 唯一编号 / ERC-1155 的 id；ERC-20 为 NULL）|
| `t_withdraw` | `token_id` | NFT 提现指定 tokenId |

## 充值扫描（DepositScanner）

- **ERC-721**：`Transfer(from,to,tokenId)`，tokenId 在 topics[3]，amount 恒为 1
- **ERC-1155**：`TransferSingle`（data 解析 id+value）与 `TransferBatch`（ABI 解码 ids[]+values[] 双数组）
- 三个标准的事件签名常量 + `addOptionalTopics` 一次 eth_getLogs 拉取，靠 topic0 分流
- `handleNftTransfer`：幂等落单（uk_tx_hash），带 tokenId
- **NFT 不校验 symbol**（同一链地址接收该链所有 NFT 合约），ERC-20 仍需 symbol 匹配

## NFT 资产接口

- `GET /api/chain/nft/list?userId=&page=&size=` → 用户 NFT 资产分页（tokenId 非空 + 状态 1/2）

## 提现（transferFrom）

`WithdrawServiceImpl.broadcast()` 对 NFT（ERC-721/1155）用 `transferFrom(hotWallet, to, tokenId)`：
- gas 150,000（ERC-721 transferFrom 比 ERC-20 transfer 贵）
- 金额为 0（NFT 不转移原生价值）
- 提现请求 `WithdrawApplyRequest.tokenId` 指定要提的 NFT

## 端到端验证（mock-rpc）

mock-rpc 扩展返回一条 ERC-721 Transfer 日志（合约 0x5B38...，tokenId=66）：
- 新增 PUNKS 币种（token_standard=ERC-721，contract=0x5B38...）
- chain 扫描器识别 tokenId=66 → 落单 `[deposit] NFT落单 ... symbol=PUNKS tokenId=66 amount=1`
- `GET /api/chain/nft/list` 返回 PUNKS tokenId=66

## 排坑记录

1. **ERC-721 data 为空**：NFT Transfer 的 data 是 `0x`，不能当 value 解析（会抛 "Zero length BigInteger"）。改为 ERC-721 用 topics[3] 取 tokenId。
2. **NFT 不校验 symbol**：NFT 与 ERC-20 共用链充币地址，原 symbol 相等校验会误拒 NFT。放宽为 NFT 只校验链 + 地址归属。
