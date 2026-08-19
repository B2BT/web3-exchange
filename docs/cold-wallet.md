# 冷热钱包分离 + 冷钱包离线签名

> 提现金额分流：**小额走热钱包在线签名，大额走冷钱包离线签名（多签）**。
> 冷钱包私钥不落地服务器，通过"构建待签交易 → 离线签名 → 回填广播"实现，是 CEX 资金安全的分水岭。

## 架构

```
提现申请(apply)
   ├── 冻结资金(audit) → broadcast()
   │        │
   │        ├─ 金额 < cold-threshold ──► 热钱包在线签名 → 直接广播(现有逻辑)
   │        │
   │        └─ 金额 >= cold-threshold ──► 冷钱包流程:
   │             1. 构建 unsigned tx(不签名不广播) → 存 t_withdraw.cold_tx_data, 状态=6(待冷签)
   │             2. 管理平台拉取待签清单 /withdraw/cold/pending
   │             3. 离线环境用冷钱包私钥签名(contracts/cold-sign.js, 私钥不上服务器)
   │             4. 回填签名 /withdraw/cold/sign → 收集 N 个签名(多签)
   │             5. 达到阈值 → 广播
   │
   └─ 记录全流程(t_sign_mode / 多签计数 / 审核人)
```

## 配置(appplication.yml)

```yaml
chain:
  withdraw:
    cold-threshold: 5000000000000000   # 最小单位，>= 该值走冷钱包
    cold-required-signs: 2             # 冷钱包多签所需签名数(N 审 1 签)
```

## 数据库扩展(t_withdraw)

| 字段 | 说明 |
|------|------|
| `sign_mode` | 1=热钱包(小额), 2=冷钱包(大额) |
| `cold_tx_data` | 待签名交易(unsigned tx hex) |
| `cold_sign_hash` | 待签交易签名哈希(离线核对用) |
| `cold_signed_raw` | 离线签名后的 raw(hex) |
| `cold_sign_count` / `cold_required_signs` | 已收集签名数 / 阈值(多签) |
| 状态 6=待冷钱包签名 | audit 后大额转入此状态 |

## 接口

### 管理平台
```http
GET  /api/chain/internal/withdraw/cold/pending?            → 待冷签清单
POST /api/chain/internal/withdraw/cold/sign?withdrawId=&signedRawHex=  → 提交离线签名
```

## 离线签名工具(contracts/cold-sign.js)

在【离线/隔离环境】运行(ethers, 私钥仅存在于本机):

```bash
# 1. 从待签清单拿 unsigned tx(cold_tx_data)
# 2. 离线签名(用冷钱包私钥)
node cold-sign.js sign <unsignedHex> <colPrivKey>

# 3. 校验签名者 from 是否为预期冷钱包地址
node cold-sign.js verify <signedRawHex> <expectedColdAddr>
```

## 验证(mock-rpc + hardhat)

- ✅ 小额 → sign_mode=1 → 热钱包直接广播(txHash)
- ✅ 大额(>= 阈值) → sign_mode=2 → 状态转 6(待冷签)，不广播
- ✅ 待签清单返回该单(cold_tx_data / cold_sign_hash)
- ✅ 离线签名回填(提交 1/2 → 收集中；2/2 → 广播成功，状态 3)

## 与真实生产的差距(本实现为演示级)

1. **多签是"N 审 1 签"**：真实多签是智能合约钱包(如 Gnosis Safe)的 2-of-3 signature，需部署合约 + 收集多个独立私钥签名
2. **冷钱包校验**：此处信任离线工具；生产应在离线工具内校验 from 属冷钱包白名单，并在服务端强校验
3. **密钥托管**：冷签名好；生产私钥应存 **KMS/HSM**(而非 config 明文)
4. 建议结合 `docs/production-gap.md` 逐步补齐
