"""#154-#159 已知边界与遗留项 全链路验证。
覆盖：
  A. 订单状态机加固（禁止绕过流水直写终态）+ 修复端点
  B. 优惠券系统（实体/服务/接口/转介绍发券/下单抵扣）
  C. 线索转化硬关联 memberId
  D. 社群档位支付打通
  E. Flyway + 密钥外部化（构建/启动层验证）
逐段可独立运行；失败即抛出 AssertionError。
"""
import json
import urllib.parse
import urllib.request
import datetime as _dt

BASE = "http://localhost:8080"
H = {"Content-Type": "application/json", "User-Agent": "verify"}


def post(path, body, token=None):
    h = dict(H)
    if token:
        h["Authorization"] = "Bearer " + token
    req = urllib.request.Request(BASE + path, data=json.dumps(body).encode(),
                                 headers=h, method="POST")
    return _do(req)


def put(path, body, token=None):
    h = dict(H)
    if token:
        h["Authorization"] = "Bearer " + token
    req = urllib.request.Request(BASE + path, data=json.dumps(body).encode(),
                                 headers=h, method="PUT")
    return _do(req)


def get(path, token=None, query=None):
    url = BASE + path
    if query:
        url += "?" + urllib.parse.urlencode(query)
    h = dict(H)
    if token:
        h["Authorization"] = "Bearer " + token
    return _do(urllib.request.Request(url, headers=h, method="GET"))


def delete(path, token=None, query=None):
    url = BASE + path
    if query:
        url += "?" + urllib.parse.urlencode(query)
    h = dict(H)
    if token:
        h["Authorization"] = "Bearer " + token
    return _do(urllib.request.Request(url, headers=h, method="DELETE"))


def _do(req):
    try:
        with urllib.request.urlopen(req, timeout=10) as r:
            return r.status, json.loads(r.read().decode() or "{}")
    except urllib.error.HTTPError as e:
        try:
            return e.code, json.loads(e.read().decode() or "{}")
        except Exception:
            return e.code, {"raw": e.read().decode()[:200]}
    except Exception as e:
        return 0, {"error": str(e)}


PASS, FAIL = [], []

# 每次运行唯一后缀：H2 为文件库且跨次运行持久化，测试数据必须隔离，避免累积干扰断言
SUF = _dt.datetime.now().strftime("%H%M%S")


def check(name, cond, extra=""):
    if cond:
        PASS.append(name)
        print(f"  [PASS] {name} {extra}")
    else:
        FAIL.append(name)
        print(f"  [FAIL] {name} {extra}")


def login(user="admin"):
    st, r = post("/api/auth/login", {"username": user, "password": "123456"})
    assert st == 200 and "token" in r, f"login {user} failed: {st} {r}"
    return r["token"]


def today():
    return _dt.date.today().isoformat()


# ----------------------------- A. 订单状态机加固 -----------------------------
def test_A(admin, manager):
    print("\n=== A. 订单状态机加固 ===")
    # A1: 新建直写「已完成」必须经状态机（确认收款），回填 paidAt + 渠道流水
    st, o = post("/api/finance/orders", {
        "member": "A状态机会员", "type": "收款", "amount": 1888,
        "status": "已完成", "channel": "线下", "orderType": "新购", "agent": "红娘甲",
    }, admin)
    check("A1 直写已完成→200", st == 200, f"st={st}")
    check("A1 终态经状态机(status=已完成)", o.get("status") == "已完成", f"status={o.get('status')}")
    check("A1 经确认收款回填paidAt", bool(o.get("paidAt")), f"paidAt={o.get('paidAt')}")
    oid = o.get("id")

    # 对账今日：该订单应有渠道流水，不应出现在差异列表
    st, run = post(f"/api/pay/reconcile/run?date={today()}", {}, admin)
    diff_ids = [d.get("orderId") for d in run.get("diffs", [])]
    check("A1 对账无差异(流水已建)", oid not in diff_ids, f"diffIds={diff_ids[:5]}")

    # A2: 新建直写「已退款」必须被拒绝
    st, r = post("/api/finance/orders", {
        "member": "A退款会员", "type": "退款", "amount": -100, "status": "已退款",
    }, admin)
    check("A2 直写已退款→被拒(非200)", st != 200, f"st={st} {r}")

    # A3: 编辑接口禁止直写终态「已完成」
    st, o2 = post("/api/finance/orders", {
        "member": "A编辑会员", "type": "收款", "amount": 999, "status": "处理中",
    }, admin)
    o2id = o2.get("id")
    st, r = put(f"/api/finance/orders/{o2id}", {"status": "已完成"}, admin)
    check("A3 编辑直写已完成→被拒(非200)", st != 200, f"st={st} {r}")
    # A3-负向：编辑为同级非终态应放行
    st, r = put(f"/api/finance/orders/{o2id}", {"remark": "允许编辑非终态"}, admin)
    check("A3-负向 编辑非终态→200", st == 200, f"st={st}")

    # A4: manager 不可调用修复端点；admin 可，且幂等
    st, r = post("/api/pay/reconcile/repair", {}, manager)
    check("A4 manager 调修复→无权限", st in (401, 403), f"st={st}")
    # 先清理历史累积（H2 持久化会保留旧订单，避免历史污染影响幂等断言）
    post("/api/pay/reconcile/repair", {}, admin)
    st, r1 = post("/api/pay/reconcile/repair", {}, admin)
    check("A4 修复无新增(已清理历史)", st == 200 and r1["ledgersCreated"] == 0
          and r1["commissionsBackfilled"] == 0 and r1["referralsBackfilled"] == 0,
          f"r1={r1}")
    st, r2 = post("/api/pay/reconcile/repair", {}, admin)
    check("A4 修复幂等(二次运行无新增)", st == 200 and r2["ledgersCreated"] == 0
          and r2["commissionsBackfilled"] == 0 and r2["referralsBackfilled"] == 0,
          f"r2={r2}")
    print("  repair 首次:", r1)


# ----------------------------- B. 优惠券系统 -----------------------------
def test_B(admin):
    print("\n=== B. 优惠券系统 ===")
    MID = "M-COUPON-TEST"
    MNAME = "优惠券测试会员"

    # B1: 管理员手动发放抵现券
    st, c = post("/api/coupons/issue", {
        "memberId": MID, "memberName": MNAME, "amount": 200, "days": 90,
    }, admin)
    check("B1 手动发券→200", st == 200 and c.get("status") == "未使用", f"st={st} {c}")
    cid = c.get("id")
    code = c.get("code")

    # B2: 钱包按会员可查到
    st, wallet = get("/api/coupons", admin, query={"memberId": MID})
    ids = [x.get("id") for x in wallet]
    check("B2 钱包查询到该券", st == 200 and cid in ids, f"ids={ids[:3]}")

    # B3: 下单使用优惠券 → 金额折减
    st, created = post("/api/pay/create", {
        "member": MNAME, "memberId": MID, "amount": 1000,
        "channel": "线下", "orderType": "新购", "couponId": cid,
    }, admin)
    check("B3 下单用券→200", st == 200, f"st={st} {created}")
    o = created.get("order", {})
    check("B3 金额折减(1000-200=800)", o.get("amount") == 800, f"amount={o.get('amount')}")
    check("B3 记录couponId/couponAmount", o.get("couponId") == cid and o.get("couponAmount") == 200,
          f"cid={o.get('couponId')} camt={o.get('couponAmount')}")
    oid = o.get("id")

    # B4: 支付成功 → 优惠券核销
    st, paid = post(f"/api/pay/simulate/{oid}", {}, admin)
    check("B4 支付→已完成", st == 200 and paid.get("status") == "已完成", f"st={st} {paid}")
    st, wallet = get("/api/coupons", admin, query={"memberId": MID, "status": "已使用"})
    used = [x for x in wallet if x.get("id") == cid]
    check("B4 优惠券已核销", len(used) == 1 and used[0].get("orderId") == oid,
          f"used={used}")

    # B5: 重复支付幂等（券不重复核销）
    st2, paid2 = post(f"/api/pay/simulate/{oid}", {}, admin)
    check("B5 重复支付幂等", st2 == 200 and paid2.get("status") == "已完成")

    # B6: 转介绍奖励=优惠券 → 自动向被推荐人发券（REFERRAL 来源，幂等）
    st, ref = post("/api/referral", {
        "referrerId": "M-REFERRER", "referrerType": "会员",
        "refereeName": "被推荐人B6", "refereePhone": "13800000006",
    }, admin)
    check("B6 登记转介绍→200", st == 200, f"st={st}")
    rid = ref.get("id")
    st, conv = post(f"/api/referral/{rid}/convert", {"memberId": "M-B6-REF", "orderAmount": 1000}, admin)
    check("B6 转化→200", st == 200, f"st={st}")
    st, rew = post(f"/api/referral/{rid}/reward", {"rewardType": "优惠券", "amount": 150}, admin)
    check("B6 发奖(优惠券)→200", st == 200 and rew.get("rewardType") == "优惠券", f"st={st} {rew}")
    st, wallet = get("/api/coupons", admin, query={"memberId": "M-B6-REF"})
    refs = [x for x in wallet if x.get("sourceType") == "REFERRAL" and x.get("sourceId") == rid]
    check("B6 被推荐人获REFERRAL券", len(refs) == 1, f"refs={refs}")
    # 幂等：再次发奖不应产生第二张券
    st, rew2 = post(f"/api/referral/{rid}/reward", {"rewardType": "优惠券", "amount": 150}, admin)
    st, wallet = get("/api/coupons", admin, query={"memberId": "M-B6-REF"})
    refs2 = [x for x in wallet if x.get("sourceType") == "REFERRAL" and x.get("sourceId") == rid]
    check("B6 发奖幂等(仅一张)", len(refs2) == 1, f"refs2={refs2}")


# ----------------------------- C. 线索转化硬关联 memberId -----------------------------
def test_C(admin):
    print("\n=== C. 线索转化硬关联 memberId ===")
    PHONE = f"13{SUF}001"  # 唯一手机号：确保每次运行都创建全新会员，避免历史会员订单累积污染 ROI

    # C1: 新建线索 → 转化为会员（新建/匹配），硬关联 memberId，阶程已成交，幂等
    st, lead = post("/api/crm/leads", {
        "name": "线索C1", "phone": PHONE, "channel": f"渠道C1-{SUF}", "agent": "红娘甲",
    }, admin)
    check("C1 新建线索→200", st == 200, f"st={st}")
    lid = lead.get("id")
    st, conv = post(f"/api/crm/leads/{lid}/convert", {}, admin)
    check("C1 转化→200", st == 200, f"st={st} {conv}")
    mid = conv.get("memberId")
    check("C1 硬关联memberId", mid is not None, f"mid={mid}")
    st, det = get(f"/api/crm/leads/{lid}", admin)
    check("C1 转化后置已成交", det.get("stage") == "已成交", f"stage={det.get('stage')}")
    st, conv2 = post(f"/api/crm/leads/{lid}/convert", {}, admin)
    check("C1 转化幂等(同会员)", st == 200 and conv2.get("memberId") == mid and conv2.get("alreadyConverted") is True,
          f"conv2={conv2}")

    # C2: 阶段变更到已成交 → 按手机号回退匹配已有会员（用 C1 的手机号新建第二条线索）
    st, lead2 = post("/api/crm/leads", {
        "name": "线索C2", "phone": PHONE, "channel": f"渠道C2-{SUF}",
    }, admin)
    lid2 = lead2.get("id")
    st, mv = post(f"/api/crm/leads/{lid2}/stage", {"stage": "已成交"}, admin)
    check("C2 阶段变更→200", st == 200, f"st={st} {mv}")
    check("C2 手机号回退硬关联同一会员", mv.get("memberId") == mid, f"mv.mid={mv.get('memberId')} expect={mid}")

    # C3: ROI 按 memberId 精准归因（C1 线索渠道=渠道C1-SUF，会员已支付 500）
    # 用「归因增量」断言：避免 H2 持久化 + 重启后业务号序号重置导致的历史订单污染
    st, roi0 = get("/api/channels/roi", admin)
    rows0 = roi0.get("rows", [])
    c1_0 = [r for r in rows0 if f"渠道C1-{SUF}" in str(r.get("name"))]
    rev_before = c1_0[0].get("revenue") if c1_0 else 0

    st, o = post("/api/finance/orders", {
        "memberId": mid, "member": "线索C1", "type": "收款", "amount": 500,
        "status": "已完成", "channel": "线下", "orderType": "新购",
    }, admin)
    check("C3 为会员下单并确认→200", st == 200, f"st={st}")

    st, roi = get("/api/channels/roi", admin)
    rows = roi.get("rows", [])
    c1 = [r for r in rows if f"渠道C1-{SUF}" in str(r.get("name"))]
    check("C3 ROI 含渠道C1 行", len(c1) >= 1, f"rows={[r.get('name') for r in rows]}")
    rev = c1[0].get("revenue") if c1 else 0
    check("C3 渠道C1 营收归因增量=500", rev - rev_before == 500,
          f"rev_after={rev} rev_before={rev_before}")


# ----------------------------- D. 社群档位支付打通 -----------------------------
def test_D(admin):
    print("\n=== D. 社群档位支付打通 ===")
    CITY = f"社群城D-{SUF}"

    # D1: 创建三档社群（admin），其中一档容量=1 用于满员校验
    st, vip = post("/api/city/tiers", {"city": CITY, "level": "VIP", "price": 299, "capacity": 50}, admin)
    check("D1 创建VIP档位→200", st == 200, f"st={st}")
    vipId = vip.get("id")
    st, cap = post("/api/city/tiers", {"city": CITY, "level": "轻会员", "price": 39.9, "capacity": 1}, admin)
    check("D1 创建容量=1档位→200", st == 200, f"st={st}")
    capId = cap.get("id")

    # D2: 订阅下单（requireAuth）→ 待支付订单，金额=档位价，弱关联 productId=tierId
    st, sub = post(f"/api/city/tiers/{vipId}/subscribe",
                   {"memberId": f"M-D-{SUF}", "memberName": "社群会员D"}, admin)
    check("D2 订阅下单→200", st == 200, f"st={st} {sub}")
    o = sub.get("order", {})
    check("D2 订单待支付", o.get("status") == "待支付", f"status={o.get('status')}")
    check("D2 金额=299", o.get("amount") == 299, f"amount={o.get('amount')}")
    check("D2 弱关联productId=tierId", o.get("productId") == vipId, f"pid={o.get('productId')}")
    oid = o.get("id")

    # D3: 模拟支付 → 开通真实 membership + 档位 memberCount+1 + overview 反映
    st, paid = post(f"/api/pay/simulate/{oid}", {}, admin)
    check("D3 支付→已完成", st == 200 and paid.get("status") == "已完成", f"st={st} {paid}")
    st, mem = get(f"/api/city/tiers/{vipId}/memberships", admin)
    check("D3 开通真实会员记录", st == 200 and len(mem) == 1 and mem[0].get("status") == "有效", f"mem={mem}")
    check("D3 会员绑定订单", bool(mem) and mem[0].get("orderId") == oid,
          f"oid={mem[0].get('orderId') if mem else None}")
    st, ov = get("/api/city/overview", admin, query={"city": CITY})
    vip_row = next((t for t in ov.get("tiers", []) if t.get("id") == vipId), None)
    check("D3 档位memberCount=1", vip_row is not None and vip_row.get("memberCount") == 1,
          f"mc={vip_row.get('memberCount') if vip_row else None}")
    check("D3 overview communityMembersActive>=1", ov.get("communityMembersActive", 0) >= 1,
          f"active={ov.get('communityMembersActive')}")

    # D4: 幂等——重复支付不重复开通会员
    st2, paid2 = post(f"/api/pay/simulate/{oid}", {}, admin)
    check("D4 重复支付→已完成(幂等)", st2 == 200 and paid2.get("status") == "已完成")
    st, mem2 = get(f"/api/city/tiers/{vipId}/memberships", admin)
    check("D4 会员记录仍唯一", len(mem2) == 1, f"mem2={mem2}")

    # D5: 容量校验——容量=1 档位，首次支付后二次订阅应被拒
    st, sub1 = post(f"/api/city/tiers/{capId}/subscribe",
                    {"memberId": f"M-D-CAP1-{SUF}", "memberName": "满员会员1"}, admin)
    check("D5 容量档首次订阅→200", st == 200, f"st={st}")
    capOid = sub1.get("order", {}).get("id")
    st, paid = post(f"/api/pay/simulate/{capOid}", {}, admin)
    check("D5 容量档支付→已完成", st == 200 and paid.get("status") == "已完成", f"st={st}")
    st, sub2 = post(f"/api/city/tiers/{capId}/subscribe",
                    {"memberId": f"M-D-CAP2-{SUF}", "memberName": "满员会员2"}, admin)
    check("D5 满员后订阅→被拒(非200)", st != 200, f"st={st} {sub2}")

    # D6: 未登录订阅应被拒（subscribe 需 requireAuth）
    st, r = post(f"/api/city/tiers/{vipId}/subscribe", {"memberId": "x", "memberName": "y"}, None)
    check("D6 未登录订阅→401", st in (401, 403), f"st={st}")


# --------------------- E. 对账口径一致性（已退款订单可回填） ---------------------
def test_E(admin):
    """回归：reconcile 稽核「已完成 + 已退款」，但回填/修复此前只覆盖「已完成」，
    导致已退款订单一旦流水缺失便永远无法修复、每日对账恒报差异。"""
    print("\n=== E. 对账口径一致性（已退款订单） ===")

    # E1: 走完整链路 下单 → 支付 → 退款
    st, created = post("/api/pay/create",
                       {"memberId": f"M-E-{SUF}", "member": f"对账会员E-{SUF}",
                        "amount": 500, "channel": "线下", "orderType": "新购"}, admin)
    check("E1 下单→200", st == 200, f"st={st} {created}")
    o = created.get("order", {})
    oid = o.get("id")
    st, paid = post(f"/api/pay/simulate/{oid}", {}, admin)
    check("E1 支付→已完成", st == 200 and paid.get("status") == "已完成", f"st={st}")
    pay_date = str(paid.get("paidAt") or "")[:10]
    st, rf = post(f"/api/finance/orders/{oid}/refund", {"reason": "对账口径回归"}, admin)
    check("E1 退款→200", st == 200, f"st={st} {rf}")

    # E2: 原单转「已退款」，仍在对账稽核范围内且不产生差异
    st, orders = get("/api/finance/orders", admin, query={"page": 1, "size": 2000})
    rows = orders if isinstance(orders, list) else (orders.get("list") or [])
    src = next((x for x in rows if x.get("id") == oid), {})
    check("E2 原单状态=已退款", src.get("status") == "已退款", f"status={src.get('status')}")

    # E3: 修复端点覆盖已退款订单且幂等
    st, r1 = post("/api/pay/reconcile/repair", {}, admin)
    check("E3 修复端点→200", st == 200, f"st={st} {r1}")
    st, r2 = post("/api/pay/reconcile/repair", {}, admin)
    check("E3 修复幂等(不再新建流水)", st == 200 and r2.get("ledgersCreated") == 0,
          f"r2={r2}")

    # E4: 当日对账应账实相符（含刚退款的订单）
    st, run = post(f"/api/pay/reconcile/run?date={pay_date}", {}, admin)
    check("E4 当日对账→200", st == 200, f"st={st}")
    check("E4 当日无差异", run.get("diffCount") == 0,
          f"checked={run.get('checked')} matched={run.get('matched')} diff={run.get('diffCount')} "
          f"diffs={[d.get('note') for d in (run.get('diffs') or [])][:3]}")
    check("E4 已退款订单纳入稽核", (run.get("checked") or 0) >= 1, f"checked={run.get('checked')}")


# --------------------- F. 前端契约（管理端实际调用路径） ---------------------
def test_F(admin):
    print("\n=== F. 前端契约（管理端调用路径） ===")

    # F1: 支付页券选择器 —— GET /api/coupons?memberId&status=未使用
    mid = f"M-F-{SUF}"
    st, c = post("/api/coupons/issue",
                 {"memberId": mid, "memberName": f"券会员F-{SUF}", "amount": 150, "days": 30}, admin)
    check("F1 发券→200", st == 200, f"st={st} {c}")
    st, usable = get("/api/coupons", admin, query={"memberId": mid, "status": "未使用"})
    check("F1 可用券查询→200", st == 200, f"st={st}")
    check("F1 仅返回未使用券",
          isinstance(usable, list) and usable and all(x.get("status") == "未使用" for x in usable),
          f"n={len(usable) if isinstance(usable, list) else usable}")
    check("F1 券含前端所需字段(amount/expireAt/sourceType)",
          all(("amount" in x and "expireAt" in x and "sourceType" in x) for x in usable),
          f"keys={sorted(usable[0].keys()) if usable else []}")

    # F2: 用券下单后，订单列表暴露 couponId / couponAmount（列表页展示实付与抵扣）
    st, created = post("/api/pay/create",
                       {"memberId": mid, "member": f"券会员F-{SUF}", "amount": 600,
                        "channel": "线下", "orderType": "新购", "couponId": c.get("id")}, admin)
    check("F2 用券下单→200", st == 200, f"st={st} {created}")
    fo = created.get("order", {})
    check("F2 实付=600-150=450", fo.get("amount") == 450, f"amount={fo.get('amount')}")
    st, orders = get("/api/finance/orders", admin, query={"page": 1, "size": 2000})
    rows = orders if isinstance(orders, list) else (orders.get("list") or [])
    row = next((x for x in rows if x.get("id") == fo.get("id")), {})
    check("F2 列表暴露couponId/couponAmount",
          row.get("couponId") == c.get("id") and row.get("couponAmount") == 150,
          f"couponId={row.get('couponId')} couponAmount={row.get('couponAmount')}")

    # F3: 增长页档位表格 —— status 字段驱动「订阅」按钮可用性
    st, tiers = get("/api/city/tiers", admin)
    check("F3 档位列表→200", st == 200 and isinstance(tiers, list), f"st={st}")
    check("F3 档位含status字段", all("status" in t for t in tiers), "缺少 status 将导致前端订阅按钮判断失效")
    check("F3 档位含容量/人数字段",
          all(("capacity" in t and "memberCount" in t) for t in tiers))

    # F4: 全量社群会员名单（增长页名单弹窗）
    st, all_mem = get("/api/city/memberships", admin)
    check("F4 全量会员名单→200", st == 200 and isinstance(all_mem, list), f"st={st}")
    check("F4 名单含前端所需字段",
          (not all_mem) or all(
              ("memberName" in m and "tierLevel" in m and "status" in m
               and "activeFrom" in m and "activeTo" in m and "orderId" in m)
              for m in all_mem),
          f"keys={sorted(all_mem[0].keys()) if all_mem else []}")

    # F5: 下架档位不可订阅（前端按钮置灰的后端兜底）
    st, t = post("/api/city/tiers",
                 {"city": f"下架城F-{SUF}", "level": "轻会员", "price": 99, "capacity": 0}, admin)
    check("F5 建档位→200", st == 200, f"st={st}")
    tid = t.get("id")
    st, _ = put(f"/api/city/tiers/{tid}", {"status": "已下架"}, admin)
    check("F5 下架→200", st == 200, f"st={st}")
    st, r = post(f"/api/city/tiers/{tid}/subscribe",
                 {"memberId": f"M-F5-{SUF}", "memberName": "下架订阅"}, admin)
    check("F5 下架档位订阅→被拒", st != 200 and "下架" in str(r), f"st={st} {r}")


if __name__ == "__main__":
    print("=== 登录 ===")
    admin = login("admin")
    manager = login("manager")
    test_A(admin, manager)
    test_B(admin)
    test_C(admin)
    test_D(admin)
    test_E(admin)
    test_F(admin)
    print(f"\n=== 结果：PASS={len(PASS)} FAIL={len(FAIL)} ===")
    if FAIL:
        print("FAILED:", FAIL)
        raise SystemExit(1)
    print("ALL BOUNDARY A+B+C+D+E+F CHECKS PASSED")
