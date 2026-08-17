# Solidity 智能合约（DeFi 演示）

> 为 web3-exchange 补充 Solidity 合约开发能力，贴合 JD「熟悉 Solidity、理解 DeFi 核心逻辑」。
> 两个真实可编译、可部署、可交互的合约，演示 ERC-20 质押与 AMM 做市两种 DeFi 核心模式。

## 目录

```
contracts/
├── StakingToken.sol     # ERC-20 质押代币合约（质押/解质押/奖励）
├── SimpleAMM.sol        # 简化 AMM 做市合约（Uniswap V2 恒定乘积）
├── deploy.js            # ethers 部署脚本（连本地链/测试网）
├── hardhat.config.js    # Hardhat 配置（solc 0.8.20 + 优化器）
└── artifacts/           # solc 编译产物（.abi + .bin）
```

## StakingToken.sol —— ERC-20 质押代币

**功能**
- ERC-20 标准：transfer/approve/transferFrom/mint/burn
- **质押** `stake(amount)`：代币进合约，开始累计奖励
- **解质押** `unstake(amount)`：先结算奖励，退还代币
- **领取奖励** `claimReward()`：把累计奖励铸造给用户

**奖励模型**：按「单位时间每质押量」线性累计（`rewardRate=1e12/块`），用块号差结算
```solidity
// pendingReward: 结算前应得奖励
accrued = stakedAmount * rewardRate * (block.number - lastUpdateBlock)
rewardDebt += accrued   // 结算时累入
```

**安全**：checks-effects-interactions 防重入；结算先于资金转移。

## SimpleAMM.sol —— 恒定乘积 AMM

**核心公式**：`x * y = k`（Uniswap V2）

- **添加流动性** `addLiquidity`：按储备比例注入两币，铸造 LP 份额（首笔=几何平均，后续=按比例 min）
- **移除流动性** `removeLiquidity`：按 LP 份额比例提取两币
- **兑换** `swap`：输入单边代币，`amountOut = amountIn * reserveOut / (reserveIn + amountIn)`，用实际余额差算真实入账（防税币）
- **查价** `getAmountOut`：模拟价格影响

## 编译 / 部署 / 交互

```bash
# 编译（solc 0.8.20，生成 artifacts/）
npx --yes solc@0.8.20 --bin --abi -o /tmp/solc-out StakingToken.sol SimpleAMM.sol

# 启动本地链（hardhat）
./node_modules/.bin/hardhat node --port 8545

# 部署（连 hardhat，用其默认账户私钥）
PRIVATE_KEY="0xac0974bec39a17e36ba4a6b4d238ff944bacb478cbed5efcae784d7bf4f2ff80" \
RPC_URL="http://127.0.0.1:8545" node deploy.js
```

## 验证结果（hardhat 本地链）

- ✅ **StakingToken 编译/部署/交互成功**：totalSupply=1000000 STK，质押 100 STK → 质押量 100，奖励按块累计正确
- ✅ **SimpleAMM 编译/部署成功**：token0/token1 读取正确

## 说明

- 合约用 solc 0.8.20 + OpenZeppelin 风格，可接 web3j（用 `web3j generate` 从 .abi 生成 Java wrapper）集成到 exchange-chain
- mock-rpc（127.0.0.1:9099）单线程 + eth_call 固定返回，部署真实验证建议用 hardhat/anvil 或测试网
