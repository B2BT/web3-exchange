# Web3 钱包契约 — Phase 2.1

> 作者：PM · 对标主流交易所（Binance Web3 Wallet / OKX Wallet / Bybit）
> 定位：Web3 交易所**差异化核心**——托管(CEX自托管私钥) + 链上资产，区别于普通 CEX
> 分阶段：**A 托管钱包**（交易所持私钥，用户链上充值/提现）→ **B 自托管钱包**（用户助记词/私钥，DApp/DeFi）→ **C 链上资产看板**

## 一、目标功能
- **托管钱包（Custodial）**：交易所为每用户生成独立链地址（BIP44），充值自动扫链入账、提现签名出账。✅ M1 已落地 BIP44 每用户地址自动派生 + 前端充值二维码；充值/提现扫链入账与签名广播沿用 chain 域既有实现。
- **自托管钱包（Self-Custodial）**：用户创建/导入助记词(HD)或私钥，导出到 Keystore/QR，可查链上余额、转账（SignMessage/交易签名），接 DApp（可选 WalletConnect）。✅ M2 已落地创建/导入/地址/链上余额 + 前端 Web3 钱包页；✅ M3 已落地自托管转账（离线签名广播）+ 链上资产看板。
- **链上资产看板**：绑定地址后展示 BTC/ETH/ERC-20/USDT 链上余额。

## 二、后端设计（扩展 exchange-chain 域）
1. **托管钱包完善**（现有补强）：
   - BIP44 派生：`m/44'/coin'/0'/0/i` 为每用户生成 BTC(0)/ETH(60)/USDT(60 ERC20) 地址，私钥加密存储（AES-GCM，密钥环境变量）。
   - 充值入账：扫链（BTC UTXO / ETH block / ERC20 logs）→ 确认数阈值 → 调用 asset LedgerService.credit 入账。
   - 提现：用户申请 → 管理员审核（已有）→ 链上签名广播 → 回调更新状态。
2. **自托管钱包（新增）**：
   - 表 `t_user_wallet`：userId, chainCode, walletType(HD/PRIVATE/READONLY), mnemonicEnc(仅HD), privateKeyEnc, address, addressType(托管/自托管), createdAt。
   - 接口（/api/chain/wallet/**，需登录）：
     - `POST /api/chain/wallet/create` {chainCode} → 生成 HD 助记词+地址（返回明文一次，私钥加密存库）
     - `POST /api/chain/wallet/import` {chainCode, mnemonic|privateKey} → 导入校验（BIP39）
     - `GET /api/chain/wallet/list` → 用户钱包列表
     - `GET /api/chain/wallet/{id}/address` → 地址/二维码
     - `GET /api/chain/wallet/{id}/balance` → 链上余额（BTC/ETH/ERC20 USDT）
     - `POST /api/chain/wallet/{id}/send` {to, amount, fee} → 链上转账（签名广播）
   - 库：web3j（已有，ETH/ERC20）+ bitcoinj（新增，BTC）或 HTTP-RPC。
3. **密码学**：BIP39 助记词（web3j Bip39 / bitcoinj）、BIP32/44 派生、私钥 AES 加密（PBKDF2 派生密钥）。
4. **依赖**：exchange-chain pom 加 bitcoinj-core（BTC 地址/交易）。

## 三、前端设计（扩展前端）
- 用户中心或独立路由 `/wallet`：
  - **Web3 钱包**页：钱包列表（托管/自托管）、创建钱包（显示助记词备份）、导入钱包、查余额、转账、地址/二维码。
  - 托管充值：显示充值地址+二维码（复用现有）。
  - 自托管：创建/导入助记词 → 展示地址 → 查链上余额 → 转账表单。
- 深色主题对齐；`api/chain.ts` 加 wallet 接口。

## 四、测试（纳入 api_test.py）
- 托管：充值地址生成、充值入账（mock 扫链）、提现审核链路。
- 自托管：创建钱包返回助记词+地址、导入校验、查余额、转账签名广播（mock RPC）。
- 生成测试报告 docs/test-reports/。

## 五、里程碑

- **M1** ✅（已落地 2026-08-07）：托管钱包补强（BIP44 地址生成 + 提现签名广播完善）→ 前端充值地址/二维码。
  - 后端：`HdWalletService`（BIP39 主助记词 `chain.hd-wallet.mnemonic` + BIP32/44 派生 `m/44'/coin'/0'/0/index`，
    EVM coinType=60、BTC=0、TRON=195 预留）；`DepositService.getOrCreateDepositAddress` 幂等自动生成
    （每链一地址，Redis `chain:hd:index:{chainCode}` 自增索引，落 `t_asset_address` address_type=1）；
    `GET /api/chain/deposit/address` 无则自动生成。提现签名广播（冻结→离线签名→RPC 广播→签名自检→回执确认→扣减/回滚）核验通过。
  - 前端：资产页充值 tab 自动派生地址 + 二维码（qrcode 库）展示。
  - 测试：`scripts/api_test.py` 新增 6 用例，23 全 PASS（`docs/test-reports/report-20260807_100927.md`）。
- **M2** ✅（已落地 2026-08-07）：自托管钱包创建/导入/地址/余额 → 前端 Web3 钱包页。
  - 后端：新增表 `t_user_wallet`（sql/wallet.sql）+ `UserWallet` 实体/Mapper；`AesGcmCrypto`
    （PBKDF2+AES-GCM）加密私钥/助记词入库、明文不落库；`Bip44Utils` 从用户助记词/私钥派生
    BIP44 首地址（区别于配置主助记词的 HdWalletService）；`UserWalletService.create/import/list/address/balance`；
    接口 `POST /api/chain/wallet/create|import` + `GET /list|/{id}/address|/{id}/balance`（需登录）。
    余额查询走 chainRegistry Web3j（原生币 eth_getBalance / ERC-20 eth_call balanceOf）。
  - 前端：新增 `/wallet` 路由 + Web3 钱包页（钱包列表、创建返回助记词备份+二维码、导入助记词/私钥二选一、查链上余额）。
  - 测试：新增 8 个 chain.wallet 用例，31 全 PASS（`docs/test-reports/report-20260807_104006.md`）。
- **M3** ✅（已落地 2026-08-07）：自托管转账（签名广播）+ 链上资产看板。
  - 后端：`UserWalletService.send` 解密钱包私钥（AES-GCM）→ Credentials → 组装交易（原生 ETH/ERC-20 USDT，
    nonce/gas/chainId）→ 离线签名 → `eth_sendRawTransaction` 广播；校验目标地址 0x+40hex、金额>0、币种存在且链匹配。
    接口 `POST /api/chain/wallet/{id}/send`。
  - 前端：Web3 钱包页加「链上资产看板」（按币种聚合所有钱包余额）+ 转账对话框（ETH/USDT 离线签名广播）。
  - ⚠️ 修复雪花 id JS 精度丢失：`WalletVO.id`/`WalletSendResultVO.walletId` 序列化为 String（否则前端查余额/转账
    用被 JS 舍入的假 id 报 400）。
  - 测试：新增 3 个转账用例，34 全 PASS（`docs/test-reports/report-20260807_105509.md`）。
- **M4** ✅（已落地 2026-08-07）：全量测试 + 报告 + 演示。
  - 全量回归：`api_test.py` 39 用例全 PASS（`docs/test-reports/report-20260807_110327.md`），覆盖 auth/market/order/asset/notify/monitor/chain/chain.wallet。
  - 充值入账 E2E：mock RPC 指向 BIP44 充币地址 → 扫描落单 → 确认达标 → credit 入账，USDT 余额 +1e6（`uk_tx_hash` 幂等双保险验证）。
  - 前端演示：`docs/screenshots/m1-deposit.png`（充值二维码）、`docs/screenshots/m2-wallet.png`（链上资产看板+钱包列表+转账）。
  - 汇总：`docs/test-reports/M4-web3-wallet-summary.md`。

## 六、后续（Phase 2 其余）
- **P2.2 杠杆现货**（Margin）：借币/抵押/强制平仓——新杠杆账户 + 利率。
- **P2.3 Staking/Earn**：活期/锁仓质押，年化收益分配。
- **P2.4 风控引擎**：下单风控(滑点/限额)、提现二次验证/反钓鱼码、异常登录。
- **P2.5 Admin B**：公告 CRUD、服务监控、管理员审计日志、交易对管理。
