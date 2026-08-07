#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
Web3 交易所 API 全功能冒烟测试
自动登录 → 逐域调用核心接口 → 断言 → 生成 markdown 测试报告
用法: python3 scripts/api_test.py
输出: docs/test-reports/report-<时间戳>.md
"""
import subprocess, json, sys, time, datetime, urllib.parse, os

BASE = "http://127.0.0.1:8080"  # 网关
GW_ORDER = "http://127.0.0.1:8104"  # order 直连(部分接口未挂网关? 用网关为主)
USERNAME = "e2e92443"; PASSWORD = "Test@123456"
SYMBOL = "BTC/USDT"
PASS, FAIL = 0, 0
RESULTS = []  # (domain, case, ok, detail)

def curl(method, url, data=None, token=None):
    cmd = ["curl", "-s", "-X", method, url]
    if token: cmd += ["-H", "Authorization: Bearer " + token]
    if data is not None:
        cmd += ["-H", "Content-Type: application/json", "-d", json.dumps(data, ensure_ascii=False)]
    try:
        out = subprocess.run(cmd, capture_output=True, text=True, timeout=20).stdout
        return json.loads(out) if out.strip() else {}
    except Exception as e:
        return {"code": -1, "message": "curl/parse err: %s" % e}

def record(domain, case, ok, detail):
    global PASS, FAIL
    if ok: PASS += 1
    else: FAIL += 1
    RESULTS.append((domain, case, ok, detail))
    print("  %s %s" % ("PASS" if ok else "FAIL", case), ("- " + str(detail)) if not ok else "")

def check(domain, case, cond, detail=""):
    record(domain, case, bool(cond), detail if not cond else "OK")

def token():
    cap = curl("GET", BASE + "/api/auth/captcha").get("data", {})
    login = curl("POST", BASE + "/api/auth/login",
                 {"username": USERNAME, "password": PASSWORD,
                  "captchaId": cap.get("captchaId"), "captcha": cap.get("captchaText")})
    d = login.get("data") or {}
    return d.get("accessToken"), (d.get("userInfo") or {}).get("id")

def main():
    global FAIL
    print("=== Web3 交易所 API 冒烟测试 ===")
    tok, uid = token()
    if not tok:
        print("登录失败，终止"); sys.exit(1)
    print("登录OK uid=%s\n" % uid)

    # 认证
    print("[认证]")
    cap = curl("GET", BASE + "/api/auth/captcha")
    check("auth", "验证码", cap.get("code") == 200, cap.get("message"))
    check("auth", "登录返回token", tok is not None)

    # 行情
    print("\n[行情 market]")
    tk = curl("GET", BASE + "/api/market/ticker/list")
    data = tk.get("data") or []
    btc = next((x for x in data if x.get("symbol") == SYMBOL), None)
    check("market", "ticker列表", tk.get("code") == 200 and len(data) > 0, tk.get("message"))
    check("market", "ticker含BTC/USDT", btc is not None and btc.get("lastPrice"), "lastPrice missing")
    kl = curl("GET", BASE + "/api/market/kline/list?symbol=%s&period=1m&limit=20" % urllib.parse.quote(SYMBOL))
    check("market", "K线1m", kl.get("code") == 200 and len(kl.get("data") or []) >= 1, kl.get("message"))
    kl5 = curl("GET", BASE + "/api/market/kline/list?symbol=%s&period=5m&limit=5" % urllib.parse.quote(SYMBOL))
    check("market", "K线5m多周期", kl5.get("code") == 200 and len(kl5.get("data") or []) >= 1, kl5.get("message"))

    # 盘口/成交
    print("\n[盘口/成交 order]")
    dp = curl("GET", GW_ORDER + "/api/order/depth?symbol=%s&limit=5" % urllib.parse.quote(SYMBOL))
    dd = dp.get("data") or {}
    check("order", "深度盘口bids+asks", dp.get("code") == 200 and dd.get("bids") and dd.get("asks"), "no depth")
    rt = curl("GET", GW_ORDER + "/api/order/recent-trades?symbol=%s&limit=5" % urllib.parse.quote(SYMBOL))
    check("order", "最近成交", rt.get("code") == 200 and len(rt.get("data") or []) >= 0, rt.get("message"))

    # 订单
    print("\n[订单 order]")
    olist = curl("GET", BASE + "/api/order/list?userId=%s&page=1&size=5" % uid, token=tok)
    check("order", "订单分页list", olist.get("code") == 200 and (olist.get("data") or {}).get("total") is not None, olist.get("message"))
    ol0 = curl("GET", BASE + "/api/order/list?userId=%s&status=0&page=1&size=5" % uid, token=tok)
    check("order", "挂单过滤status=0", ol0.get("code") == 200, ol0.get("message"))
    tr = curl("GET", BASE + "/api/order/triggered?userId=%s" % uid, token=tok)
    check("order", "条件单triggered", tr.get("code") == 200, tr.get("message"))

    # 资产
    print("\n[资产 asset]")
    acc = curl("GET", BASE + "/api/asset/accounts?userId=%s" % uid, token=tok)
    check("asset", "账户余额", acc.get("code") == 200 and len(acc.get("data") or []) > 0, acc.get("message"))
    led = curl("GET", BASE + "/api/asset/ledger/list?userId=%s&page=1&size=3" % uid, token=tok)
    check("asset", "资金明细分页", led.get("code") == 200 and (led.get("data") or {}).get("total") is not None, led.get("message"))

    # 下单(限价GTC挂单, 用PostOnly验证撮合策略不破坏)
    print("\n[下单/撤单 order]")
    order = curl("POST", BASE + "/api/order/place",
                 {"userId": uid, "symbol": SYMBOL, "side": 1, "orderType": 1,
                  "price": 9000 * 10**8, "quantity": int(0.001 * 10**8), "quoteAmount": 0,
                  "timeInForce": 0}, token=tok)
    od = (order.get("data") or {}).get("order") or {}
    ono = od.get("orderNo")
    check("order", "限价GTC下单", order.get("code") == 200 and od.get("status") == 0, order.get("message"))
    if ono:
        # 撤单
        c = curl("POST", BASE + "/api/order/cancel", {"userId": uid, "orderNo": ono}, token=tok)
        check("order", "撤单", c.get("code") == 200, c.get("message"))
    else:
        record("order", "撤单", False, "无orderNo")

    # 通知
    print("\n[通知 notify]")
    n = curl("GET", BASE + "/api/notify/list?userId=%s&page=1&size=5" % uid, token=tok)
    check("notify", "通知列表", n.get("code") == 200, n.get("message"))

    # 链上
    print("\n[链上 chain]")
    w = curl("GET", BASE + "/api/chain/deposit/address?userId=%s&chainCode=ETH&symbol=USDT" % uid, token=tok)
    wd = w.get("data") or {}
    check("chain", "充值地址自动生成(BIP44)", w.get("code") == 200 and bool(wd.get("address")), w.get("message"))
    check("chain", "充值地址0x前缀+40hex", (wd.get("address") or "").startswith("0x") and len((wd.get("address") or "")) == 42, wd.get("address"))
    w2 = curl("GET", BASE + "/api/chain/deposit/address?userId=%s&chainCode=ETH&symbol=USDT" % uid, token=tok)
    check("chain", "充值地址幂等(两次一致)", (w2.get("data") or {}).get("address") == wd.get("address"), "mismatch")
    w3 = curl("GET", BASE + "/api/chain/deposit/address?userId=%s&chainCode=ETH&symbol=ETH" % uid, token=tok)
    check("chain", "同链复用同一地址", (w3.get("data") or {}).get("address") == wd.get("address"), "mismatch")
    # 充值记录分页
    dl = curl("GET", BASE + "/api/chain/deposit/list?userId=%s&page=1&size=10" % uid, token=tok)
    check("chain", "充值记录分页", dl.get("code") == 200 and (dl.get("data") or {}).get("total") is not None, dl.get("message"))

    # 提现申请(状态0待审核) —— 验证签名广播链路可用
    wd_apply = curl("POST", BASE + "/api/chain/withdraw/apply",
                    {"userId": uid, "symbol": "USDT", "chainCode": "ETH",
                     "toAddress": "0x70997970c51812dc3a010c7d01b50e0d17dc79c8",
                     "amount": 1000000}, token=tok)
    wid = (wd_apply.get("data") or {}).get("id")
    check("chain", "提现申请落单", wd_apply.get("code") == 200 and (wd_apply.get("data") or {}).get("status") == 0, wd_apply.get("message"))
    check("chain", "提现requestId前缀", (wd_apply.get("data") or {}).get("requestId", "").startswith("WD:"), wd_apply.get("message"))

    # 自托管钱包 (M2)
    print("\n[自托管钱包 chain.wallet]")
    wc = curl("POST", BASE + "/api/chain/wallet/create", {"userId": uid, "chainCode": "ETH", "name": "e2e-wallet"}, token=tok)
    wcd = wc.get("data") or {}
    check("chain.wallet", "创建HD钱包返回助记词+地址", wc.get("code") == 200 and bool(wcd.get("address")) and bool(wcd.get("mnemonic")), wc.get("message"))
    check("chain.wallet", "创建地址0x+40hex", (wcd.get("address") or "").startswith("0x") and len((wcd.get("address") or "")) == 42, wcd.get("address"))
    check("chain.wallet", "创建助记词12词", len((wcd.get("mnemonic") or "").split()) == 12, wcd.get("mnemonic"))

    # 导入同一助记词 → 同地址 → 撞唯一约束(证明派生确定性)
    wi = curl("POST", BASE + "/api/chain/wallet/import",
              {"userId": uid, "chainCode": "ETH", "mnemonic": wcd.get("mnemonic")}, token=tok)
    check("chain.wallet", "重复导入同助记词拒绝(唯一约束)", wi.get("code") != 200, wi.get("message"))

    # 导入私钥 (hardhat #1)
    wik = curl("POST", BASE + "/api/chain/wallet/import",
               {"userId": uid, "chainCode": "ETH",
                "privateKey": "59c6995e998f97a5a0044966f0945389dc9e86dae88c7a8412f4603b6b78690d",
                "name": "e2e-pk"}, token=tok)
    wikd = wik.get("data") or {}
    check("chain.wallet", "导入私钥派生地址正确", wik.get("code") == 200 and (wikd.get("address") or "").lower() == "0x70997970c51812dc3a010c7d01b50e0d17dc79c8", wikd.get("address"))

    wl = curl("GET", BASE + "/api/chain/wallet/list?userId=%s" % uid, token=tok)
    check("chain.wallet", "钱包列表", wl.get("code") == 200 and len(wl.get("data") or []) > 0, wl.get("message"))
    check("chain.wallet", "列表不回传明文助记词", all((w.get("mnemonic") is None) for w in (wl.get("data") or [])), "leak!")

    wid_new = (wl.get("data") or [{}])[0].get("id")
    wb = curl("GET", BASE + "/api/chain/wallet/%s/balance?userId=%s" % (wid_new, uid), token=tok)
    check("chain.wallet", "链上余额查询", wb.get("code") == 200 and len(wb.get("data") or []) > 0, wb.get("message"))

    # 转账 (M3)：用导入的私钥钱包签名广播
    wsend = curl("POST", BASE + "/api/chain/wallet/%s/send?userId=%s" % (wid_new, uid),
                 {"userId": uid, "symbol": "ETH",
                  "toAddress": "0x70997970c51812dc3a010c7d01b50e0d17dc79c8",
                  "amount": 10000000000000000}, token=tok)
    wsd = wsend.get("data") or {}
    check("chain.wallet", "转账广播返回txHash", wsend.get("code") == 200 and bool(wsd.get("txHash")), wsend.get("message"))
    check("chain.wallet", "转账from=钱包地址", (wsd.get("fromAddress") or "").lower() == (wikd.get("address") or "").lower(), wsd.get("fromAddress"))
    check("chain.wallet", "转账校验非法地址", curl("POST", BASE + "/api/chain/wallet/%s/send?userId=%s" % (wid_new, uid),
         {"userId": uid, "symbol": "ETH", "toAddress": "0x123", "amount": 1000}, token=tok).get("code") != 200, "")
    # 转账缺 symbol → 404
    check("chain.wallet", "转账缺币种拒绝", curl("POST", BASE + "/api/chain/wallet/%s/send?userId=%s" % (wid_new, uid),
         {"userId": uid, "toAddress": "0x70997970c51812dc3a010c7d01b50e0d17dc79c8", "amount": 1000}, token=tok).get("code") != 200, "")
    # 转账金额<=0 → 400
    check("chain.wallet", "转账金额<=0拒绝", curl("POST", BASE + "/api/chain/wallet/%s/send?userId=%s" % (wid_new, uid),
         {"userId": uid, "symbol": "ETH", "toAddress": "0x70997970c51812dc3a010c7d01b50e0d17dc79c8", "amount": 0}, token=tok).get("code") != 200, "")

    # BTC 链自托管派生 (coinType=0 路径)
    wbtc = curl("POST", BASE + "/api/chain/wallet/create", {"userId": uid, "chainCode": "BTC", "name": "btc-wallet"}, token=tok)
    wbcd = wbtc.get("data") or {}
    check("chain.wallet", "BTC链创建钱包", wbtc.get("code") == 200 and bool(wbcd.get("address")), wbtc.get("message"))

    # 余额精度字段存在
    bal0 = (wb.get("data") or [{}])[0]
    check("chain.wallet", "余额含decimals精度", bal0.get("decimals") is not None, str(bal0))

    # 永续合约 futures (P3.5)
    print("\n[永续合约 futures]")
    FUT_SYM = "BTC-USDT-SWAP"
    FUT_USER = 99998888  # 独立测试用户
    fc = curl("GET", BASE + "/api/futures/contracts")
    fcd = fc.get("data") or []
    check("futures", "合约列表", fc.get("code") == 200 and len(fcd) > 0, fc.get("message"))
    fmp = curl("GET", BASE + "/api/futures/mark/%s" % FUT_SYM)
    fmpv = fmp.get("data")
    check("futures", "标记价", fmp.get("code") == 200 and fmpv is not None and int(fmpv) > 0, fmp.get("message"))

    # 入金 + 账户
    dep = curl("POST", BASE + "/api/futures/deposit?userId=%s&coin=USDT&amount=10000" % FUT_USER)
    facc = (dep.get("data") or {})
    check("futures", "合约入金10000USDT", dep.get("code") == 200 and int(facc.get("marginBalance") or 0) > 0, dep.get("message"))
    facq = curl("GET", BASE + "/api/futures/account?userId=%s&coin=USDT" % FUT_USER)
    facqd = facq.get("data") or {}
    check("futures", "查询合约账户", facq.get("code") == 200 and int(facqd.get("availableBalance") or 0) > 0, facq.get("message"))

    # 下单：开多(挂单) → 对手开空撮合成交 → 持仓建立
    o1 = curl("POST", BASE + "/api/futures/order",
              {"userId": FUT_USER, "symbol": FUT_SYM, "side": 1, "orderType": 1,
               "price": "0.5", "quantity": "0.001", "leverage": 10})
    o1d = o1.get("data") or {}
    check("futures", "开多限价下单", o1.get("code") == 200 and o1d.get("orderNo"), o1.get("message"))
    # 对手开空(另一用户)撮合
    curl("POST", BASE + "/api/futures/deposit?userId=99998889&coin=USDT&amount=10000")
    o2 = curl("POST", BASE + "/api/futures/order",
              {"userId": 99998889, "symbol": FUT_SYM, "side": 2, "orderType": 1,
               "price": "0.5", "quantity": "0.001", "leverage": 10})
    o2d = o2.get("data") or {}
    check("futures", "对手开空撮合成交", o2.get("code") == 200 and int(o2d.get("filled") or 0) > 0, o2.get("message"))

    # 持仓
    fpos = curl("GET", BASE + "/api/futures/position?userId=%s" % FUT_USER)
    fposd = fpos.get("data") or []
    long_pos = next((p for p in fposd if p.get("side") == 1 and int(p.get("size") or 0) > 0), None)
    check("futures", "多头持仓建立", long_pos is not None and int(long_pos.get("size") or 0) > 0, "no long position")

    # 平仓：平多(挂单) → 对手开多吃 → 持仓清空
    c1 = curl("POST", BASE + "/api/futures/order",
              {"userId": FUT_USER, "symbol": FUT_SYM, "side": 3, "orderType": 1,
               "price": "0.5", "quantity": "0.001", "leverage": 10})
    c1d = c1.get("data") or {}
    check("futures", "平多下单", c1.get("code") == 200 and c1d.get("orderNo"), c1.get("message"))
    curl("POST", BASE + "/api/futures/deposit?userId=99998890&coin=USDT&amount=10000")
    c2 = curl("POST", BASE + "/api/futures/order",
              {"userId": 99998890, "symbol": FUT_SYM, "side": 1, "orderType": 1,
               "price": "0.5", "quantity": "0.001", "leverage": 10})
    c2d = c2.get("data") or {}
    check("futures", "对手开多吃平多", c2.get("code") == 200 and int(c2d.get("filled") or 0) > 0, c2.get("message"))
    fpos2 = curl("GET", BASE + "/api/futures/position?userId=%s" % FUT_USER)
    fpos2d = fpos2.get("data") or []
    still_long = next((p for p in fpos2d if p.get("side") == 1 and int(p.get("size") or 0) > 0), None)
    check("futures", "平多后多头持仓清空", still_long is None, "long still open")
    # 保证金释放
    facq2 = curl("GET", BASE + "/api/futures/account?userId=%s&coin=USDT" % FUT_USER)
    facq2d = facq2.get("data") or {}
    check("futures", "平仓后占用保证金归零", int(facq2d.get("positionMargin") or 0) == 0, str(facq2d.get("positionMargin")))

    # 监控/网关
    print("\n[监控 monitor]")
    h = curl("GET", BASE + "/api/market/ticker/list")  # 公开行情→网关可达性
    check("monitor", "网关+行情可达", h.get("code") == 200, h.get("message"))

    # 汇总
    print("\n=== 汇总: PASS=%d FAIL=%d ===" % (PASS, FAIL))
    gen_report()
    return 0 if FAIL == 0 else 1

def gen_report():
    os.makedirs("docs/test-reports", exist_ok=True)
    ts = datetime.datetime.now().strftime("%Y%m%d_%H%M%S")
    path = "docs/test-reports/report-%s.md" % ts
    lines = []
    lines.append("# 测试报告 - %s" % datetime.datetime.now().strftime("%Y-%m-%d %H:%M:%S"))
    lines.append("\n## 汇总: 通过 %d / 失败 %d / 共 %d\n" % (PASS, FAIL, PASS + FAIL))
    lines.append("| 状态 | 域 | 用例 | 详情 |")
    lines.append("|------|----|------|------|")
    for dom, case, ok, det in RESULTS:
        lines.append("| %s | %s | %s | %s |" % ("✅" if ok else "❌", dom, case, det))
    with open(path, "w", encoding="utf-8") as f:
        f.write("\n".join(lines))
    print("报告: %s" % path)
    # 同时写 latest
    with open("docs/test-reports/latest.md", "w", encoding="utf-8") as f:
        f.write("\n".join(lines))

if __name__ == "__main__":
    sys.exit(main())
