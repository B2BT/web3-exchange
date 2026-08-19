#!/usr/bin/env node
/**
 * 冷钱包离线签名工具（演示真实"离线签名"工作流，不触碰服务器私钥）。
 *
 * 用途：管理平台拉取待冷签提现清单 → 此工具在【离线/隔离环境】输入冷钱包私钥，
 *      对 unsigned tx 签名 → 输出 signedRawHex → 回填提交。
 *
 * 用法（离线环境运行，私钥仅存在于本机，不上服务器）：
 *   node cold-sign.js sign <signedRawUnsignedHex> [coldPrivKey]
 *   node cold-sign.js verify <signedRawHex> <expectedFrom>
 *   node cold-sign.js demo   # 本地演示：用 hot 私钥签名 unsigned → 输出 signedRawHex
 */
const { ethers, Wallet } = require("ethers");

const HOT_PRIV = "0xac0974bec39a17e36ba4a6b4d238ff944bacb478cbed5efcae784d7bf4f2ff80";

async function main() {
  const [cmd, arg1, arg2] = process.argv.slice(2);
  if (!cmd) { console.log(usage()); return; }

  if (cmd === "demo") {
    // 演示：用 hot 私钥对 unsigned tx 签名（生产用 cold 私钥）
    const unsignedHex = arg1;
    if (!unsignedHex) { console.log("用法: node cold-sign.js demo <unsignedTxHex>"); return; }
    const wallet = new Wallet(HOT_PRIV);
    const signed = await wallet.signTransaction(unsignedHex);
    const from = wallet.address;
    console.log(JSON.stringify({ signedRawHex: signed, from, hint: "生产用冷钱包私钥在离线环境签名" }, null, 2));
    return;
  }

  if (cmd === "sign") {
    const unsignedHex = arg1, priv = arg2 || HOT_PRIV;
    const wallet = new Wallet(priv);
    const signed = await wallet.signTransaction(unsignedHex);
    console.log(JSON.stringify({ signedRawHex: signed, from: wallet.address }, null, 2));
    return;
  }

  if (cmd === "verify") {
    const signedHex = arg1, expectFrom = arg2;
    const parsed = ethers.Transaction.from(signedHex);
    const from = parsed.from;
    console.log(JSON.stringify({ txHash: parsed.hash, from, expectedFrom: expectFrom,
      match: !expectFrom || from.toLowerCase() === expectFrom.toLowerCase() }, null, 2));
  }
}

function usage() { return "冷钱包离线签名工具\n用法:\n  node cold-sign.js demo <unsignedTxHex>    # 签名演示(生产用冷私钥)\n  node cold-sign.js sign <unsignedTxHex> [priv]\n  node cold-sign.js verify <signedRawHex> <expectFrom>"; }

main().catch(e => { console.error("失败:", e.message); process.exit(1); });
