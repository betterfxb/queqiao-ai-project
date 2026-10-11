"""#108 支付闭环 端到端验证：createPay -> simulate -> 对账无差异 -> refund 幂等"""
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


def get(path, token=None, query=None):
    url = BASE + path
    if query:
        url += "?" + urllib.parse.urlencode(query)
    h = dict(H)
    if token:
        h["Authorization"] = "Bearer " + token
    return _do(urllib.request.Request(url, headers=h, method="GET"))


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


print("=== 登录 manager ===")
st, login = post("/api/auth/login", {"username": "manager", "password": "123456"})
assert st == 200 and "token" in login, f"login failed: {st} {login}"
token = login["token"]
print("  login OK, role =", login.get("role"))

print("\n=== /api/pay/config ===")
st, cfg = get("/api/pay/config", token)
assert st == 200, f"config {st}"
print("  channels:", [(c["channel"], c["available"]) for c in cfg["channels"]])

print("\n=== createPay (线下) ===")
st, created = post("/api/pay/create", {
    "member": "验证会员A", "productId": "", "amount": 1999,
    "channel": "线下", "orderType": "新购", "agent": "红娘测试",
}, token)
assert st == 200, f"createPay {st} {created}"
oid = created["order"]["id"]
assert created["order"]["status"] == "待支付", created["order"]["status"]
print("  order", created["order"]["orderNo"], "status =", created["order"]["status"], "prepay.offline =", created["prepay"]["offline"])

print("\n=== simulate 支付回调（确认收款） ===")
st, paid = post(f"/api/pay/simulate/{oid}", {}, token)
assert st == 200, f"simulate {st} {paid}"
assert paid["status"] == "已完成", paid["status"]
assert paid.get("paidAt"), "paidAt 应为非空"
assert paid.get("tradeNo", "").startswith("OFFLINE-"), paid.get("tradeNo")
print("  status =", paid["status"], "| paidAt =", paid["paidAt"], "| tradeNo =", paid["tradeNo"])

print("\n=== simulate 再次调用（支付幂等）===")
st2, paid2 = post(f"/api/pay/simulate/{oid}", {}, token)
assert st2 == 200 and paid2["status"] == "已完成"
print("  仍为 已完成（幂等）")

print("\n=== 手动对账当日（管理员）===")
st, run = post("/api/pay/reconcile/run", {}, token)
# manager 不是 admin，预期 401/403；改用 admin 登录
print("  manager 触发结果:", st, "(预期无权限，下面用 admin)")

print("\n=== 登录 admin 并手动对账 ===")
st, alogin = post("/api/auth/login", {"username": "admin", "password": "123456"})
atoken = alogin["token"]
st, run = post("/api/pay/reconcile/run", {}, atoken)
assert st == 200, f"run reconcile {st} {run}"
print(f"  昨日 核对 {run['checked']} 笔 | 吻合 {run['matched']} 笔 | 差异 {run['diffCount']} 笔")

print("\n=== 对账今日（覆盖刚支付的订单，并覆盖历史脏差异）===")
today = _dt.date.today().isoformat()
st, run_today = post(f"/api/pay/reconcile/run?date={today}", {}, atoken)
assert st == 200, f"run today {st} {run_today}"
print(f"  今日 核对 {run_today['checked']} 笔 | 吻合 {run_today['matched']} 笔 | 差异 {run_today['diffCount']} 笔")
assert run_today["checked"] >= 1, "今日应至少核对到刚支付的订单"
assert run_today["diffCount"] == 0, "今日支付订单应账实相符"

print("\n=== /api/pay/reconcile 查询（30天，应已无差异）===")
st, rec = get("/api/pay/reconcile", atoken, {"days": 30})
assert st == 200
print("  diffCount =", rec["diffCount"], "| list len =", len(rec["list"]))
assert rec["diffCount"] == 0

print("\n=== refund 退款（幂等）===")
st, rf1 = post(f"/api/finance/orders/{oid}/refund", {"reason": "测试退款"}, atoken)
assert st == 200, f"refund1 {st} {rf1}"
print("  refund1 生成退款单:", rf1["orderNo"], "amount =", rf1["amount"], "status =", rf1["status"])
st, rf2 = post(f"/api/finance/orders/{oid}/refund", {"reason": "重复退款"}, atoken)
assert st == 200
print("  refund2 status =", rf2["status"], "(应仍为 已退款，不新增负数单)")
# 通过订单列表统计关联原单的退款单数量（退款单 status=已完成、type=退款）
st, orders = get("/api/finance/orders", atoken)
assert st == 200, f"orders {st} {orders}"
refund_only = [o for o in orders if isinstance(o, dict) and o.get("type") == "退款"
               and (o.get("remark") or "").find(created["order"]["orderNo"]) >= 0]
print("  关联原单的退款单数量 =", len(refund_only), "(预期 1，幂等)")
assert len(refund_only) == 1, f"退款单数量应为 1，实际 {len(refund_only)}"

print("\n✅ 全部断言通过：支付闭环 create→pay(幂等)→对账无差异→退款(幂等) 工作正常")
