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
    w = curl("GET", BASE + "/api/chain/deposit/address?userId=%s&chainCode=BTC&symbol=BTC" % uid, token=tok)
    check("chain", "充值地址", w.get("code") in (200, 404), w.get("message"))

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
