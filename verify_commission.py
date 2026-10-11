#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
#109 红娘分佣结算 —— 全链路 / 多维度验证
覆盖：成功撮合自动计提、续费计提、比例可配置、结算单生成、提现、幂等不重复计提、
     角色权限（agent/manager/admin 越权 401/403）、团队汇总含「分佣占营收比」。
"""
import json
import urllib.parse
import urllib.request
import urllib.error
import sys

BASE = "http://localhost:8080"
UA = {"User-Agent": "x", "Content-Type": "application/json"}

failures = []


def log(*a):
    print(*a)


def req(method, path, token=None, body=None, query=None):
    url = BASE + path
    if query:
        url += "?" + urllib.parse.urlencode(query)
    data = json.dumps(body).encode() if body is not None else None
    h = dict(UA)
    if token:
        h["Authorization"] = "Bearer " + token
    r = urllib.request.Request(url, data=data, headers=h, method=method)
    try:
        resp = urllib.request.urlopen(r, timeout=15)
        raw = resp.read()
        try:
            body = json.loads(raw.decode("utf-8")) if raw else None
        except Exception:
            body = None
        return resp.status, body
    except urllib.error.HTTPError as e:
        try:
            b = json.loads(e.read() or "null")
        except Exception:
            b = None
        return e.code, b
    except Exception as e:
        return 0, {"error": str(e)}


def login(u):
    st, b = req("POST", "/api/auth/login", body={"username": u, "password": "123456"})
    assert st == 200 and b and b.get("token"), f"login {u} failed: {st} {b}"
    return b["token"]


def check(name, cond, extra=""):
    if cond:
        log(f"  ✅ {name} {extra}")
    else:
        log(f"  ❌ {name} {extra}")
        failures.append(name)


# ---- 登录 ----
log("== 登录 ==")
admin = login("admin")
manager = login("manager")
agent = login("wangjie")

# ---- 取会员与产品 ----
st, mresp = req("GET", "/api/members", manager, query={"size": 50})
assert st == 200, f"members {st}"
members = mresp.get("list", mresp if isinstance(mresp, list) else [])
assert len(members) >= 2, f"需要至少 2 个会员，实际 {len(members)}"
m0, m1 = members[0], members[1]
log(f"  选用会员: {m0['name']}({m0['id']}) / {m1['name']}({m1['id']})")

st, products = req("GET", "/api/products", manager)
assert st == 200 and products, f"products {st}"
prod = products[0]
log(f"  选用产品: {prod['name']} ¥{prod['price']} ({prod['id']})")

AGENT = "王姐"  # 与登录红娘 wangjie 的姓名一致，便于 /mine 核对

# ============================================================
log("\n== 1. 成功撮合自动计提（牵线成功触发）==")
# 1.1 下单（新购，待支付）
st, c = req("POST", "/api/pay/create", manager, {
    "member": m0["name"], "memberId": m0["id"], "productId": prod["id"],
    "orderType": "新购", "channel": "线下", "agent": AGENT})
check("创建新购订单", st == 200 and c.get("order"), f"status={st}")
order1 = c["order"]
oid1 = order1["id"]

# 1.2 先支付（此时无成功牵线，不应产生成功佣金）
st, paid = req("POST", f"/api/pay/simulate/{oid1}", manager)
check("支付完成(新购)", st == 200 and paid.get("status") == "已完成", f"status={getattr(paid,'get',lambda k:None)('status') if isinstance(paid,dict) else paid}")

# 1.3 建立牵线
st, pr = req("POST", "/api/pairs", manager, {"maleId": m0["id"], "femaleId": m1["id"], "agent": AGENT})
check("建立牵线", st == 200 and pr.get("id"), f"status={st}")
pid = pr["id"]

# 1.4 按状态机合法推进至已成功（待确认→牵线中→双方同意→已约见→已成功）
seq = ["牵线中", "双方同意", "已约见", "已成功"]
last = 200
for s in seq:
    st, upd = req("PUT", f"/api/pairs/{pid}", manager, {"status": s})
    last = st
    if st != 200:
        break
check("牵线按状态机推进至已成功", last == 200 and upd.get("status") == "已成功", f"final={last}")

# 1.5 红娘本人佣金应含 1 条成功佣金
st, mine = req("GET", "/api/commission/mine", agent)
check("红娘我的佣金可查", st == 200)
succ_recs = [r for r in (mine or []) if r.get("type") == "成功" and r.get("orderId") == oid1]
check("成功佣金已自动生成", len(succ_recs) == 1, f"count={len(succ_recs)}")
if succ_recs:
    r = succ_recs[0]
    exp = round((order1.get("amount") or 0) * 10 / 100, 2)
    check("成功佣金金额=订单额×10%", abs((r.get("amount") or 0) - exp) < 0.01,
          f"got={r.get('amount')} expect≈{exp}")
    check("佣金归属红娘正确", r.get("agentName") == AGENT, f"agent={r.get('agentName')}")
    check("佣金状态为待结算", r.get("status") == "待结算", f"status={r.get('status')}")

# ============================================================
log("\n== 2. 续费计提（续费支付完成触发）==")
st, c2 = req("POST", "/api/pay/create", manager, {
    "member": m0["name"], "memberId": m0["id"], "productId": prod["id"],
    "orderType": "续费", "channel": "线下", "agent": AGENT})
oid2 = c2["order"]["id"]
o2amt = c2["order"]["amount"]
st, _ = req("POST", f"/api/pay/simulate/{oid2}", manager)
check("支付完成(续费)", st == 200)
st, mine2 = req("GET", "/api/commission/mine", agent)
renew_recs = [r for r in (mine2 or []) if r.get("type") == "续费" and r.get("orderId") == oid2]
check("续费佣金已自动生成", len(renew_recs) == 1, f"count={len(renew_recs)}")
if renew_recs:
    exp2 = round((o2amt or 0) * 15 / 100, 2)
    check("续费佣金金额=订单额×15%", abs((renew_recs[0].get("amount") or 0) - exp2) < 0.01,
          f"got={renew_recs[0].get('amount')} expect≈{exp2}")

# ============================================================
log("\n== 3. 幂等：重复触发不重复计提 ==")
before = len([r for r in (mine2 or [])])
# 3.1 再次置牵线成功
st, _ = req("PUT", f"/api/pairs/{pid}", manager, {"status": "已成功"})
check("重复置已成功不报错", st == 200)
# 3.2 再次支付续费订单（已完成→幂等）
st, _ = req("POST", f"/api/pay/simulate/{oid2}", manager)
check("重复支付(续费)幂等", st == 200)
st, mine3 = req("GET", "/api/commission/mine", agent)
succ_after = len([r for r in (mine3 or []) if r.get("type") == "成功" and r.get("orderId") == oid1])
renew_after = len([r for r in (mine3 or []) if r.get("type") == "续费" and r.get("orderId") == oid2])
check("成功佣金未重复生成", succ_after == 1, f"count={succ_after}")
check("续费佣金未重复生成", renew_after == 1, f"count={renew_after}")

# ============================================================
log("\n== 4. 比例可配置（改 successRate 后新计提按新比例）==")
st, _ = req("PUT", "/api/settings", admin, {"commission.successRate": "20"})
check("修改成功佣金比例=20", st == 200)
# 4.1 为已有成功牵线的会员再下一笔新购订单并支付 → 应按 20% 计提
st, c3 = req("POST", "/api/pay/create", manager, {
    "member": m0["name"], "memberId": m0["id"], "productId": prod["id"],
    "orderType": "新购", "channel": "线下", "agent": AGENT})
oid3 = c3["order"]["id"]
o3amt = c3["order"]["amount"]
st, _ = req("POST", f"/api/pay/simulate/{oid3}", manager)
check("支付完成(第二笔新购)", st == 200)
st, mine4 = req("GET", "/api/commission/mine", agent)
new_succ = [r for r in (mine4 or []) if r.get("type") == "成功" and r.get("orderId") == oid3]
check("新成功佣金按20%生成", len(new_succ) == 1, f"count={len(new_succ)}")
if new_succ:
    exp3 = round((o3amt or 0) * 20 / 100, 2)
    check("新佣金金额=订单额×20%", abs((new_succ[0].get("amount") or 0) - exp3) < 0.01,
          f"got={new_succ[0].get('amount')} expect≈{exp3}")
# 还原比例
req("PUT", "/api/settings", admin, {"commission.successRate": "10"})

# ============================================================
log("\n== 5. 结算单生成（ADMIN）==")
st, settle = req("POST", "/api/commission/settle", admin, {"agentName": AGENT})
check("生成结算单(管理员)", st == 200 and settle.get("batchNo"), f"status={st} res={settle}")
if settle:
    check("结算单覆盖待结算佣金", settle.get("count", 0) >= 2, f"count={settle.get('count')} total={settle.get('total')}")
    st, mine5 = req("GET", "/api/commission/mine", agent)
    settled = [r for r in (mine5 or []) if r.get("status") == "已结算"]
    check("结算后状态=已结算", len(settled) >= 2, f"settled={len(settled)}")
    check("结算单批次号已写入", all(r.get("batchNo") == settle["batchNo"] for r in settled), "")

# ============================================================
log("\n== 6. 提现记录（ADMIN）==")
st, wd = req("POST", "/api/commission/withdraw", admin, {"agentName": AGENT})
check("提现(管理员)", st == 200, f"status={st} res={wd}")
if wd:
    check("提现覆盖已结算佣金", wd.get("count", 0) >= 2, f"count={wd.get('count')}")
    st, mine6 = req("GET", "/api/commission/mine", agent)
    withdrawn = [r for r in (mine6 or []) if r.get("status") == "已提现"]
    check("提现后状态=已提现", len(withdrawn) >= 2, f"withdrawn={len(withdrawn)}")

# ============================================================
log("\n== 7. 团队汇总 / 分佣占营收比（MANAGER）==")
st, summ = req("GET", "/api/commission/summary", manager, query={"scope": "team"})
check("团队汇总可查", st == 200, f"status={st}")
if summ:
    check("含营收总额", summ.get("revenueTotal") is not None and summ.get("revenueTotal") > 0,
          f"revenue={summ.get('revenueTotal')}")
    check("含分佣占营收比", summ.get("commissionRatio") is not None, f"ratio={summ.get('commissionRatio')}%")
    log(f"     营收 ¥{summ.get('revenueTotal')} | 分佣 ¥{summ.get('totalAmount')} | 占比 {summ.get('commissionRatio')}%")

# ============================================================
log("\n== 8. 权限控制 ==")
# 8.1 未登录访问
st, _ = req("GET", "/api/commission/mine")
check("未登录→401", st == 401, f"status={st}")
# 8.2 红娘访问团队佣金 → 403
st, _ = req("GET", "/api/commission/team", agent)
check("红娘访问团队佣金→403", st == 403, f"status={st}")
# 8.3 主管生成结算单 → 403
st, _ = req("POST", "/api/commission/settle", manager, {})
check("主管生成结算单→403", st == 403, f"status={st}")
# 8.4 红娘生成结算单 → 403
st, _ = req("POST", "/api/commission/settle", agent, {})
check("红娘生成结算单→403", st == 403, f"status={st}")
# 8.5 主管可看团队佣金 / 汇总
st, _ = req("GET", "/api/commission/team", manager)
check("主管查看团队佣金→200", st == 200, f"status={st}")
st, _ = req("GET", "/api/commission/summary", manager, query={"scope": "team"})
check("主管查看汇总→200", st == 200, f"status={st}")
# 8.6 红娘查看本人佣金 → 200
st, _ = req("GET", "/api/commission/mine", agent)
check("红娘查看本人佣金→200", st == 200, f"status={st}")

# ============================================================
log("\n" + "=" * 50)
if failures:
    log(f"❌ 验证未通过，失败项 {len(failures)}：{failures}")
    sys.exit(1)
else:
    log("✅ 全链路 / 多维度验证全部通过（#109 红娘分佣结算）")
    sys.exit(0)
