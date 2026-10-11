#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""
#110 信任合规 —— 全链路 / 多维度验证
覆盖：
 5.2.1 认证标签产品化（审核/核验回写 + 匹配加权）
 5.2.2 第三方核验（发起/结果/回写/可追溯/幂等）
 5.2.3 风控（举报处理 / 黑名单拉黑限牵线 / 风险扫描与处置）
 5.2.4 合规三件套（四类模板 + 升版 + 强制同意留痕）
 权限（401/403）、PII 脱敏、幂等
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

# ---- 取会员 ----
st, mresp = req("GET", "/api/members", manager, query={"size": 100})
assert st == 200, f"members {st}"
mlist = mresp.get("list", [])
males = [m for m in mlist if m.get("gender") == "男"]
females = [m for m in mlist if m.get("gender") == "女"]
assert males and females, "需要男女会员各至少 1"
m_a = males[0]   # 用于举报/黑名单/风险
m_b = females[0]  # 用于认证加权
log(f"  选用会员: {m_a['name']}({m_a['id']},男) / {m_b['name']}({m_b['id']},女)")

# ============================================================
log("\n== 5.2.1 认证标签产品化 + 5.2.2 第三方核验 ==")

# 匹配加权基线
st, s1r = req("GET", "/api/match/score", manager, query={"maleId": m_a["id"], "femaleId": m_b["id"]})
check("匹配评分接口可用", st == 200 and isinstance(s1r, dict) and "score" in s1r, f"status={st}")
s1 = s1r.get("score", 0)

# 对 m_b 发起三类核验并录入通过
for vtype in ["实名", "学历", "资产"]:
    st, ct = req("POST", "/api/verify", manager,
                 {"memberId": m_b["id"], "type": vtype, "channel": "学信网", "rawProof": "proof-" + vtype})
    check(f"发起{vtype}核验任务", st == 200 and ct.get("id"), f"status={st}")
    if st == 200:
        st, rt = req("POST", f"/api/verify/{ct['id']}/result", manager,
                     {"status": "已通过", "result": vtype + "核验通过"})
        check(f"录入{vtype}核验结果(通过)", st == 200 and rt.get("status") == "已通过", f"status={st}")

# 核验任务可追溯
st, vlist = req("GET", "/api/verify", manager, query={"memberId": m_b["id"], "type": "学历"})
check("核验任务可按会员/类型查询", st == 200 and any(t.get("memberId") == m_b["id"] for t in (vlist or [])), f"count={len(vlist or [])}")

# 回写认证标签
st, vd = req("GET", f"/api/members/{m_b['id']}/verify", manager)
check("认证标签回写-实名", st == 200 and vd.get("realName") is True, f"realName={vd.get('realName') if st==200 else '?'}")
check("认证标签回写-学历", vd.get("education") is True, f"education={vd.get('education')}")
check("认证标签回写-资产", vd.get("asset") is True, f"asset={vd.get('asset')}")

# 匹配加权生效（认证可信维度 > 0 即证明加权）
st, s2r = req("GET", "/api/match/score", manager, query={"maleId": m_a["id"], "femaleId": m_b["id"]})
s2 = s2r.get("score", 0)
dims = s2r.get("dimensions", {}) if isinstance(s2r, dict) else {}
check("高认证会员匹配加权生效", isinstance(dims, dict) and dims.get("认证可信", 0) > 0,
      f"认证可信={dims.get('认证可信')} s1={s1} s2={s2}")

# 核验幂等：重复录入同一任务通过，不产生异常，标签保持
st, ct2 = req("POST", "/api/verify", manager,
              {"memberId": m_b["id"], "type": "学历", "channel": "学信网", "rawProof": "dup"})
if st == 200:
    tid = ct2["id"]
    req("POST", f"/api/verify/{tid}/result", manager, {"status": "已通过", "result": "x"})
    st, rt2 = req("POST", f"/api/verify/{tid}/result", manager, {"status": "已通过", "result": "y"})
    check("核验结果录入幂等(重复通过不报错)", st == 200, f"status={st}")
    st, vd2 = req("GET", f"/api/members/{m_b['id']}/verify", manager)
    check("幂等后学历标签仍为true", vd2.get("education") is True, "")

# 未通过核验不写标签：m_a 实名核验未通过
st, ct3 = req("POST", "/api/verify", manager,
              {"memberId": m_a["id"], "type": "实名", "channel": "人工", "rawProof": "x"})
if st == 200:
    req("POST", f"/api/verify/{ct3['id']}/result", manager, {"status": "未通过", "result": "材料不符"})
    st, vd3 = req("GET", f"/api/members/{m_a['id']}/verify", manager)
    check("未通过核验不写认证标签", vd3.get("realName") is False, f"realName={vd3.get('realName')}")

# ============================================================
log("\n== 5.2.3 风控：举报 / 黑名单 / 预警 ==")

# 提交两条举报（agent 可提交）
st, c1 = req("POST", "/api/complaints", agent, {"targetMemberId": m_a["id"], "type": "欺诈", "content": "疑似婚托"})
check("提交举报(agent)", st == 200 and c1.get("id"), f"status={st}")
st, c2 = req("POST", "/api/complaints", agent, {"targetMemberId": m_a["id"], "type": "婚托", "content": "多次失联"})
check("提交举报2(agent)", st == 200 and c2.get("id"), f"status={st}")

# 主管查看举报列表
st, clist = req("GET", "/api/complaints", manager)
check("主管可查举报列表", st == 200 and any(x.get("targetMemberId") == m_a["id"] for x in (clist or [])), f"status={st}")

# 处理举报为已受理（≥2 条已受理将触发风险事件）
st, _ = req("POST", f"/api/complaints/{c1['id']}/handle", manager, {"status": "已受理", "note": "核实中"})
st2, _ = req("POST", f"/api/complaints/{c2['id']}/handle", manager, {"status": "已受理", "note": "核实中"})
check("处理举报(已受理)", st == 200 and st2 == 200, f"{st}/{st2}")

# 拉黑 m_a
st, bl = req("POST", "/api/blacklist", admin, {"memberId": m_a["id"], "reason": "高风险举报"})
check("拉黑会员(admin)", st == 200 and bl.get("status") == "生效", f"status={st}")
st, chk = req("GET", "/api/blacklist/check", manager, query={"memberId": m_a["id"]})
check("拉黑后校验为黑名单", st == 200 and chk.get("blacklisted") is True, f"blacklisted={chk.get('blacklisted')}")

# 拉黑限制牵线：用 m_a 建牵线应被拒
st, _ = req("POST", "/api/pairs", manager, {"maleId": m_a["id"], "femaleId": m_b["id"]})
check("黑名单会员被限制牵线(403)", st == 403, f"status={st}")

# 解封
st, allbl = req("GET", "/api/blacklist/all", manager)
bl_id = next((b["id"] for b in (allbl or []) if b.get("memberId") == m_a["id"] and b.get("status") == "生效"), None)
if bl_id:
    st, _ = req("POST", f"/api/blacklist/{bl_id}/unban", admin)
    check("解封黑名单(admin)", st == 200, f"status={st}")
    st, chk2 = req("GET", "/api/blacklist/check", manager, query={"memberId": m_a["id"]})
    check("解封后非黑名单", chk2.get("blacklisted") is False, f"blacklisted={chk2.get('blacklisted')}")

# 风险扫描
st, scan = req("POST", "/api/risk/scan", manager)
check("风险扫描可执行", st == 200 and isinstance(scan, dict), f"status={st} res={scan}")
st, revents = req("GET", "/api/risk/events", manager)
check("风险事件可查", st == 200 and isinstance(revents, list), f"status={st} n={len(revents or [])}")
# 应命中 m_a 被举报事件
hit = [e for e in (revents or []) if e.get("memberId") == m_a["id"] and e.get("type") == "被举报"]
check("被举报≥2次触发风险事件", len(hit) >= 1, f"hit={len(hit)}")
st, rsum = req("GET", "/api/risk/summary", manager)
check("风险概览可查", st == 200 and rsum.get("total", 0) >= 1, f"total={rsum.get('total') if st==200 else '?'}")
if hit:
    st, hd = req("POST", f"/api/risk/events/{hit[0]['id']}/handle", manager, {"note": "已约谈并标记"})
    check("处置风险事件", st == 200 and hd.get("handled") is True, f"status={st}")

# ============================================================
log("\n== 5.2.4 合规三件套 ==")

st, docs = req("GET", "/api/docs", admin)
check("合规文档列表可查", st == 200 and isinstance(docs, list), f"status={st}")
types = {d.get("type") for d in (docs or [])}
for t in ["用户协议", "隐私政策", "退费规则", "婚介服务合同"]:
    check(f"含模板[{t}]", t in types, f"types={types}")

st, d0 = req("GET", "/api/docs/" + urllib.parse.quote("用户协议", safe=""), admin)
check("按类型获取模板", st == 200 and d0.get("content"), f"status={st}")

v_before = d0.get("version")
st, dup = req("PUT", "/api/docs/" + urllib.parse.quote("隐私政策", safe=""), admin, {"title": "隐私保护政策(修订)", "content": "修订版内容..."})
check("更新模板并升版(admin)", st == 200 and dup.get("version") != v_before, f"v {v_before}→{dup.get('version') if st==200 else '?'}")
# 非管理员不可改
st, _ = req("PUT", "/api/docs/" + urllib.parse.quote("隐私政策", safe=""), manager, {"title": "x", "content": "y"})
check("非管理员更新模板→403", st == 403, f"status={st}")

# 强制同意留痕
all_ok = True
for dt in ["用户协议", "隐私政策", "退费规则", "婚介服务合同"]:
    st, cresp = req("POST", "/api/docs/consent", agent,
                    {"memberId": m_a["id"], "memberName": m_a["name"], "docType": dt, "version": "1.0", "channel": "付费"})
    if not (st == 200 and cresp.get("ok")):
        all_ok = False
    if dt == "婚介服务合同":
        check("四类全部同意后 acceptedAll=true", cresp.get("acceptedAll") is True, f"acceptedAll={cresp.get('acceptedAll')}")
check("四类合规同意均留痕", all_ok, "")
st, cons = req("GET", "/api/docs/consent/" + m_a["id"], manager)
check("同意记录可查(4条)", st == 200 and len(cons or []) >= 4, f"n={len(cons or [])}")

# ============================================================
log("\n== 权限控制 ==")
st, _ = req("GET", "/api/docs")
check("未登录→401", st == 401, f"status={st}")
st, _ = req("GET", "/api/blacklist", agent)
check("红娘查黑名单→403", st == 403, f"status={st}")
st, _ = req("GET", "/api/verify", agent)
check("红娘查核验→403", st == 403, f"status={st}")
st, _ = req("GET", "/api/complaints", agent)
check("红娘查举报列表→403", st == 403, f"status={st}")
st, _ = req("GET", "/api/docs", manager)
check("主管查合规文档→200", st == 200, f"status={st}")
st, _ = req("GET", "/api/risk/events", manager)
check("主管查风险事件→200", st == 200, f"status={st}")
st, _ = req("POST", "/api/complaints", agent, {"targetMemberId": m_a["id"], "type": "其他", "content": "t"})
check("红娘提交举报→200", st == 200, f"status={st}")

# ============================================================
log("\n== PII 脱敏（@Sensitive 按角色）==")
# 找一个 income 非空的会员
target = None
for m in mlist:
    if m.get("income"):
        target = m
        break
if not target:
    target = m_a
tid = target["id"]
st, d_admin = req("GET", f"/api/members/{tid}", admin)
st2, d_mgr = req("GET", f"/api/members/{tid}", manager)
inc_admin = d_admin.get("member", {}).get("income") if isinstance(d_admin, dict) else None
inc_mgr = d_mgr.get("member", {}).get("income") if isinstance(d_mgr, dict) else None
check("管理员可见明文收入", inc_admin and "*" not in str(inc_admin), f"admin_income={inc_admin}")
check("主管视角收入脱敏", inc_mgr is not None and "*" in str(inc_mgr), f"mgr_income={inc_mgr}")

# ============================================================
log("\n" + "=" * 50)
if failures:
    log(f"❌ 验证未通过，失败项 {len(failures)}：{failures}")
    sys.exit(1)
else:
    log("✅ 全链路 / 多维度验证全部通过（#110 信任合规）")
    sys.exit(0)
