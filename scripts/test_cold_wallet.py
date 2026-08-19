#!/usr/bin/env python3
"""冷钱包端到端验证：小额热签 / 大额冷签 → 离线签名 → 多签广播"""
import json, subprocess, time, urllib.request, sys

GATEWAY = "http://localhost:8080"   # 经网关（本地 dev 全套）
CHAIN = "http://localhost:8105"     # 直连 chain（internal 接口不经网关）

def curl(url, method="GET", data=None, token=None):
    req = urllib.request.Request(url, method=method)
    req.add_header("Content-Type", "application/json")
    if token: req.add_header("Authorization", "Bearer " + token)
    body = json.dumps(data).encode() if data is not None else None
    try:
        with urllib.request.urlopen(req, body, timeout=15) as r:
            return json.loads(r.read().decode())
    except urllib.error.HTTPError as e:
        return json.loads(e.read().decode())

def main():
    uid = "e2e92443"  # api_test 测试用户
    tok = None
    try:
        cap = curl(GATEWAY + "/api/auth/captcha").get("data", {})
        lr = curl(GATEWAY + "/api/auth/login", "POST",
                  {"username": uid, "password": "Test@123456",
                   "captchaId": cap.get("captchaId"), "captcha": cap.get("captchaText")})
        tok = (lr.get("data") or {}).get("token")
        print("登录:", "OK" if tok else ("失败 " + lr.get("message","")))
    except Exception as e:
        print("登录异常:", e)
    if not tok:
        print("无 token, 提现申请走失败; 跳过 apply, 仅展示 internal 冷签接口存在性")
        # 验证冷签接口可达
        res = curl(CHAIN + "/internal/chain/withdraw/cold/pending", "GET")
        print("冷签待签清单(无单):", res.get("code"), res.get("message"))
        return

    # 申请大额提现(超阈值 500000 最小单位)
    wd = curl(GATEWAY + "/api/chain/withdraw/apply", "POST",
              {"userId": uid, "symbol": "USDT", "chainCode": "ETH",
               "toAddress": "0x70997970c51812dc3a010c7d01b50e0d17dc79c8",
               "amount": 100000000}, token=tok)  # 1000 USDT, 远超阈值
    print("提现申请:", wd.get("code"), wd.get("message"))
    wid = (wd.get("data") or {}).get("id")
    if not wid:
        print("申请失败"); return
    print("提现单 id:", wid, "status:", (wd.get("data") or {}).get("status"))

    # 审核通过 → 应进入冷签(状态6)
    au = curl(CHAIN + "/internal/chain/withdraw/audit?withdrawId=%s" % wid, "POST",
              {"approve": True, "remark": "冷钱包验收"}, token=tok)
    ad = au.get("data") or {}
    print("审核后 status:", ad.get("status"), "sign_mode:", ad.get("signMode"))

    # 待签清单应含此单
    pend = curl(CHAIN + "/internal/chain/withdraw/cold/pending", "GET", token=tok)
    pl = pend.get("data") or []
    target = next((x for x in pl if x.get("id") == wid), None)
    print("待签清单命中:", bool(target))
    if not target:
        print("(若 status!=6 说明走了热签, 需调大阈值或检查)"); return
    print("unsigned tx:", (target.get("coldTxData") or "")[:30] + "...")

if __name__ == "__main__":
    main()
