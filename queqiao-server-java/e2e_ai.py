# -*- coding: utf-8 -*-
"""端到端验证：Java 业务后端 -> Python AI 服务 代理链路。"""
import json
import urllib.request

BASE = "http://127.0.0.1:8080"
HDR = {"User-Agent": "Mozilla/5.0 (queqiao-e2e; compatible)"}
TOKEN = None


def call(method, path, body=None, auth=True):
    url = BASE + path
    data = None
    headers = dict(HDR)
    if body is not None:
        data = json.dumps(body).encode("utf-8")
        headers["Content-Type"] = "application/json"
    if auth and TOKEN:
        headers["Authorization"] = "Bearer " + TOKEN
    req = urllib.request.Request(url, data=data, headers=headers, method=method)
    try:
        with urllib.request.urlopen(req, timeout=15) as r:
            return r.status, json.loads(r.read().decode("utf-8"))
    except urllib.error.HTTPError as e:
        return e.code, e.read().decode("utf-8", "replace")
    except Exception as e:  # noqa
        return 0, str(e)


def show(name, code, body):
    print(f"\n=== {name} -> HTTP {code} ===")
    if isinstance(body, (dict, list)):
        print(json.dumps(body, ensure_ascii=False, indent=2)[:1100])
    else:
        print(str(body)[:600])


# 1. 登录
code, body = call("POST", "/api/auth/login", {"username": "admin", "password": "123456"}, auth=False)
if code != 200 or "token" not in body:
    show("LOGIN FAILED", code, body)
    raise SystemExit(1)
TOKEN = body["token"]
print("LOGIN OK, user:", body.get("user", {}).get("name"), "role:", body.get("user", {}).get("role"))

# 2. 取数据 ID
code, audits = call("GET", "/api/audits")
audit_id = (audits.get("list") or [{}])[0].get("id")
print("audit id =", audit_id)

code, board = call("GET", "/api/crm/board")
lead_id = None
if isinstance(board, list):
    for col in board:
        cards = col.get("cards") or []
        if cards:
            lead_id = cards[0].get("id")
            break
print("lead id =", lead_id)

code, convs = call("GET", "/api/conversations")
conv_id = (convs if isinstance(convs, list) else [])[0].get("id") if isinstance(convs, list) else None
print("conversation id =", conv_id)

# 3. 审计建议（代理 /audit-suggest）
if audit_id:
    show("POST /api/audits/{id}/ai-suggest", *call("POST", f"/api/audits/{audit_id}/ai-suggest"))

# 4. 分配建议（代理 /assign-suggest）
show("POST /api/agents/assign-suggest", *call("POST", "/api/agents/assign-suggest", {
    "member": {"id": "M1001", "name": "张伟", "city": "上海"},
    "memberLevel": "SVIP",
    "memberCity": "上海",
    "agents": [
        {"id": "A1", "name": "红娘小雨", "level": "金牌红娘", "status": "在职", "memberCount": 40, "capacity": 80, "successRate": 72, "rating": 4.9, "region": "上海"},
        {"id": "A2", "name": "红娘小美", "level": "资深红娘", "status": "在职", "memberCount": 70, "capacity": 80, "successRate": 60, "rating": 4.6, "region": "北京"},
    ],
}))

# 5. 话术建议（代理 /reply-suggest）
if conv_id:
    show("POST /api/conversations/{id}/reply-suggest", *call("POST", f"/api/conversations/{conv_id}/reply-suggest"))

# 6. 下一步动作（代理 /next-action）
if lead_id:
    show("POST /api/crm/leads/{id}/next-action", *call("POST", f"/api/crm/leads/{lead_id}/next-action"))
