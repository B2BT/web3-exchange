# Web3 钱包 Phase 2.1 — M4 全量测试与演示报告

> 日期：2026-08-07 · 里程碑 M1/M2/M3 全部落地，M4 全量回归 + 演示
> 测试脚本：`scripts/api_test.py` · 报告：`docs/test-reports/report-20260807_110327.md`

## 一、里程碑达成总览

| 里程碑 | 内容 | 状态 |
|--------|------|------|
| **M1** 托管钱包补强 | BIP44 每用户充币地址自动生成 + 提现签名广播 + 前端充值二维码 | ✅ |
| **M2** 自托管钱包 | 创建/导入/地址/链上余额 + 前端 Web3 钱包页 | ✅ |
| **M3** 自托管转账 | 离线签名广播 + 链上资产看板 | ✅ |
| **M4** 全量测试+演示 | 39 用例全 PASS + 浏览器演示 + 充值入账 E2E | ✅ |

## 二、全量 API 回归（39 用例，0 失败）

`python3 scripts/api_test.py` → `docs/test-reports/report-20260807_110327.md`

域分布：
- **auth** 2 · **market** 4 · **order** 7 · **asset** 2 · **notify** 1 · **monitor** 1
- **chain**（托管钱包/充值/提现）7
- **chain.wallet**（自托管钱包 M2+M3）15

关键覆盖：
- 充值地址：自动生成 BIP44 / 0x 前缀 / 幂等 / 同链复用 / 记录分页
- 提现：申请落单 / requestId 前缀
- 自托管：创建 HD（助记词 12 词 + 地址）/ 重复导入唯一约束 / 导入私钥派生地址正确 / 列表不回传明文助记词 / 余额含精度 / BTC 链派生
- 转账：广播返回 txHash / from=钱包地址 / 非法地址拒绝 / 缺币种拒绝 / 金额<=0 拒绝

## 三、真实充值入账 E2E（托管钱包资金闭环，手动验证）

配置 mock RPC 的 `deposit_address` 指向测试用户自动派生的 BIP44 充币地址后，观察扫描器全链路：

```
链上交易 0xefef... 命中充币地址 0x7099... (USDT 1e6)
  → 扫描器落单 t_deposit status=0
  → 确认数 = 258-256+1 = 3 ≥ 2（required）达标
  → 调 asset credit 入账（requestId=DEP:{id} 幂等）
  → status=2 已入账，USDT 可用余额 +1,000,000 ✅
```

实测余额变化：`1999999938659751000` → `1999999938660751000`（+1,000,000 USDT 最小单位）。
> 注：mock 默认固定返回同一 tx_hash，与既有测试行 `uk_tx_hash` 冲突导致首轮"重复仅更新确认数"；
> 更换为新 tx_hash 后完整入账成功——印证 `uk_tx_hash` 幂等双保险生效。

## 四、前端演示（浏览器截图）

| 页面 | 截图 | 验证点 |
|------|------|--------|
| 资产充值 tab（M1） | `/tmp/frontshot/demo-m1-deposit.png` | 充币地址 + 二维码 + 复制按钮，深色主题 |
| Web3 钱包页（M2+M3） | `/tmp/frontshot/demo-m2-wallet.png` | 链上资产看板(ETH=3/USDT=0) + 钱包列表(转账按钮) + 创建/导入表单 |

## 五、技术要点记录

1. **BIP44 派生**：`HdWalletService`（配置主助记词，托管充币地址）+ `Bip44Utils`（用户助记词/私钥，自托管）共用于 BIP39/BIP32 派生，EVM=60/BTC=0/TRON=195。
2. **私钥加密**：`AesGcmCrypto`（PBKDF2+AES-GCM）加密助记词/私钥入库，明文不落库、不回传。
3. **⚠️ 雪花 id JS 精度丢失**（M3 实际踩坑）：`WalletVO.id`/`WalletSendResultVO.walletId` 序列化为 String，否则前端把 >2^53 的 id 舍入后查询报 400。
4. **提现/转账签名**：离线签名后**重签比较 r/s** 自检（规避 EIP-155 大 v 值恢复问题），再 `eth_sendRawTransaction` 广播。
5. **幂等**：充值 `uk_tx_hash` + `requestId=DEP:{id}` 双保险；提现 freeze/transfer/unfreeze 各自 requestId。

## 六、Git 提交记录（M1-M4）

```
dc22ab9 46cb30d 2674591 67c884d 4cba37b   # M1 (backend/fix/frontend/test/docs)
db2c379 c556a20 69aa5cf c7a2dc6 9cf9f7f   # M2
6e4b322 9c6f3a2 4423adb d476f76           # M3
c8135f0 (本轮 M4 测试+报告)                # M4
```
