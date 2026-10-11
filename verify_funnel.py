import json, urllib.request, urllib.error

BASE = "http://localhost:8080"

def post(path, body):
    req = urllib.request.Request(BASE + path, data=json.dumps(body).encode(),
                                 headers={"Content-Type": "application/json",
                                          "User-Agent": "Mozilla/5.0 verify"}, method="POST")
    with urllib.request.urlopen(req, timeout=10) as r:
        return json.loads(r.read())

def get(path, token):
    req = urllib.request.Request(BASE + path,
                                 headers={"Authorization": "Bearer " + token,
                                          "User-Agent": "Mozilla/5.0 verify"}, method="GET")
    with urllib.request.urlopen(req, timeout=10) as r:
        return json.loads(r.read())

print("== login as manager ==")
login = post("/api/auth/login", {"username": "manager", "password": "123456"})
token = login.get("token")
print("token acquired:", bool(token), "role:", login.get("user", {}).get("role"))

print("\n== /api/ops/funnel?days=0 ==")
data = get("/api/ops/funnel", token)
print("window:", data.get("window"))
print("stages:")
for s in data["stages"]:
    conv = "起点" if s.get("prevCount") is None else f"{s['conversion']*100:.1f}%"
    print(f"  {s['name']:<4} count={s['count']:<5} 层间转化={conv}")
print(f"\n整体成功率(成功/注册) = {data['overallSuccessRate']*100:.2f}%  (基准 {data['benchmarkSuccessRate']*100:.1f}%)")
print(f"整体付费率(付费/注册) = {data['overallPaidRate']*100:.2f}%  (基准 {data['benchmarkPaidRate']*100:.1f}%)")
print("agentEfficiency rows:", len(data.get("agentEfficiency", [])))

# 断言：六层都在
names = [s["name"] for s in data["stages"]]
assert names == ["注册", "认证", "付费", "牵线", "约见", "成功"], names
assert all(isinstance(s["count"], int) for s in data["stages"])
print("\nASSERT PASS: 六层漏斗数据正确返回")
