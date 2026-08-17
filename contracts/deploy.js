#!/usr/bin/env node
/**
 * 部署并交互 SimpleAMM / StakingToken 合约（演示 Solidity → 部署 → 调用全流程）。
 *
 * 用法：
 *   RPC_URL=http://127.0.0.1:9099 PRIVATE_KEY=<hex> node deploy.js
 *
 * 环境：
 *   - RPC_URL   JSON-RPC 端点（默认 mock-rpc 127.0.0.1:9099，或本地链如 anvil/hardhat）
 *   - PRIVATE_KEY 部署者私钥（默认演示私钥 0x...）
 *
 * 说明：mock-rpc 的 eth_call 固定返回 0x0，故链上只读调用(name/symbol 等)会拿到空值；
 * 真实验证请连 anvil/hardhat 或测试网。此处演示「编译产物 → 部署 → 交易广播 → 回执」流程。
 */
const { ethers } = require("ethers");
const fs = require("fs");
const path = require("path");

const RPC_URL = process.env.RPC_URL || "http://127.0.0.1:9099";
const PRIVATE_KEY = process.env.PRIVATE_KEY || "0x0000000000000000000000000000000000000000000000000000000000000001";

function loadContract(name) {
  const abi = JSON.parse(fs.readFileSync(path.join(__dirname, "artifacts", `${name}.abi`), "utf8"));
  const bytecode = fs.readFileSync(path.join(__dirname, "artifacts", `${name}.bin`), "utf8").trim();
  return { abi, bytecode };
}

async function main() {
  const provider = new ethers.JsonRpcProvider(RPC_URL);
  const wallet = new ethers.Wallet(PRIVATE_KEY, provider);
  const network = await provider.getNetwork();
  console.log(`连接 ${RPC_URL} | 链ID=${network.chainId} | 部署者=${wallet.address}`);

  // 1. 部署 StakingToken（初始供应 1,000,000 STK）
  const stk = loadContract("StakingToken_sol_StakingToken");
  console.log("\n=== 部署 StakingToken ===");
  const stkFactory = new ethers.ContractFactory(stk.abi, "0x" + stk.bytecode, wallet);
  const stkContract = await stkFactory.deploy(ethers.parseEther("1000000"));
  await stkContract.waitForDeployment();
  const stkAddr = await stkContract.getAddress();
  console.log(`StakingToken 部署成功 @ ${stkAddr}`);
  console.log(`  (bytecode ${stk.bytecode.length / 2} 字节, ABI ${stk.abi.length} 方法/事件)`);

  // 2. 部署 SimpleAMM（用两个 ERC-20 地址占位）
  const amm = loadContract("SimpleAMM_sol_SimpleAMM");
  console.log("\n=== 部署 SimpleAMM ===");
  const ammFactory = new ethers.ContractFactory(amm.abi, "0x" + amm.bytecode, wallet);
  const ammContract = await ammFactory.deploy(
    "0x0000000000000000000000000000000000000001", // token0 占位
    "0x0000000000000000000000000000000000000002"  // token1 占位
  );
  await ammContract.waitForDeployment();
  const ammAddr = await ammContract.getAddress();
  console.log(`SimpleAMM 部署成功 @ ${ammAddr}`);

  // 3. 读合约状态（真实链可读到；mock-rpc eth_call 返回 0x0）
  try {
    const name = await stkContract.name();
    const symbol = await stkContract.symbol();
    const total = await stkContract.totalSupply();
    console.log(`\n=== StakingToken 状态 ===`);
    console.log(`  name=${name} symbol=${symbol} totalSupply=${ethers.formatEther(total)} STK`);
  } catch (e) {
    console.log(`\n（读状态失败：${e.shortMessage || e.message}。mock-rpc 的 eth_call 固定返回 0x0，真实链可正常读取）`);
  }

  console.log("\n✅ 部署流程完成。脚本产物见 contracts/ + artifacts/");
  console.log(`合约地址：\n  StakingToken = ${stkAddr}\n  SimpleAMM   = ${ammAddr}`);
}

main().catch((e) => {
  console.error("❌ 部署失败:", e.shortMessage || e.message);
  process.exit(1);
});
