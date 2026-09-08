# -*- coding: utf-8 -*-
"""对**已启动服务**的带断言冒烟检查（可作 CI 门禁，失败 exit 1）。

原无断言版本的 happy path 已迁移到 tests/test_ai_service.py（无需起服务）。
本脚本仅用于部署后验活：
    .venv\\Scripts\\python.exe smoke.py [http://127.0.0.1:8090]
"""
import json
import sys
import urllib.error
import urllib.request

BASE = (sys.argv[1] if len(sys.argv) > 1 else "http://127.0.0.1:8090").rstrip("/")
failures: list[str] = []


def call(method: str, path: str, payload=None):
    data = json.dumps(payload).encode("utf-8") if payload is not None else None
    req = urllib.request.Request(
        BASE + path, data=data, method=method,
        headers={"Content-Type": "application/json"},
    )
    try:
        with urllib.request.urlopen(req, timeout=10) as r:
            return r.status, json.loads(r.read().decode("utf-8"))
    except urllib.error.HTTPError as e:
        return e.code, e.read().decode("utf-8", "replace")
    except Exception as e:  # noqa: BLE001
        return 0, str(e)


def check(name: str, cond: bool, detail: str = "") -> None:
    print(("[PASS] " if cond else "[FAIL] ") + name + (("  " + detail) if detail and not cond else ""))
    if not cond:
        failures.append(name)


code, body = call("GET", "/health")
check("GET /health", code == 200 and isinstance(body, dict) and body.get("status") == "ok", str(body))

code, body = call("POST", "/parse", {
    "text": "姓名：张伟，28岁，男生，上海人，本科，年收入30万，爱好摄影和旅行，手机13800138000"})
ok = code == 200 and body["fields"].get("name") == "张伟" and body["fields"].get("age") == 28
check("POST /parse 抽取正确", ok, str(body))
check("POST /parse 手机号已脱敏", code == 200 and body["fields"].get("phone") == "138****8000", str(body))

code, body = call("POST", "/match", {
    "target": {"name": "李娜", "gender": "女", "age": 27, "city": "上海", "education": "本科",
               "tags": ["摄影", "旅行"], "requirements": {"ageMin": 26, "ageMax": 32}},
    "candidates": [
        {"name": "王强", "gender": "男", "age": 29, "city": "上海", "education": "本科", "tags": ["摄影"]},
        {"name": "陈晨", "gender": "女", "age": 30, "city": "北京", "education": "硕士", "tags": []},
    ],
    "top": 5,
})
check("POST /match 命中 1 人", code == 200 and body.get("total") == 1, str(body))
check("POST /match 同性别显式 rejected",
      code == 200 and body.get("rejected_total") == 1
      and body["rejected"][0]["reason"] == "same_gender", str(body))

code, body = call("POST", "/audit-suggest", {
    "type": "会员升级", "applicant": "张伟", "urgency": "普通", "memberId": "M1001",
    "material": "支付凭证", "changeField": "收入", "changeFrom": "30万", "changeTo": "80万",
    "payText": "已收SVIP费用", "paid": True, "history": ["前次实名认证通过"]})
check("POST /audit-suggest", code == 200 and body.get("decision") in ("approve", "review", "reject"), str(body))

code, body = call("POST", "/reply-suggest", {"name": "张伟", "messages": [
    {"sender": "member", "content": "你们这个会员费多少钱啊"},
    {"sender": "agent", "content": "我们分三档"},
    {"sender": "member", "content": "我再考虑一下费用的问题"}]})
check("POST /reply-suggest", code == 200 and len(body.get("suggestions", [])) == 3, str(body))

code, body = call("POST", "/assign-suggest", {
    "member": {"id": "M1001", "name": "张伟", "city": "上海"},
    "memberLevel": "SVIP", "memberCity": "上海",
    "agents": [
        {"id": "A1", "name": "红娘小雨", "level": "金牌红娘", "status": "在职", "memberCount": 40,
         "capacity": 80, "successRate": 72, "rating": 4.9, "region": "上海"},
        {"id": "A2", "name": "红娘小美", "level": "资深红娘", "status": "在职", "memberCount": 70,
         "capacity": 80, "successRate": 60, "rating": 4.6, "region": "北京"},
        {"id": "A3", "name": "红娘小南", "level": "普通红娘", "status": "离职", "memberCount": 10,
         "capacity": 80, "successRate": 40, "rating": 4.2, "region": "上海"}]})
check("POST /assign-suggest 选中 A1", code == 200 and body.get("agentId") == "A1", str(body))

code, body = call("POST", "/next-action", {
    "name": "张伟", "stage": "匹配推荐", "level": "SVIP", "stayDays": 12,
    "fresh": "已跟进", "follows": ["初访", "需求确认"]})
check("POST /next-action 高优先级", code == 200 and body.get("priority") == "高", str(body))

print("\n%d 项检查，失败 %d 项" % (9, len(failures)))
sys.exit(1 if failures else 0)
