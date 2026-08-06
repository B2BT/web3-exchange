# 链上域（Phase 3）落地设计：web3j 充值监控、提现签名广播、接入 asset 入账/冻结

> 版本：v1.0 · 作者：系统架构师 · 日期：2026-08-06
> 适用：`exchange-chain`（Nacos 服务名 `exchange-chain`，端口 **8105**）落地依据。
> 定位：本文件是 `docs/ARCHITECTURE.md` 附录「链上域」与 3.2.7 的**落地细化**（web3j 选型、充值/提现状态机、Mock RPC 验证、接口契约），与架构蓝图一致，供 `/dev` 直接照此实现。**本文件为新建，不改动 `docs/` 既有文档、不改动 `sql/*.sql`、不改动 asset 库已建表结构。**
> 兼容基线：Spring Boot 3.2.5 / Spring Cloud Alibaba 2023.0.1.0 / Java 17 / MyBatis-Plus 3.5.7 / **web3j 4.10.3** / MySQL 8 / Redis 7 / 统一 `Result<T>` / `BaseEntity`（id=雪花 + createBy/createTime/updateBy/updateTime + isDeleted 逻辑删除 + version 乐观锁 + tenantId 租户）。
> 依赖：**资金能力复用 `exchange-asset` 已实现的内部接口 `/internal/asset/**`**（credit 充值入账 / freeze 提现冻结 / unfreeze 失败回滚 / transfer 成功扣减，金额 Long 最小单位，requestId 幂等）。充值/提现业务表 `t_deposit/t_withdraw/t_asset_address/t_chain/t_coin` **已在 asset 库建好**，chain 模块通过自有 entity+mapper 读写这些表（**表归属 asset 库，chain 不再建任何新表**）。

---

## 目录

1. [总体设计要点](#一总体设计要点)
2. [web3j 依赖选型与引入](#二web3j-依赖选型与引入)
3. [链/币种配置驱动与 ChainRegistry](#三链币种配置驱动与-chainregistry)
4. [充值监控设计](#四充值监控设计)
5. [提现流程设计](#五提现流程设计)
6. [地址与私钥（热钱包）管理](#六地址与私钥热钱包管理)
7. [REST / 内部接口契约](#七rest--内部接口契约)
8. [ERC-20 / ETH 链上交互要点](#八erc-20--eth-链上交互要点)
9. [Mock RPC 验证方案](#九mock-rpc-验证方案)
10. [落地 Checklist（/dev 实施指引）](#十落地-checklistdev-实施指引)

---

## 一、总体设计要点

- **本期范围：ETH 链 + ERC-20（USDT 优先），原生 ETH 一并支持。** web3j 是 EVM 链最成熟、与 Java 17/Spring Boot 3.2.5 兼容性最好的库。**BTC / TRON 本期不做**（不同链协议、不同库：BTC 需 UTXO/签名库、TRON 需 TronGrid/不同 ABI），仅在 `ChainProvider` 抽象层预留接口与 `t_chain.chain_type` 字段，作为后续扩展点。
- **资金铁律（与 asset 一致）**：chain **绝不直接改 asset 余额**，充值入账走 `POST /internal/asset/credit`，提现冻结/回滚/扣减走 `/internal/asset/freeze|unfreeze|transfer`。金额一律 `long` 最小单位（由 `t_coin.decimals` 定义，USDT=6、ETH=18），应用层禁用 `double/float`。所有跨服务资金操作 `requestId` 由 **chain（调用方）** 生成并保证确定性，asset 幂等兜底。
- **幂等双重防线（充值）**：① `t_deposit.uk_tx_hash` 唯一索引防**同一笔链上交易重复落单/重复入账**；② credit 的 `requestId`（由 depositId 派生）唯一索引防 Feign 重试/消息重投重复入账。二者叠加，账实安全。
- **扫描游标不建新表**：各链已扫描高度存 Redis（`chain:scan:{chainCode}:cursor`），启动时若缺失则从 `t_deposit.max(block_height)`（该链 status∈(0,1)）回退一个安全窗口续扫，保证重启不漏块。**不新增任何 SQL 表**（遵守约束）。
- **配置驱动**：RPC 地址、确认数、扫描开关、Gas 范围、合约地址全部读 `t_chain`/`t_coin`，业务代码不写死。`scan_enabled=1 && status=1` 的链才被调度扫描。
- **接口分层**：对外 REST 走网关（`/api/chain/**`）；服务间调用走 `/internal/chain/**`（本模块内部管理用）与 `/internal/asset/**`（调 asset），网关不路由 `/internal/**`。事件可发 RocketMQ `DEPOSIT-CONFIRMED`（见 `docs/mq-topics.md`），本阶段以「Feign 同步 + 幂等」为主链路。

---

## 二、web3j 依赖选型与引入

### 2.1 版本确认

| 项 | 结论 |
|----|------|
| **web3j 版本** | **`4.10.3`**（父 pom 已声明 `<web3j.version>4.10.3</web3j.version>`，与 `docs/ARCHITECTURE.md` 基线一致） |
| 兼容性 | Java 17 ✅ / Spring Boot 3.2.5 ✅（4.9.x+ 全面支持 Spring Boot 3 / Jakarta）；基于 okhttp 4.x + rxjava 2.2.x + jackson-databind + bouncycastle |
| 是否用 `web3j-spring-boot-starter` | **不用**。官方 starter 仅到 1.6.0（面向旧版 Spring Boot 2），与 Boot 3.2/Java 17 不匹配；采用 **web3j `core` + 手动配置 `Web3j` Bean**，更可控、可多链多实例 |
| 依赖冲突 | 父 pom 的 Spring Boot BOM 已把 `jackson-databind` 钉在 2.15.4，与 web3j 4.10.3 依赖的 jackson 2.15.x 兼容；okhttp/rxjava/bcprov 未被 Boot 管理、由 web3j 自带，无冲突。若构建期出现 jackson 版本告警，在 chain pom 显式加 `jackson-databind` 并交给 Boot 版本即可 |

### 2.2 引入方式（两步）

**① 父 pom `dependencyManagement` 增加 web3j core（版本走已有 `${web3j.version}`）：**

```xml
<dependencyManagement>
    <dependencies>
        <!-- web3j 链上交互（版本复用上方 web3j.version=4.10.3） -->
        <dependency>
            <groupId>org.web3j</groupId>
            <artifactId>core</artifactId>
            <version>${web3j.version}</version>
        </dependency>
    </dependencies>
</dependencyManagement>
```

**② `exchange-chain/pom.xml` 声明依赖（版本由父管理，不写版本号）：**

```xml
<dependencies>
    <!-- ======= web3j 链上交互 ======= -->
    <dependency>
        <groupId>org.web3j</groupId>
        <artifactId>core</artifactId>
    </dependency>
    <!-- 其余与 exchange-asset 对齐：spring-boot-starter-web / nacos-discovery /
         mybatis-plus / mysql / druid / lombok / springdoc / exchange-common / rocketmq -->
</dependencies>
```

> **注意**：父 pom 的 `dependencyManagement` 与 chain pom 均属**既有文件修改**，由 `/dev` 落地时执行（本设计只给目标内容，不改任何文件）。`web3j.version` 属性已在父 pom 定义，无需改动属性区。

### 2.3 `Web3j` Bean 配置（多链多实例）

不注入单例 `Web3j`，而是按链构造并注册到 `ChainRegistry`：

```java
@Configuration
public class Web3jConfig {
    /** 每启用链建一个 Web3j（HttpService 指向 t_chain.rpc_url） */
    @Bean
    public ChainRegistry chainRegistry(ChainService chainService) {
        Map<String, Web3j> map = new HashMap<>();
        chainService.listEnabled().forEach(c -> {
            HttpService hs = new HttpService(c.getRpcUrl(), new OkHttpClient.Builder().build(), false);
            map.put(c.getChainCode(), Web3j.build(hs));
        });
        return new ChainRegistry(map);
    }
}
```

---

## 三、链/币种配置驱动与 ChainRegistry

| 类 | 职责 | 数据来源 |
|----|------|---------|
| `Chain`（entity） | 读 `t_chain`：`chain_code`（ETH…）、`chain_type`（EVM）、`chain_id`（EIP-155）、`rpc_url`、`confirmations`（充值入账确认数）、`withdraw_confirmations`（提现成功确认数）、`scan_enabled`、`min_gas_price/max_gas_price`、`status` | asset 库 `t_chain` |
| `Coin`（entity） | 读 `t_coin`：`symbol`、`chain_code`、`coin_type`（COIN=原生/TOKEN=代币）、`contract_address`、`decimals`、`deposit_enabled/withdraw_enabled`、`withdraw_fee`、`min/max_withdraw`、`min_deposit` | asset 库 `t_coin` |
| `ChainRegistry` | `chainCode → Web3j`；提供 `get(chainCode)`、按 `chainCode` 过滤 `t_coin`（拿该链合约地址集） | 由 `Web3jConfig` 初始化 |

- **ETH 原生币（COIN，如 ETH）**：`contract_address` 为空，扫描**区块内交易**（`tx.to == 充币地址`）。
- **代币（TOKEN，如 USDT）**：`contract_address` 非空，扫描**该合约 `Transfer` 事件日志**。
- 本期只激活 `chain_type='EVM'` 的链（ETH）；`TRON/OTHER` 在 `ChainProvider` 接口预留，`scan_enabled=0` 的链不参与扫描。

---

## 四、充值监控设计

### 4.1 状态机（`t_deposit.status`）

```
               命中充币地址                确认数达标                 credit 成功
   0=监听中 ──────────────────▶ 1=待确认 ──────────────▶ 2=已入账（写 ledger_id，终止）
   （首次发现即落单）          （已扫到但未达阈值）       （调 asset credit 入账成功）
                                    │
                                    ▼
                             3=失败（解码失败/入账异常后重试仍失败，终止）
```

| 状态 | 编码 | 含义 | 资金状态（asset 侧） | 触发 |
|------|------|------|---------------------|------|
| 监听中 | 0 | 首次在链上扫到命中充币地址的入账，已 INSERT | 未入账 | `BlockScanner` 扫到 Transfer 日志 to=充币地址 |
| 待确认 | 1 | 确认数未达 `t_chain.confirmations` | 未入账 | 每次轮询更新 `confirmations` 后仍未达标 |
| 已入账 | 2 | **终止态**：调 `credit` 成功，可用余额已增加 | **已入账**（DEPOSIT 流水 + available↑） | `confirmations >= required_confirmations` → credit 成功 → 写 `ledger_id` |
| 失败 | 3 | 终止态：入账失败（见下） | 未入账 | credit 多次失败 / 事件解码无法还原用户 |

**状态机约束**：0→1→2 单向；已入账(2)为终止态，不允许回退重复入账（`uk_tx_hash` + credit requestId 双重兜底）。状态更新用乐观锁（`version`），`UPDATE ... WHERE id=? AND status=?` 防并发重复入账。

### 4.2 `BlockScanner` 扫描流程（轮询，非订阅）

> 采用**轮询拉取日志**（`eth_getLogs`），比 `subscribe` 更适合交易所场景（可补块、可断点续扫、不依赖长期 WebSocket 连接）。

```
每个调度周期（chain.scan.interval-ms，默认 3000）：
  对每个启用链 c（t_chain.scan_enabled=1 && status=1）：
  1. latest  = web3j.ethBlockNumber()          // 最新块
  2. cursor  = redis.get("chain:scan:"+c) 或启动回退逻辑（见 4.4 补块）
  3. to      = min(latest, cursor + chain.scan.batch-size - 1)   // 批扫，如每批 100 块
  4. 取该链 token 合约地址集合 contracts = {t_coin.contract_address where chain_code=c && coin_type=TOKEN}
  5. 对每批 [cursor,to] 调 eth_getLogs(address=contracts, topics=[Transfer sig], fromBlock, toBlock)
       + 原生币：eth_getBlockByNumber(fullTx=true) 扫 tx.to == 充币地址
  6. 对每条命中日志 → handleLog(log, c)
  7. redis.set("chain:scan:"+c, to+1)；cursor=to+1 继续下一批，直到 cursor>latest 结束本周期
```

`handleLog` 命中逻辑：

```
for 每条日志 L：
  if L 是 ERC-20 Transfer：
     to = 解析 topics[2]（地址）
     amount = Numeric.toBigInt(L.data)
     symbol = 该合约地址对应 t_coin.symbol（一次查好 map）
  else（原生 ETH）：to = tx.to；amount = tx.value
  查 t_asset_address：WHERE chain_code=c AND address=to AND address_type=1 AND is_active=1
  if 命中（用户 A）且 symbol 对应 t_coin.deposit_enabled=1：
     try INSERT t_deposit(userId=A, symbol, chainCode=c, from/to, amount,
                          tx_hash=L.transactionHash, block_height=L.blockNumber,
                          confirmations=0, required_confirmations=c.confirmations, status=0,
                          request_id=生成（入账时用）, fee=0)
     catch DuplicateKeyException(uk_tx_hash) → 已存在，仅更新确认数，不重复入账
```

### 4.3 确认数递增与入账

- 每次轮询对该链 `status IN (0,1)` 的 `t_deposit` 记录：`confirmations = latestBlock - block_height + 1`，`UPDATE ... SET confirmations=?, status=(confirmations>=required ? 1 : 0) WHERE id=? AND status<2`。
- 当 `confirmations >= required_confirmations` 且 `status=1` → 触发入账：

```java
// requestId 由 depositId 派生，保证同一笔充值重复触发时 requestId 相同
CreditRequest req = CreditRequest.builder()
        .requestId("DEP:" + deposit.getId())      // 幂等
        .userId(deposit.getUserId())
        .symbol(deposit.getSymbol())
        .amount(deposit.getAmount())               // 最小单位，已扣链上手续费(fee 本期=0)
        .bizType("DEPOSIT")
        .refNo(deposit.getId().toString())
        .remark("充值入账")
        .build();
Result<LedgerVO> r = assetClient.credit(req);     // Feign → /internal/asset/credit
if (r.isOk()) {
    UPDATE t_deposit SET status=2, ledger_id=r.getData().getId(), request_id=req.requestId WHERE id=? AND status=1;
} else {
    记录失败并保留 status=1，下一轮重试（credit 幂等，重试安全）
}
```

**幂等总结（充值）**：

| 层 | 键 | 落点 |
|----|----|------|
| 链上交易唯一 | `t_deposit.uk_tx_hash` | 防同一笔链上交易重复落单/重复入账 |
| credit 幂等 | `requestId = "DEP:"+depositId` | `t_asset_ledger.uk_request_id`，防 Feign 重试/消息重投重复入账 |

### 4.4 补块 / 断点续扫（重启恢复）

- 正常运行时游标存 Redis（`chain:scan:{chainCode}:cursor`），每批扫完即写。
- **启动时**游标缺失 → 回退逻辑：
  ```
  cursor = SELECT max(block_height) FROM t_deposit WHERE chain_code=? AND status IN (0,1)
           - chain.scan.safety-window（默认 3 块，覆盖极短时间窗口内新到的未确认交易）
  cursor = max(cursor, 0)；若无记录 → 用 chain.scan.start-block（默认 0 或配置值）
  ```
  之后从 `cursor` 续扫，因 `uk_tx_hash` 幂等，重复扫到已入账记录仅更新确认数、绝不重复入账。
- **孤儿块回滚（可选，标注）**：对每个扫到的块 `eth_getBlockByNumber` 校验 `parentHash` 连续；若最新 `parentHash` 与游标前块不符（链重组织），将 Redis 游标回退到重组点并从该处重扫，重扫命中已入账记录由 `uk_tx_hash` 幂等兜底。**本期实现补块即可，孤儿块回滚标注为增强项（Phase 5）。**

### 4.5 充币地址来源（本期简化）

- 架构目标态为「热钱包派生（HD）」，但**本期简化**：支持**配置/预生成地址绑定用户**——运维在 `t_asset_address` 预置若干 `address_type=1`（用户充币地址）+ `is_active=1` 的行，`BlockScanner` 据此识别入账归属。冷/热钱包 HD 派生、热钱包轮换留待 Phase 5。
- 对外提供 `GET /api/chain/deposit/address?userId&chainCode&symbol`：返回该用户已绑定的充币地址；未绑定返回 `notFound`（本期不自动生成，提示运营预配置）。

---

## 五、提现流程设计

### 5.1 状态机（`t_withdraw.status`）

```
 0=待审核 ──▶ 1=审核中 ──▶ 2=处理中(已冻结上链) ──▶ 3=成功（终止）
   │            │                │ 广播失败/回执失败
   │            │                ▼
   │            │           5=失败回滚（解冻，终止）
   │            ▼
   │        4=拒绝（无冻结，终止）
```

| 状态 | 编码 | 含义 | 资金状态（asset 侧） | 触发 |
|------|------|------|---------------------|------|
| 待审核 | 0 | 用户申请已落单，等待风控/运营审核 | 无冻结 | 用户 `POST /api/chain/withdraw/apply` |
| 审核中 | 1 | 已进入人工审核 | 无冻结 | 审核人开始处理（可选状态，可合并入 0） |
| 处理中 | 2 | **已冻结 + 正在上链** | **已冻结**（freeze：available→frozen） | 审核通过 → 调 asset freeze → 签名广播 |
| 成功 | 3 | **终止态**：链上确认 → 永久扣减 | **已扣减**（transfer：冻结→平台热钱包账户） | 回执确认达标 → 调 asset transfer |
| 拒绝 | 4 | 终止态：审核拒绝 | 无冻结（未动资金） | 审核拒绝 |
| 失败回滚 | 5 | 终止态：上链失败 → 解冻退回 | **已解冻**（unfreeze：frozen→available） | 广播失败 / 回执异常 → 调 asset unfreeze |

### 5.2 申请（幂等落单）

```
POST /api/chain/withdraw/apply {userId, symbol, chainCode, toAddress, amount}
1. 校验：t_coin.withdraw_enabled=1、chain 匹配、toAddress 合法(0x + 40hex)、
        amount>=min_withdraw && <=max_withdraw、daily 限额（可选）
2. fee = t_coin.withdraw_fee；real_amount = amount - fee（表已定义，二者均最小单位）
3. requestId = "WD:" + 雪花ID  （申请时生成，幂等键）
4. INSERT t_withdraw(status=0, ...)；撞 uk_request_id → 幂等返回已有单
```

### 5.3 审核（冻结）

```
审核通过（approve=true）：
1. UPDATE t_withdraw SET status=2, audit_by/time/remark WHERE id=? AND status IN (0,1)
   （乐观锁 version 兜底，防并发重复审核）
2. 调 asset freeze：
   FreezeRequest{ requestId = withdraw.requestId,          // 幂等
                  userId = withdraw.userId, symbol = withdraw.symbol,
                  amount = withdraw.amount,                // 全额（含手续费）
                  bizType = "FREEZE", refNo = withdrawId, remark = "提现冻结" }
   → 成功：freeze_ledger_id = ledger.id，回写 t_withdraw
   → 失败（如余额不足 409）：UPDATE status=5(fail_reason=余额不足) 或退回待审核，不再上链
3. 进入 5.4 签名广播（可异步）
审核拒绝（approve=false）：UPDATE status=4, audit_by/remark；无资金操作。
```

> 冻结放在**审核通过进入处理中(2)**这一步（先锁资金再上链），避免「上链了但资金未被冻结」的风险。`requestId = withdraw.requestId` 复用申请幂等号（与 credit 不同，freeze/transfer/unfreeze 是不同流水，各自唯一）。

### 5.4 签名广播（处理中）

```
1. 取热钱包私钥（见 §6，非明文）→ Credentials credentials
2. 组装交易：
   非ce = web3j.ethGetTransactionCount(hotWallet, pending)
   gasPrice = 取 t_chain.min_gas_price..max_gas_price 之间（可 eth_gasPrice 估算后 clamp）
   gasLimit = 代币：estimateGas(transfer) 或固定 100000；原生 ETH：21000
   chainId  = t_chain.chain_id
   ERC-20（USDT）：RawTransaction 的 data = FunctionEncoder.encode(
                    new Function("transfer", Arrays.asList(
                       new Address(toAddress), new Uint256(realAmount))))
   ETH 原生：RawTransaction.createEtherTransaction(nonce, gasPrice, gasLimit, toAddress, realAmount)
3. 离线签名：byte[] raw = TransactionEncoder.signMessage(tx, chainId, credentials)
   String rawHex = Numeric.toHexString(raw)
4. 广播：web3j.ethSendRawTransaction(rawHex) → txHash
5. UPDATE t_withdraw SET tx_hash=?, status=2(处理中)  // 已上链
```

**签名校验（自检，Mock 阶段必做）**：用 `Credentials.create(signatureMessage)` 恢复出签名者地址，应与热钱包地址一致（见 §9.3）。

### 5.5 成功确认与资金扣减（成功/失败分支）

```
确认（随扫描轮询或提现专用 confirm 任务）：
  receipt = eth_getTransactionReceipt(txHash)
  - receipt.status == 0x1 且 确认数 >= t_chain.withdraw_confirmations → 成功：
        调 asset transfer（冻结 → 平台热钱包账户，永久扣减）：
        TransferRequest{ requestId = withdraw.requestId + ":W",   // 区别于 freeze
                         fromUserId = withdraw.userId,            // 冻结余额转出
                         toUserId   = hotWalletPlatformUserId,    // 平台/热钱包账户(见下)
                         symbol = withdraw.symbol,
                         amount = withdraw.amount,                // 全额
                         bizType = "WITHDRAW", refNo = withdrawId, remark="提现扣减" }
        成功 → UPDATE t_withdraw SET status=3；失败 → 保留 status=2 由补偿任务重试（幂等）
  - receipt.status == 0x0（链上失败）或广播即失败 → 失败分支：
        调 asset unfreeze（冻结 → 可用，退回用户）：
        UnfreezeRequest{ requestId = withdraw.requestId + ":U",
                         userId, symbol, amount = withdraw.amount, bizType="UNFREEZE", refNo }
        成功 → UPDATE t_withdraw SET status=5, fail_reason=...
```

> **资金衔接说明**：asset 现无「直接扣减 frozen/available」的对外端点，故**采用 freeze + transfer 组合**：
> - **freeze**（进入处理中）：用户 `available→frozen`，锁住资金。
> - **transfer**（成功确认）：`fromUserId=用户(冻结余额) → toUserId=平台热钱包账户(可用)`，单事务内 from 冻结减少 + to 可用增加，等于**永久扣减**；手续费 `fee` 本期随全额扣入平台账户，`remark` 标注（后续如需 fee 独立归集，可在平台账户内再拆，本阶段从简）。
> - **unfreeze**（失败回滚）：用户 `frozen→available`，资金退回。
> - 三个操作的 requestId 各不相同（`requestId` / `requestId+":W"` / `requestId+":U"`），互不冲突、各自幂等。
> - `toUserId`（平台热钱包账户）由配置 `chain.hot-wallet.platform-user-id` 指定；该用户 `symbol` 账户需先开户（asset `open` 幂等）。

**提现 requestId 幂等约定汇总**：

| 操作 | requestId | asset 接口 | 说明 |
|------|-----------|-----------|------|
| 提现冻结 | `withdraw.requestId` | `/internal/asset/freeze` | 进入处理中(2)时 |
| 提现成功扣减 | `withdraw.requestId + ":W"` | `/internal/asset/transfer` | 链上确认达标后，冻结→平台 |
| 提现失败回滚 | `withdraw.requestId + ":U"` | `/internal/asset/unfreeze` | 上链失败，冻结→可用 |

---

## 六、地址与私钥（热钱包）管理

- **本期**：热钱包私钥从配置文件注入（`chain.hot-wallet.private-key`，**仅 Mock/测试网**），或从本地加密文件加载；**严禁明文入库、严禁落日志**。生产需上 KMS/HSM 或加密 keystore（Phase 5）。
- **热钱包地址**：由私钥 `Credentials.getAddress()` 得出，用于提现签名；平台热钱包收款账户 `toUserId` 由 `chain.hot-wallet.platform-user-id` 配置。
- 充币地址（`address_type=1`）本期由运营预配置（§4.5），冷/热钱包 HD 派生 Phase 5。
- 建议提供 `WalletService`：`getCredentials(chainCode)`（带缓存 + 私钥解密）+ `recoverAddress(rawTx)`（签名自检）。

---

## 七、REST / 内部接口契约

> 统一返回 `com.web3.exchange.common.model.Result<T>`；金额 DTO 一律 `long` 最小单位。

### 7.1 对外 REST（经网关 `/api/chain/**`）

| 方法 | 接口 | 请求 | 返回 | 说明 |
|------|------|------|------|------|
| 查充值地址 | `GET /api/chain/deposit/address` | `userId, chainCode, symbol` | `Result<AssetAddressVO>` | 用户充币地址（未绑定返回 notFound） |
| 充值列表 | `GET /api/chain/deposit/list` | `userId, page, size` | `Result<Page<DepositVO>>` | 用户充值记录 |
| 申请提现 | `POST /api/chain/withdraw/apply` | `WithdrawApplyRequest{userId,symbol,chainCode,toAddress,amount}` | `Result<WithdrawVO>` | 落单 status=0，幂等 |
| 提现列表 | `GET /api/chain/withdraw/list` | `userId, page, size` | `Result<Page<WithdrawVO>>` | 用户提现记录 |
| 提现详情 | `GET /api/chain/withdraw/{id}` | — | `Result<WithdrawVO>` | 含 status/txHash/failReason |
| 提现审核（运营） | `POST /api/chain/withdraw/{id}/audit` | `approve=true/false, remark` | `Result<WithdrawVO>` | 审核通过→冻结上链；拒绝→status=4 |

### 7.2 内部接口（`/internal/chain/**`，服务间调用，网关不路由）

| 方法 | 接口 | 说明 |
|------|------|------|
| 触发上链 | `POST /internal/chain/withdraw/send` | 传入 `withdrawId`，将 status=2 的单签名广播（供补偿/重试） |
| 查询确认 | `GET /internal/chain/withdraw/confirm?withdrawId` | 查回执，触发成功扣减或失败回滚 |

> asset 侧已有 `/internal/asset/credit|freeze|unfreeze|transfer`，chain 作为调用方复用（见 §4.3、§5）。

### 7.3 Feign 客户端（chain 侧调 asset）

```java
@FeignClient(name = "exchange-asset", path = "/internal/asset")
public interface AssetClient {
    @PostMapping("/credit")
    Result<LedgerVO> credit(@RequestBody CreditRequest req);

    @PostMapping("/freeze")
    Result<LedgerVO> freeze(@RequestBody FreezeRequest req);

    @PostMapping("/unfreeze")
    Result<LedgerVO> unfreeze(@RequestBody UnfreezeRequest req);

    @PostMapping("/transfer")
    Result<LedgerVO> transfer(@RequestBody TransferRequest req);
}
```

---

## 八、ERC-20 / ETH 链上交互要点

### 8.1 ERC-20 `Transfer` 事件解析

- 事件签名：`Transfer(address,address,uint256)` → 哈希
  `0xddf252ad1be2c89b69c2b068fc378daa952ba7f163c4a11628f55a4df523b3ef`
- Log 结构（`eth_getLogs` 返回项）：
  - `topics[0]` = 事件签名哈希（可据此过滤 `Transfer`）
  - `topics[1]` = `from` 地址，32 字节：`0x0000...0000<20字节地址>` → 取**末 40 位 hex** 得地址
  - `topics[2]` = `to` 地址，同上取末 40 位（**命中充币地址即用户归属**）
  - `data` = `uint256` 金额，32 字节 → `Numeric.toBigInt(data)`（**代币最小单位**，与 `t_coin.decimals` 对齐）
  - `address` = 合约地址（映射到 `t_coin.symbol`），`transactionHash`、`blockNumber`、`logIndex`（同块同合约多笔时用于唯一拼接）
- 过滤条件：`eth_getLogs(fromBlock, toBlock, address=[合约地址们], topics=[[Transfer签名哈希]])`——只拉 `Transfer` 事件，且按已登记合约地址过滤，数据量可控。

### 8.2 原生 ETH 充值

- 无合约：扫 `eth_getBlockByNumber(..., fullTx=true)` 的 `transactions[]`，命中 `tx.to == 充币地址` 即一笔入账；`tx.value` 即金额（wei，18 位最小单位）。
- 需**去重**：`tx.hash` 作 `t_deposit.tx_hash`，`uk_tx_hash` 幂等兜底。

### 8.3 离线签名要点

- 代币转账 `data` 用 `FunctionEncoder.encode` 构造 `transfer(address,uint256)` ABI；参数 `Address` / `Uint256` 来自 web3j `abi` 包。
- 交易必带：`nonce`（eth_getTransactionCount(pending)）、`gasPrice`（clamp 到 t_chain 范围）、`gasLimit`（代币建议估算或固定阈值）、`chainId`（t_chain.chain_id，**必带**以抗重放，用 `signMessage(tx, chainId, cred)`）。
- 原生 ETH：`RawTransaction.createEtherTransaction`。
- 签名自检：`Credentials.create(SignedRawTransaction.decode(raw).getSignatureData())` 恢复地址比对热钱包地址。

---

## 九、Mock RPC 验证方案

> 无真实链时，**用轻量 HTTP JSON-RPC stub 模拟 web3j 所需方法**，返回预置数据驱动全流程。此为本期**主验证手段**（纯 Python 标准库即可，无需 node/额外依赖）。Node v22 已装，可选 Anvil/Hardhat 作为**更强的补充验证**（见 §9.4）。

### 9.1 Mock RPC 覆盖的方法

web3j 调 RPC 走 HTTP POST，body 为 JSON-RPC：`{"jsonrpc":"2.0","method":"<m>","params":[...],"id":n}`。stub 只需按 `method` 分派返回预置 JSON，无需解析参数。

| 方法 | 用途 | stub 返回 |
|------|------|---------|
| `eth_blockNumber` | 最新块 | `0x102`（可配递增，模拟确认数增长） |
| `eth_getLogs` | 充值事件 | 预置一条指向测试充币地址的 ERC-20 `Transfer` log |
| `eth_getBlockByNumber` | 原生币/补块（fullTx=true） | 简单块对象（可选，ERC-20 场景可省） |
| `eth_sendRawTransaction` | 提现广播 | 合法 hex 校验后返回预置 txHash |
| `eth_getTransactionReceipt` | 提现回执 | `{status:0x1, blockNumber, transactionHash}` |
| `eth_getTransactionCount` | nonce | 自增计数器 |
| `eth_chainId` | chainId | `0x1` |
| `eth_gasPrice` / `eth_estimateGas` / `eth_call` | gas | 常量（gasPrice `0x3b9aca00`=1e9，gasLimit `0x186a0`=100000） |

### 9.2 实现与运行（纯 Python 标准库）

新建 `tools/mock-rpc/mock_rpc.py`（+ `mock_fixture.json`），用 `http.server` 实现 `do_POST` 按 `method` 分派。示例骨架：

```python
# tools/mock-rpc/mock_rpc.py  —— 纯标准库 JSON-RPC stub
import json, re
from http.server import BaseHTTPRequestHandler, HTTPServer

def transfer_log():
    return {  # 构造一条指向测试充币地址的 USDT Transfer
        "address": "0xdac17f958d2ee523a2206206994597c13d831ec7",  # USDT(0xdAC17F...) 小写
        "topics": [
            "0xddf252ad1be2c89b69c2b068fc378daa952ba7f163c4a11628f55a4df523b3ef",
            "0x0000000000000000000000001111111111111111111111111111111111111111",  # from
            "0x000000000000000000000000AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA",  # to=充币地址
        ],
        "data": "0x00000000000000000000000000000000000000000000000000000000000f4240",  # 1e6 USDT
        "transactionHash": "0x" + "ab"*32,
        "blockNumber": "0x100",
        "logIndex": "0x0",
    }

def dispatch(method):
    if method == "eth_blockNumber":        return "0x102"
    if method == "eth_getLogs":            return {"result": [transfer_log()]}
    if method == "eth_chainId":            return "0x1"
    if method == "eth_getTransactionCount": return "0x5"
    if method == "eth_gasPrice":           return "0x3b9aca00"
    if method == "eth_estimateGas":        return "0x186a0"
    if method == "eth_sendRawTransaction": return {"result": "0x" + "cd"*32}  # 校验 hex 后返回 txHash
    if method == "eth_getTransactionReceipt":
        return {"result": {"transactionHash": "0x"+"cd"*32, "blockNumber":"0x102",
                           "status":"0x1"}}
    raise KeyError(method)

class H(BaseHTTPRequestHandler):
    def do_POST(self):
        body = json.loads(self.rfile.read(int(self.headers["Content-Length"])))
        try:
            res = {"jsonrpc":"2.0","id":body.get("id"),
                   "result": dispatch(body.get("method"))}
        except KeyError:
            res = {"jsonrpc":"2.0","id":body.get("id"),
                   "error":{"code":-32601,"message":"method not found"}}
        data = json.dumps(res).encode()
        self.send_response(200); self.send_header("Content-Type","application/json")
        self.send_header("Content-Length", str(len(data))); self.end_headers()
        self.wfile.write(data)

if __name__ == "__main__":
    HTTPServer(("127.0.0.1", 9099), H).serve_forever()
```

运行：`python3 tools/mock-rpc/mock_rpc.py`（监听 `127.0.0.1:9099`）。`t_chain.rpc_url` 配置为 `http://127.0.0.1:9099`。**注意**：web3j `HttpService` 走 HTTP，mock 无需 HTTPS。

### 9.3 验证步骤

**充值验证（扫到 → 确认 → credit 入账 → asset 余额增加）**

1. 预置数据：
   - `t_chain`：`chain_code=ETH`、`chain_type=EVM`、`chain_id=1`、`rpc_url=http://127.0.0.1:9099`、`confirmations=2`、`scan_enabled=1`、`status=1`。
   - `t_coin`：`symbol=USDT`、`chain_code=ETH`、`coin_type=TOKEN`、`contract_address=0xdAC17F...7ec7`、`decimals=6`、`deposit_enabled=1`。
   - `t_asset_address`：`user_id=100`、`chain_code=ETH`、`symbol=USDT`、`address=0xAAAA...AAAA`（与 mock 里 to 一致）、`address_type=1`、`is_active=1`。
2. 启动 `exchange-chain`（游标缺失 → 从 0 续扫）。`eth_blockNumber=0x102`、log 的 `blockNumber=0x100` → `confirmations = 0x102-0x100+1 = 3 ≥ 2` → 触发 credit。
3. 断言：
   - `t_deposit` 出现一行 `status=2`、`ledger_id` 非空、`tx_hash` 为 mock 的 `0xab...`。
   - `GET /internal/asset/account/balance?userId=100&symbol=USDT` → `available = 1_000_000`（1 USDT）。
   - **幂等**：重复触发扫描，`t_deposit` 仍 1 行、余额仍 1_000_000（`uk_tx_hash` + credit requestId 双重拦截）。
4. **确认数递增**：把 `eth_blockNumber` 调小（如 0x100）使 `confirmations=1 < 2`，重扫后记录保持 `status=1` 不入账；再调大（0x103）验证入账。

**提现验证（冻结 → 离线签名 → 广播 → 扣减/失败回滚）**

1. 预置：热钱包私钥配置；用户 100 的 USDT 账户先有可用余额（可先调 asset `credit` 或用 freeze/credit 造数）。
2. `POST /api/chain/withdraw/apply`（amount=1_000_000）→ 落单 `status=0`。
3. `audit(approve=true)` → 断言：
   - asset 调了 `freeze`：用户 USDT `available` 减 1_000_000、`frozen` 加 1_000_000；`t_withdraw.freeze_ledger_id` 非空、`status=2`。
4. 上链：
   - 断言 `eth_sendRawTransaction` 收到的 `rawHex` 是**合法 hex** 且**长度正确**（ERC-20 交易 data 段含 `transfer` 函数选择子 + 目标地址 + 金额）；
   - **签名自检**：对 raw 解码后 `Credentials.create(sig)` 恢复的地址 == 热钱包地址（证明离线签名正确、未改错链/错 nonce）。
   - mock 返回 `txHash`；`eth_getTransactionReceipt.status=0x1` 且确认达标 → 调 `transfer`（冻结→平台账户）：断言用户 USDT `frozen` 归 0、`available` 不变、平台热钱包账户 `available` 增 1_000_000；`t_withdraw.status=3`、`tx_hash` 非空。
5. **失败回滚分支**：把 mock `eth_getTransactionReceipt.status` 改为 `0x0`，重跑一条 → 断言调 `unfreeze`：用户 USDT `frozen` 归 0、`available` 恢复；`t_withdraw.status=5`、`fail_reason` 非空。
6. **幂等**：同一 `withdraw.requestId` 重复 freeze/transfer/unfreeze 不重复扣减。

### 9.4 可选：本地测试链（Anvil / Hardhat）—— 标注是否可用

- **Node 环境**：本机 `node v22.23.1` / `npm` / `npx` **已就绪** ✅。
- **Anvil / Hardhat 未安装**：需先 `npm install -g ganache`（或 `curl -L https://foundry.paradigm.xyz | bash && foundryup` 装 anvil），或 `npx hardhat node`（首次联网拉包）。**联网/安装后可选用**，作为最强验证：可真实部署一个 USDT 合约（OpenZeppelin `MockERC20`）、往充币地址 mint，web3j 扫真日志、真签名、真广播、真回执。
- **取舍**：Mock RPC 零依赖、秒级起效，覆盖「扫描→入账」与「签名→广播→扣减」逻辑正确性；Anvil 进一步验证 **ABI/编码/签名与真实链一致**（防 mock 掩盖编码 bug）。建议 **先跑 Mock（必做），有网再补 Anvil 冒烟（可选）**。

---

## 十、落地 Checklist（/dev 实施指引）

### 依赖与配置
- [ ] 父 pom `dependencyManagement` 增加 `org.web3j:core:${web3j.version}`（版本属性已在）。
- [ ] `exchange-chain/pom.xml` 增加 `org.web3j:core` 依赖（版本由父管理），并补齐与 asset 对齐的 web 依赖组。
- [ ] 修正 `application.yml`：`spring.application.name=exchange-chain`、`server.port=8105`、MyBatis-Plus/数据源/Nacos/Redis/RocketMQ/`chain.scan.*`（enabled/interval-ms/batch-size/start-block/safety-window）、`chain.hot-wallet.*`（platform-user-id/private-key[仅测试网]）。
- [ ] `Web3jConfig`：按启用链构造 `Web3j`，注册 `ChainRegistry`。

### 实体与 Mapper（读/写 asset 库既有表，**不建新表**）
- [ ] 实体 `Chain`/`Coin`/`Deposit`/`Withdraw`/`AssetAddress`（`@TableName` 指向既有表，金额 `Long`、继承 `BaseEntity`）。
- [ ] Mapper：`t_deposit` 按链+状态查未入账、按 txHash 幂等查、`max(block_height)` 补块起点；`t_withdraw` 按状态+时间查待处理/待确认；`t_asset_address` 按 `(chain_code,address,address_type=1,is_active=1)` 精确查归属用户；`t_coin`/`t_chain` 按链查。

### 充值
- [ ] `BlockScanner`：按 `t_chain.scan_enabled=1` 轮询，`eth_blockNumber` + `eth_getLogs`(ERC-20 Transfer) + 原生币块扫；Redis 游标 `chain:scan:{chain}` 断点续扫；启动缺失游标按 `max(block_height)` 回退 `safety-window` 补块。
- [ ] `DepositService.handleLog`：命中充币地址 INSERT `t_deposit(status=0)`，`uk_tx_hash` 幂等；确认数递增；达标后 `credit`（requestId=`DEP:{id}`）→ `status=2, ledger_id`；失败保留重试。

### 提现
- [ ] `WithdrawService.apply`（幂等落单，fee/real_amount 计算）、`audit`（通过→`freeze`→上链；拒绝→status=4）。
- [ ] `WithdrawBroadcaster`：nonce/gasPrice(范围 clamp)/gasLimit/chainId，ERC-20 `transfer` ABI 编码，`TransactionEncoder.signMessage(tx,chainId,credentials)` 离线签名，`ethSendRawTransaction` 记 `tx_hash`。
- [ ] 确认任务：`eth_getTransactionReceipt` → 成功 `transfer`（`requestId+":W"`）扣减→status=3；失败 `unfreeze`（`requestId+":U"`）回滚→status=5。
- [ ] 补偿：status=2 且超时未确认的定时重查/重发（幂等）。

### 钱包
- [ ] `WalletService.getCredentials`（私钥加载，非明文、缓存）+ `recoverAddress`（签名自检）。

### 接口
- [ ] 对外 `/api/chain/**`：deposit/address、deposit/list、withdraw/apply、withdraw/list、withdraw/{id}、withdraw/{id}/audit。
- [ ] 内部 `/internal/chain/**`：withdraw/send、withdraw/confirm。
- [ ] Feign `AssetClient`（credit/freeze/unfreeze/transfer），requestId 按 §5.5 约定。

### 验证
- [ ] 启动 `tools/mock-rpc/mock_rpc.py`（`127.0.0.1:9099`），按 §9.3 跑通充值（扫到→确认→credit→余额+1e6；幂等不重复）。
- [ ] 跑通提现（apply→审核冻结→离线签名自检通过→广播→transfer 扣减→status=3）；改 mock status=0x0 验证失败回滚（unfreeze→status=5）；幂等不重复扣减。
- [ ] （可选）联网安装 anvil/ganache，部署 MockERC20 + mint 充币地址，做真实链冒烟。
- [ ] `mvn -pl exchange-chain -am package -DskipTests`（JAVA_HOME=temurin-17）编译通过；`java -jar exchange-chain/target/exchange-chain-1.0.0.jar` 启动。

> 本文件为链上域落地依据；若实现中需调整契约/状态机，请同步更新本文件与 `docs/ARCHITECTURE.md`，保持与 `docs/PROJECT_MEMORY.md` 一致。
