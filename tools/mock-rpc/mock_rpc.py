#!/usr/bin/env python3
"""
mock_rpc.py —— 纯 Python 标准库 JSON-RPC stub，用于在无真实链环境下驱动 web3j 充值/提现全流程。

监听 127.0.0.1:9099，按 method 分派返回预置数据。运行状态（最新块、回执状态、日志块高）从
同目录 mock_state.json 实时读取（每次请求重新加载），便于不重启调整以验证确认数递增/失败回滚分支。

用法：
    python3 tools/mock-rpc/mock_rpc.py            # 监听 127.0.0.1:9099

关键预置：
  - 一条指向测试充币地址 0xAAAA...AAAA 的 USDT(0xdac17f...) ERC-20 Transfer log（block 0x100, amount 1e6）
  - eth_blockNumber = latest_block（默认 0x102，比 log 高 2 块 → confirmations=3 ≥ required=2）
  - eth_sendRawTransaction 校验 hex 后返回 txHash 0xcd*32
  - eth_getTransactionReceipt status = receipt_status（默认 0x1 成功）
"""
import json
import os
import re
import threading
from http.server import BaseHTTPRequestHandler, HTTPServer

BASE_DIR = os.path.dirname(os.path.abspath(__file__))
STATE_FILE = os.path.join(BASE_DIR, "mock_state.json")
DEFAULT_STATE = {
    "latest_block": 258,          # 0x102
    "log_block_number": 256,      # 0x100
    "receipt_status": "0x1",      # 0x0 可切换失败回滚
    "deposit_address": "0xAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA",
    "deposit_contract": "0xdac17f958d2ee523a2206206994597c13d831ec7",
    "deposit_amount_hex": "0x00000000000000000000000000000000000000000000000000000000000f4240",  # 1e6
    "deposit_tx_hash": "0x" + "ab" * 32,
    "broadcast_tx_hash": "0x" + "cd" * 32,
    "nonce": 5,
    # ===== NFT（ERC-721）模拟充值 =====
    "nft_contract": "0x5B38Da6a701c568545dCfcB03FcB875f56beddC4",   # 模拟 NFT 合约
    "nft_token_id_hex": "0x0000000000000000000000000000000000000000000000000000000000000042",  # tokenId=66
    "nft_deposit_tx_hash": "0x" + "ef" * 32,
    "nft_log_block_number": 254,   # 0xfe，低于 latest_block 使确认数达标
    "nft_log_index": "0x1",
}

_lock = threading.Lock()


def load_state():
    try:
        with open(STATE_FILE, "r", encoding="utf-8") as f:
            st = json.load(f)
            merged = dict(DEFAULT_STATE)
            merged.update(st)
            return merged
    except Exception:
        return dict(DEFAULT_STATE)


def to_hex(num):
    return hex(num)


def transfer_log(st):
    return {
        "address": st["deposit_contract"],
        "topics": [
            "0xddf252ad1be2c89b69c2b068fc378daa952ba7f163c4a11628f55a4df523b3ef",
            "0x0000000000000000000000001111111111111111111111111111111111111111",  # from
            "0x000000000000000000000000" + st["deposit_address"][2:].lower().ljust(40, "a"),  # to
        ],
        "data": st["deposit_amount_hex"],
        "transactionHash": st["deposit_tx_hash"],
        "blockNumber": to_hex(st["log_block_number"]),
        "logIndex": "0x0",
    }


def nft_transfer_log(st):
    """ERC-721 Transfer(from,to,tokenId)：topics[3] 为 tokenId，data 为空，amount 概念为 1。"""
    return {
        "address": st["nft_contract"],
        "topics": [
            "0xddf252ad1be2c89b69c2b068fc378daa952ba7f163c4a11628f55a4df523b3ef",
            "0x0000000000000000000000001111111111111111111111111111111111111111",  # from
            "0x000000000000000000000000" + st["deposit_address"][2:].lower().ljust(40, "a"),  # to
            st["nft_token_id_hex"],   # tokenId=66
        ],
        "data": "0x",
        "transactionHash": st["nft_deposit_tx_hash"],
        "blockNumber": to_hex(st["nft_log_block_number"]),
        "logIndex": st["nft_log_index"],
    }


def block_for(st, num):
    return {
        "number": to_hex(num),
        "hash": "0x" + ("aa" * 32),
        "parentHash": "0x" + ("bb" * 32),
        "timestamp": "0x" + to_hex(num).lstrip("0x") or "0x0",
        "transactions": [],
    }


def dispatch(method, params):
    st = load_state()
    if method == "eth_blockNumber":
        return to_hex(st["latest_block"])
    if method == "eth_getLogs":
        # 返回 ERC-20 与 ERC-721(NFT) 两条 Transfer 日志，驱动两类充值扫描验证
        return [transfer_log(st), nft_transfer_log(st)]
    if method == "eth_getBlockByNumber":
        # fullTx=false 返回 tx hashes 数组；fullTx=true 返回对象数组（此处给空，原生币验证可扩展）
        return block_for(st, int(params[0], 16))
    if method == "eth_chainId":
        return "0x1"
    if method == "eth_getTransactionCount":
        return to_hex(st["nonce"])
    if method == "eth_getBalance":
        return "0x0de0b6b3a7640000"  # 1e18 wei (mock 固定余额)
    if method == "eth_gasPrice":
        return "0x3b9aca00"  # 1e9 wei
    if method == "eth_estimateGas":
        return "0x186a0"  # 100000
    if method == "eth_sendRawTransaction":
        raw = params[0] if params else ""
        if not re.fullmatch(r"0x[0-9a-fA-F]+", raw or ""):
            raise ValueError("invalid raw transaction hex")
        if len(raw) <= 2:
            raise ValueError("empty raw transaction")
        return st["broadcast_tx_hash"]
    if method == "eth_getTransactionReceipt":
        return {
            "transactionHash": st["broadcast_tx_hash"],
            "blockNumber": to_hex(st["latest_block"]),
            "status": st["receipt_status"],
            "gasUsed": "0x5208",
        }
    if method == "eth_call":
        return "0x0000000000000000000000000000000000000000000000000000000000000000"
    raise KeyError(method)


class H(BaseHTTPRequestHandler):
    def log_message(self, *args):
        pass

    def do_POST(self):
        length = int(self.headers.get("Content-Length", 0))
        body = json.loads(self.rfile.read(length) if length else b"{}")
        try:
            res = {"jsonrpc": "2.0", "id": body.get("id"),
                   "result": dispatch(body.get("method"), body.get("params") or [])}
        except KeyError:
            res = {"jsonrpc": "2.0", "id": body.get("id"),
                   "error": {"code": -32601, "message": "method not found: %s" % body.get("method")}}
        except Exception as e:
            res = {"jsonrpc": "2.0", "id": body.get("id"),
                   "error": {"code": -32000, "message": str(e)}}
        data = json.dumps(res).encode()
        self.send_response(200)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(data)))
        self.end_headers()
        self.wfile.write(data)


if __name__ == "__main__":
    if not os.path.exists(STATE_FILE):
        with open(STATE_FILE, "w", encoding="utf-8") as f:
            json.dump(DEFAULT_STATE, f, indent=2, ensure_ascii=False)
        print("已生成默认 mock_state.json")
    print("Mock RPC listening on 127.0.0.1:9099 ...")
    HTTPServer(("127.0.0.1", 9099), H).serve_forever()
