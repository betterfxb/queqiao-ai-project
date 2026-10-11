#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
#111 增长与差异化 —— 全链路 / 多维度验证
覆盖：
 5.3.1 转介绍激励（登记/幂等/转化/发奖/封顶/作废/支付事件驱动闭环）
 5.3.2 成功案例营销（脱敏草稿/幂等/发布/公开免登录/下架/PII 零泄漏）
 5.3.3 同城运营模板（合伙人 CRUD / 社群档位 CRUD / 单城总览 / 多城列表）
 5.3.4 产业链导流（商户 CRUD / 导流单 状态机+幂等 / 计佣 / 推荐）
 5.3.5 渠道归因 ROI（渠道 CRUD / 线索打标 / ROI 口径 / ops 别名 / 时间窗）
 横切：权限矩阵（401/403）、幂等、脱敏、接线（支付→发奖、牵线成功→草稿、线索→转介绍）
"""
import json
import re as _re
import time
import urllib.parse
import urllib.request
import urllib.error

BASE = "http://localhost:8080"
UA = {"User-Agent": "x", "Content-Type": "application/json"}
failures = []
passes = []


def req(method, path, token=None, body=None, query=None):
    url = BASE + path
    if query:
        url += "?" + urllib.parse.urlencode(query)
    data = json.dumps(body, ensure_ascii=False).encode("utf-8") if body is not None else None
    h = dict(UA)
    if token:
        h["Authorization"] = "Bearer " + token
    r = urllib.request.Request(url, data=data, headers=h, method=method)
    try:
        resp = urllib.request.urlopen(r, timeout=20)
        raw = resp.read()
        try:
            b = json.loads(raw.decode("utf-8")) if raw else None
        except Exception:
            b = raw.decode("utf-8", "ignore")
        return resp.status, b
    except urllib.error.HTTPError as e:
        try:
            b = json.loads(e.read() or b"null")
        except Exception:
            b = None
        return e.code, b
    except Exception as e:
        return 0, {"error": str(e)}


def login(u):
    st, b = req("POST", "/api/auth/login", body={"username": u, "password": "123456"})
    assert st == 200 and b and b.get("token"), "login %s failed: %s %s" % (u, st, b)
    return b["token"]


def check(name, cond, extra=""):
    if cond:
        passes.append(name)
        print("  [PASS] %s" % name)
    else:
        failures.append("%s %s" % (name, extra))
        print("  [FAIL] %s  %s" % (name, extra))


def section(t):
    print("\n" + "=" * 78)
    print(t)
    print("=" * 78)


def rows_of(b):
    """统一分页解析：后端 PageResult 使用 list 键"""
    if isinstance(b, list):
        return b
    if isinstance(b, dict):
        for k in ("list", "rows", "records", "content", "data"):
            if isinstance(b.get(k), list):
                return b[k]
    return []


# ============================ 0. 登录与权限基线 ============================
section("0. 登录 / 权限基线")
T_ADMIN = login("admin")
T_MGR = login("manager")
T_AGENT = login("wangjie")
print("  tokens ok: admin/manager/agent")

st, _ = req("GET", "/api/referral")
check("未登录访问 /api/referral 返回 401", st == 401, "got %s" % st)

st, _ = req("GET", "/api/referral", token=T_AGENT)
check("红娘访问转介绍全量列表 403", st == 403, "got %s" % st)

st, b = req("GET", "/api/referral/mine", token=T_AGENT)
check("红娘可访问 /api/referral/mine", st == 200 and isinstance(b, list), "got %s" % st)

st, _ = req("GET", "/api/stories/public")
check("未登录可访问 /api/stories/public（白名单）", st == 200, "got %s" % st)

st, _ = req("POST", "/api/partners/merchants", token=T_AGENT, body={"name": "x"})
check("红娘新增合作商户 403", st == 403, "got %s" % st)

st, _ = req("POST", "/api/channels", token=T_MGR, body={"name": "x"})
check("主管新增渠道 403（仅管理员）", st == 403, "got %s" % st)

st, _ = req("GET", "/api/city/tiers", token=T_AGENT)
check("红娘可读社群档位", st == 200, "got %s" % st)

st, _ = req("POST", "/api/city/tiers", token=T_MGR, body={"level": "x"})
check("主管新增社群档位 403（仅管理员）", st == 403, "got %s" % st)


# ============================ 1. 5.3.1 转介绍激励 ============================
section("1. 5.3.1 转介绍激励")
ts = str(int(time.time()))
st, mlist = req("GET", "/api/members", token=T_ADMIN, query={"size": 5, "page": 1})
rows = rows_of(mlist)
referrer_id = rows[0]["id"] if rows else None
check("取到推荐人会员", bool(referrer_id), str(mlist)[:120])

phone_a = "1590000" + ts[-4:]
st, r1 = req("POST", "/api/referral", token=T_AGENT, body={
    "referrerId": referrer_id, "referrerType": "会员",
    "refereeName": "测试被推荐甲", "refereePhone": phone_a, "remark": "自动化验证"})
check("登记推荐关系成功", st == 200 and r1 and r1.get("status") == "已登记", "%s %s" % (st, str(r1)[:160]))
rid = r1.get("id") if isinstance(r1, dict) else None
check("推荐人姓名已回填", bool(r1 and r1.get("referrerName")), str(r1)[:120])

st, r2 = req("POST", "/api/referral", token=T_AGENT, body={
    "referrerId": referrer_id, "referrerType": "会员",
    "refereeName": "测试被推荐甲", "refereePhone": phone_a})
check("重复登记幂等（返回同一条）", st == 200 and r2 and r2.get("id") == rid, "%s vs %s" % (r2.get("id") if r2 else None, rid))

st, _ = req("POST", "/api/referral", token=T_AGENT, body={"referrerId": referrer_id})
check("缺少被推荐人手机号 → 400", st == 400, "got %s" % st)

st, b = req("POST", "/api/referral/%s/reward" % rid, token=T_ADMIN, body={})
check("未转化直接发奖被拒（400）", st == 400, "got %s %s" % (st, str(b)[:120]))

st, b = req("POST", "/api/referral/%s/convert" % rid, token=T_AGENT, body={})
check("红娘标记转化 403", st == 403, "got %s" % st)

st, r3 = req("POST", "/api/referral/%s/convert" % rid, token=T_MGR, body={"orderAmount": 3000})
check("主管标记转化成功", st == 200 and r3 and r3.get("status") == "已转化", "%s %s" % (st, str(r3)[:160]))

st, b = req("POST", "/api/referral/%s/reward" % rid, token=T_MGR, body={})
check("主管发奖 403（仅管理员）", st == 403, "got %s" % st)

st, r4 = req("POST", "/api/referral/%s/reward" % rid, token=T_ADMIN, body={})
amt = (r4 or {}).get("rewardAmount")
check("管理员发奖成功", st == 200 and r4 and r4.get("status") == "已发奖", "%s %s" % (st, str(r4)[:160]))
check("奖励金额=订单额×10% 且不超封顶500（3000→300）", amt == 300.0, "got %s" % amt)

st, r5 = req("POST", "/api/referral/%s/reward" % rid, token=T_ADMIN, body={})
check("重复发奖幂等（金额不变）", st == 200 and (r5 or {}).get("rewardAmount") == amt, "got %s" % (r5 or {}).get("rewardAmount"))

st, b = req("POST", "/api/referral/%s/invalid" % rid, token=T_MGR)
check("已发奖记录不可作废（400）", st == 400, "got %s %s" % (st, str(b)[:120]))

# 封顶验证：10000 * 10% = 1000 > 500 → 应封顶 500
phone_cap = "1591111" + ts[-4:]
st, rc = req("POST", "/api/referral", token=T_MGR, body={
    "referrerId": referrer_id, "refereeName": "测试封顶", "refereePhone": phone_cap})
rcid = rc.get("id")
req("POST", "/api/referral/%s/convert" % rcid, token=T_MGR, body={"orderAmount": 10000})
st, rc2 = req("POST", "/api/referral/%s/reward" % rcid, token=T_ADMIN, body={})
check("奖励封顶生效（10000→500）", (rc2 or {}).get("rewardAmount") == 500.0, "got %s" % (rc2 or {}).get("rewardAmount"))

st, summ = req("GET", "/api/referral/summary", token=T_MGR)
check("转介绍汇总可读且含计数", st == 200 and isinstance(summ, dict) and "total" in summ, str(summ)[:160])


# ================= 2. 5.3.1 事件驱动闭环：支付 → 自动转化+发奖 =================
section("2. 5.3.1 支付事件驱动闭环（FinanceService.pay → onOrderPaid）")
phone_pay = "1592222" + ts[-4:]
st, newm = req("POST", "/api/members", token=T_ADMIN, body={
    "name": "验证会员" + ts[-4:], "gender": "男", "age": 30, "city": "杭州市",
    "phone": phone_pay, "status": "活跃", "level": "钻石会员"})
check("创建被推荐会员成功", st == 200 and newm and newm.get("id"), "%s %s" % (st, str(newm)[:160]))
mid = (newm or {}).get("id")

st, rp = req("POST", "/api/referral", token=T_MGR, body={
    "referrerId": referrer_id, "refereeName": "验证会员", "refereePhone": phone_pay})
rpid = (rp or {}).get("id")
check("按手机号登记推荐关系", st == 200 and rpid, "%s" % st)

st, order = req("POST", "/api/finance/orders", token=T_ADMIN, body={
    "memberId": mid, "member": (newm or {}).get("name"), "type": "钻石会员年卡",
    "orderType": "新购", "amount": 2000, "status": "处理中", "channel": "微信支付"})
oid = (order or {}).get("id")
check("创建订单成功", st == 200 and oid, "%s %s" % (st, str(order)[:160]))

st, paid = req("POST", "/api/finance/orders/%s/confirm" % oid, token=T_ADMIN)
check("订单支付成功", st == 200 and (paid or {}).get("status") == "已完成", "%s %s" % (st, str(paid)[:120]))

st, rpa = req("GET", "/api/referral/%s" % rpid, token=T_MGR)
check("支付后转介绍自动置为已发奖", (rpa or {}).get("status") == "已发奖", "got %s" % (rpa or {}).get("status"))
check("自动奖励=2000×10%=200", (rpa or {}).get("rewardAmount") == 200.0, "got %s" % (rpa or {}).get("rewardAmount"))
check("自动回填被推荐会员ID", (rpa or {}).get("refereeMemberId") == mid, "got %s" % (rpa or {}).get("refereeMemberId"))

# 幂等：再次 confirm（已完成）不应重复发奖
req("POST", "/api/finance/orders/%s/confirm" % oid, token=T_ADMIN)
st, rpb = req("GET", "/api/referral/%s" % rpid, token=T_MGR)
check("重复支付不重复发奖（幂等）", (rpb or {}).get("rewardAmount") == 200.0, "got %s" % (rpb or {}).get("rewardAmount"))


# ============================ 3. 5.3.2 成功案例营销 ============================
section("3. 5.3.2 成功案例营销")
st, pl = req("GET", "/api/pairs", token=T_ADMIN, query={"size": 200, "page": 1})
prows = rows_of(pl)
succ = [p for p in prows if p.get("status") == "已成功"]
# 「已约见」是唯一可直接流转到「已成功」的前置态（状态机：已约见 → 已成功）
ready = [p for p in prows if p.get("status") == "已约见"]
other = [p for p in prows if p.get("status") not in ("已成功", "已终止")]
check("存在已成功牵线用于生成案例", len(succ) > 0, "已成功=%d" % len(succ))

story = None
if succ:
    pair = succ[0]
    st, story = req("POST", "/api/stories/draft", token=T_MGR, body={"pairId": pair["id"]})
    check("由已成功牵线生成脱敏草稿", st == 200 and story and story.get("status") == "草稿",
          "%s %s" % (st, str(story)[:200]))
    st, story2 = req("POST", "/api/stories/draft", token=T_MGR, body={"pairId": pair["id"]})
    check("重复生成草稿幂等", st == 200 and (story2 or {}).get("id") == (story or {}).get("id"),
          "%s vs %s" % ((story2 or {}).get("id"), (story or {}).get("id")))

    blob = json.dumps(story or {}, ensure_ascii=False)
    male_real = pair.get("maleName") or ""
    female_real = pair.get("femaleName") or ""
    leak = []
    if len(male_real) > 1 and male_real in blob:
        leak.append("男方真名")
    if len(female_real) > 1 and female_real in blob:
        leak.append("女方真名")
    if _re.search(r"1[3-9]\d{9}", blob):
        leak.append("手机号")
    check("案例内容零 PII 泄漏（真名/手机号）", not leak, "泄漏=%s content=%s" % (leak, blob[:200]))
    check("使用别名（王先生/李女士式）", bool((story or {}).get("maleAlias")) and "先生" in str((story or {}).get("maleAlias")),
          "maleAlias=%s" % (story or {}).get("maleAlias"))

sid = (story or {}).get("id")
if sid:
    st, _ = req("POST", "/api/stories/%s/publish" % sid, token=T_MGR)
    check("主管发布案例 403（仅管理员）", st == 403, "got %s" % st)

    st, pubs = req("POST", "/api/stories/%s/publish" % sid, token=T_ADMIN, body={"publicVisible": True})
    check("管理员发布案例成功", st == 200 and (pubs or {}).get("status") == "已发布", "%s %s" % (st, str(pubs)[:120]))

    st, plist = req("GET", "/api/stories/public")
    ids = [x.get("id") for x in (plist or [])]
    check("匿名公开列表可见已发布案例", st == 200 and sid in ids, "ids=%s" % str(ids)[:160])
    pblob = json.dumps(plist or [], ensure_ascii=False)
    check("公开列表零手机号泄漏", not _re.search(r"1[3-9]\d{9}", pblob), pblob[:160])

    st, _ = req("POST", "/api/stories/%s/offline" % sid, token=T_ADMIN)
    st, plist2 = req("GET", "/api/stories/public")
    check("下架后匿名列表不再可见", sid not in [x.get("id") for x in (plist2 or [])], "still visible")

    st, _ = req("POST", "/api/stories/%s/publish" % sid, token=T_ADMIN, body={"publicVisible": False})
    st, plist3 = req("GET", "/api/stories/public")
    check("已发布但非公开不出现在对外列表", sid not in [x.get("id") for x in (plist3 or [])], "leaked")

if other:
    st, b = req("POST", "/api/stories/draft", token=T_MGR, body={"pairId": other[0]["id"]})
    check("非已成功牵线不可生成案例（400）", st == 400, "got %s %s" % (st, str(b)[:120]))

st, ssum = req("GET", "/api/stories/summary", token=T_MGR)
check("案例汇总可读", st == 200 and isinstance(ssum, dict) and "total" in ssum, str(ssum)[:160])

# 接线验证：牵线「已约见」→「已成功」应自动沉淀草稿
if ready:
    cand = ready[0]
    st, up = req("PUT", "/api/pairs/%s" % cand["id"], token=T_ADMIN, body={"status": "已成功"})
    check("牵线 已约见 → 已成功 流转成功", st == 200 and (up or {}).get("status") == "已成功",
          "%s %s" % (st, str(up)[:120]))
    if st == 200:
        st, dl = req("GET", "/api/stories", token=T_MGR, query={"status": "草稿"})
        hit = [x for x in (dl or []) if x.get("pairId") == cand["id"]]
        check("牵线置为已成功 → 自动沉淀案例草稿（接线）", len(hit) == 1, "hit=%d" % len(hit))
        if hit:
            hb = json.dumps(hit[0], ensure_ascii=False)
            check("自动草稿同样零手机号泄漏", not _re.search(r"1[3-9]\d{9}", hb), hb[:160])
else:
    print("  [SKIP] 无「已约见」牵线可用于接线验证")

# 状态机非法流转防护
if succ:
    st, b = req("PUT", "/api/pairs/%s" % succ[0]["id"], token=T_ADMIN, body={"status": "牵线中"})
    check("已成功牵线不可回退（400）", st == 400, "got %s %s" % (st, str(b)[:120]))


# ============================ 4. 5.3.3 同城运营模板 ============================
section("4. 5.3.3 同城运营模板")
st, parts = req("GET", "/api/city/partners", token=T_MGR)
check("城市合伙人种子已载入(>=3)", st == 200 and len(parts or []) >= 3, "%s n=%s" % (st, len(parts or [])))

st, tiers = req("GET", "/api/city/tiers", token=T_MGR)
check("社群档位种子已载入(>=5)", st == 200 and len(tiers or []) >= 5, "%s n=%s" % (st, len(tiers or [])))

st, newt = req("POST", "/api/city/tiers", token=T_ADMIN, body={
    "city": "苏州市", "level": "轻会员", "price": 199, "capacity": 100,
    "memberCount": 10, "benefits": "每月2次推荐"})
tid = (newt or {}).get("id")
check("管理员新增社群档位", st == 200 and tid, "%s %s" % (st, str(newt)[:120]))

st, upt = req("PUT", "/api/city/tiers/%s" % tid, token=T_ADMIN, body={"price": 259})
check("编辑社群档位价格生效", st == 200 and (upt or {}).get("price") == 259.0, "got %s" % (upt or {}).get("price"))

st, ov = req("GET", "/api/city/overview", token=T_MGR, query={"city": "杭州市"})
check("单城总览可读", st == 200 and isinstance(ov, dict) and ov.get("city") == "杭州市", str(ov)[:160])
if isinstance(ov, dict):
    exp = 0.0
    for t in ov.get("tiers") or []:
        exp += (t.get("price") or 0) * (t.get("memberCount") or 0)
    check("社群营收=Σ(价格×人数) 口径正确",
          abs((ov.get("communityRevenue") or 0) - round(exp, 2)) < 0.01,
          "got %s expect %s" % (ov.get("communityRevenue"), round(exp, 2)))
    check("单城总览含合伙人明细", isinstance(ov.get("partners"), list), str(ov.get("partners"))[:80])

st, cl = req("GET", "/api/city/list", token=T_MGR)
cities = [c.get("city") for c in (cl or [])]
check("多城列表含杭州市", st == 200 and "杭州市" in cities, "cities=%s" % str(cities)[:160])
check("多城列表含苏州市（新档位城市并入）", "苏州市" in cities, "cities=%s" % str(cities)[:160])

st, ov2 = req("GET", "/api/city/overview", token=T_MGR)
check("缺少 city 参数 → 400", st == 400, "got %s" % st)

st, _ = req("DELETE", "/api/city/tiers/%s" % tid, token=T_ADMIN)
check("管理员删除社群档位", st == 200, "got %s" % st)

st, np = req("POST", "/api/city/partners", token=T_ADMIN, body={
    "city": "苏州市", "ownerAgentName": "验证合伙人", "tier": "城市代理",
    "status": "筹备中", "shareRate": 22, "startDate": "2026-08-01"})
pid = (np or {}).get("id")
check("管理员新增城市合伙人", st == 200 and pid, "%s %s" % (st, str(np)[:120]))
st, _ = req("DELETE", "/api/city/partners/%s" % pid, token=T_ADMIN)
check("管理员删除城市合伙人", st == 200, "got %s" % st)


# ============================ 5. 5.3.4 产业链导流 ============================
section("5. 5.3.4 产业链延伸导流")
st, ms = req("GET", "/api/partners/merchants", token=T_MGR)
check("合作商户种子已载入(>=6)", st == 200 and len(ms or []) >= 6, "%s n=%s" % (st, len(ms or [])))
coop = [m for m in (ms or []) if m.get("status") == "合作中"]
merchant = coop[0] if coop else None
check("存在合作中商户", bool(merchant), "n=%d" % len(coop))

st, mo = req("POST", "/api/partners/orders", token=T_AGENT, body={
    "merchantId": merchant["id"], "memberId": mid, "amount": 0, "note": "验证导流"})
moid = (mo or {}).get("id")
check("红娘创建导流单成功", st == 200 and (mo or {}).get("status") == "已导流", "%s %s" % (st, str(mo)[:160]))
check("导流单快照商户佣金率", (mo or {}).get("commissionRate") == merchant.get("commissionRate"),
      "got %s expect %s" % ((mo or {}).get("commissionRate"), merchant.get("commissionRate")))

st, b = req("POST", "/api/partners/orders/%s/settle" % moid, token=T_ADMIN)
check("未成交直接结算被拒（400）", st == 400, "got %s %s" % (st, str(b)[:120]))

st, b = req("POST", "/api/partners/orders/%s/deal" % moid, token=T_AGENT, body={"amount": 8000})
check("红娘标记成交 403", st == 403, "got %s" % st)

st, d1 = req("POST", "/api/partners/orders/%s/deal" % moid, token=T_MGR, body={"amount": 8000})
rate = merchant.get("commissionRate") or 0
expect_c = round(8000 * rate / 100.0, 2)
check("主管标记成交成功", st == 200 and (d1 or {}).get("status") == "已成交", "%s %s" % (st, str(d1)[:160]))
check("佣金=金额×费率（%s%%→%s）" % (rate, expect_c),
      abs(((d1 or {}).get("commissionAmount") or 0) - expect_c) < 0.01,
      "got %s" % (d1 or {}).get("commissionAmount"))

st, d2 = req("POST", "/api/partners/orders/%s/deal" % moid, token=T_MGR, body={"amount": 99999})
check("重复成交幂等（金额/佣金不变）",
      (d2 or {}).get("amount") == 8000 and abs(((d2 or {}).get("commissionAmount") or 0) - expect_c) < 0.01,
      "got amount=%s commission=%s" % ((d2 or {}).get("amount"), (d2 or {}).get("commissionAmount")))

st, s1 = req("POST", "/api/partners/orders/%s/settle" % moid, token=T_MGR)
check("主管结算 403（仅管理员）", st == 403, "got %s" % st)

st, s2 = req("POST", "/api/partners/orders/%s/settle" % moid, token=T_ADMIN)
check("管理员结算成功", st == 200 and (s2 or {}).get("status") == "已结算", "%s %s" % (st, str(s2)[:120]))

st, s3 = req("POST", "/api/partners/orders/%s/settle" % moid, token=T_ADMIN)
check("重复结算幂等", st == 200 and (s3 or {}).get("status") == "已结算", "got %s" % st)

st, b = req("POST", "/api/partners/orders/%s/cancel" % moid, token=T_MGR, body={"reason": "x"})
check("已结算导流单不可取消（400）", st == 400, "got %s %s" % (st, str(b)[:120]))

if succ:
    st, rec = req("GET", "/api/partners/recommend", token=T_AGENT, query={"pairId": succ[0]["id"]})
    recs = (rec or {}).get("merchants") or []
    cats = [m.get("category") for m in recs]
    check("成功牵线可获商户推荐", st == 200 and len(recs) > 0, "%s n=%d" % (st, len(recs)))
    check("推荐每类目最多 1 家（不刷屏）", len(cats) == len(set(cats)), "cats=%s" % cats)
    check("推荐仅含合作中商户", all(m.get("status") == "合作中" for m in recs), "status=%s" % [m.get("status") for m in recs])

st, b = req("GET", "/api/partners/recommend", token=T_AGENT, query={})
check("推荐缺少 pairId → 400（非 500）", st == 400, "got %s %s" % (st, str(b)[:120]))

st, psum = req("GET", "/api/partners/summary", token=T_MGR)
check("导流汇总可读", st == 200 and isinstance(psum, dict), str(psum)[:200])


# ============================ 6. 5.3.5 渠道归因 ROI ============================
section("6. 5.3.5 渠道归因 ROI")
st, chs = req("GET", "/api/channels", token=T_MGR)
check("渠道种子已载入(>=6)", st == 200 and len(chs or []) >= 6, "%s n=%s" % (st, len(chs or [])))

st, roi0 = req("GET", "/api/channels/roi", token=T_MGR)
check("ROI 聚合可读", st == 200 and isinstance(roi0, dict) and "rows" in roi0, str(roi0)[:160])
base_lead = (roi0 or {}).get("leadTotal") or 0


def row_of(roi, name):
    for r in (roi or {}).get("rows") or []:
        if r.get("name") == name:
            return r
    return None


dy0 = row_of(roi0, "抖音本地推") or {}
dy0_lead = dy0.get("leadCount") or 0
dy0_cost = dy0.get("cost") or 0

# 造 3 条抖音渠道线索，其中 1 条推进到已成交
lead_ids = []
for i in range(3):
    st, lead = req("POST", "/api/crm/leads", token=T_ADMIN, body={
        "name": "渠道验证%s%d" % (ts[-4:], i), "age": 28, "gender": "女",
        "phone": "1593%03d%s" % (i, ts[-4:]), "level": "普通",
        "channel": "抖音本地推", "campaignId": "CP-" + ts[-4:], "cost": 100})
    check("创建带渠道打标线索 #%d" % i, st == 200 and lead and lead.get("id"), "%s %s" % (st, str(lead)[:120]))
    if lead and lead.get("id"):
        lead_ids.append(lead["id"])

if lead_ids:
    st, mv = req("POST", "/api/crm/leads/%s/stage" % lead_ids[0], token=T_ADMIN, body={"stage": "deal"})
    check("线索推进到已成交", st == 200, "%s %s" % (st, str(mv)[:120]))

st, roi1 = req("GET", "/api/channels/roi", token=T_MGR)
dy1 = row_of(roi1, "抖音本地推") or {}
check("渠道线索数 +3", (dy1.get("leadCount") or 0) == dy0_lead + 3,
      "got %s expect %s" % (dy1.get("leadCount"), dy0_lead + 3))
check("渠道成本累加线索成本 +300", abs((dy1.get("cost") or 0) - (dy0_cost + 300)) < 0.01,
      "got %s expect %s" % (dy1.get("cost"), dy0_cost + 300))
check("渠道成交数 >=1", (dy1.get("dealCount") or 0) >= 1, "got %s" % dy1.get("dealCount"))
check("ROI 行含 cac/roi/convRate 指标",
      all(k in dy1 for k in ("cac", "roi", "convRate")), "keys=%s" % list(dy1.keys()))
check("总线索数增长", ((roi1 or {}).get("leadTotal") or 0) >= base_lead + 3,
      "got %s base %s" % ((roi1 or {}).get("leadTotal"), base_lead))

st, roi_ops = req("GET", "/api/ops/channel-roi", token=T_MGR)
check("/api/ops/channel-roi 别名可用且口径一致",
      st == 200 and (roi_ops or {}).get("leadTotal") == (roi1 or {}).get("leadTotal"),
      "%s %s vs %s" % (st, (roi_ops or {}).get("leadTotal"), (roi1 or {}).get("leadTotal")))

st, roi_win = req("GET", "/api/channels/roi", token=T_MGR, query={"from": "1990-01-01", "to": "1990-12-31"})
check("时间窗过滤生效（历史空窗 leadTotal=0）", st == 200 and (roi_win or {}).get("leadTotal") == 0,
      "got %s" % (roi_win or {}).get("leadTotal"))

st, nc = req("POST", "/api/channels", token=T_ADMIN, body={
    "name": "验证渠道" + ts[-4:], "category": "线上投放", "code": "vfy" + ts[-4:],
    "cost": 500, "owner": "验证", "status": "启用"})
cid = (nc or {}).get("id")
check("管理员新增渠道", st == 200 and cid, "%s %s" % (st, str(nc)[:120]))
st, uc = req("PUT", "/api/channels/%s" % cid, token=T_ADMIN, body={"cost": 800})
check("编辑渠道成本生效", st == 200 and (uc or {}).get("cost") == 800.0, "got %s" % (uc or {}).get("cost"))
st, _ = req("DELETE", "/api/channels/%s" % cid, token=T_ADMIN)
check("管理员删除渠道", st == 200, "got %s" % st)


# ================= 7. 线索 → 转介绍自动登记（CrmController 接线） =================
section("7. 线索携带推荐人 → 自动登记转介绍（接线）")
phone_l = "1594444" + ts[-4:]
st, lead = req("POST", "/api/crm/leads", token=T_ADMIN, body={
    "name": "转介绍线索" + ts[-4:], "age": 27, "gender": "男", "phone": phone_l,
    "channel": "老会员转介绍", "cost": 0,
    "referrerMemberId": referrer_id, "referrerName": "老会员"})
check("创建带推荐人的线索", st == 200 and lead and lead.get("id"), "%s %s" % (st, str(lead)[:120]))

st, rl = req("GET", "/api/referral", token=T_MGR, query={"referrerId": referrer_id})
hit = [x for x in (rl or []) if x.get("refereePhone") == phone_l]
check("线索自动登记为转介绍记录", len(hit) == 1, "n=%d" % len(hit))
if hit:
    check("自动登记关联线索ID", hit[0].get("refereeLeadId") == lead.get("id"),
          "got %s expect %s" % (hit[0].get("refereeLeadId"), lead.get("id")))


# ============================ 8. 运营总览合入 ============================
section("8. 运营总览合入增长指标")
st, met = req("GET", "/api/ops/metrics", token=T_MGR)
check("运营总览可读", st == 200 and isinstance(met, dict), str(met)[:120])
check("总览含 referralProgram 汇总", isinstance((met or {}).get("referralProgram"), dict),
      str((met or {}).get("referralProgram"))[:120])
check("总览含 partnerProgram 汇总", isinstance((met or {}).get("partnerProgram"), dict),
      str((met or {}).get("partnerProgram"))[:120])
st, _ = req("GET", "/api/ops/metrics", token=T_AGENT)
check("红娘访问运营总览 403", st == 403, "got %s" % st)


# ============================ 汇总 ============================
section("验证汇总")
print("PASS: %d" % len(passes))
print("FAIL: %d" % len(failures))
if failures:
    print("\n失败明细：")
    for f in failures:
        print("  - %s" % f)
print("\nRESULT: " + ("ALL PASS" if not failures else "HAS FAILURES"))
