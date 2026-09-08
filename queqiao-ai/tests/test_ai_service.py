# -*- coding: utf-8 -*-
"""鹊桥 AI 服务测试：端点 happy path（原 smoke.py 迁移）+ 负路径 + 纯函数单测。

运行：D:\\ai_project\\queqiao-ai\\.venv\\Scripts\\python.exe -m pytest -q
"""
import os
import sys

import pytest
from fastapi.testclient import TestClient

sys.path.insert(0, os.path.dirname(os.path.dirname(os.path.abspath(__file__))))

import main  # noqa: E402
from main import (  # noqa: E402
    MAX_TEXT_LEN,
    Member,
    detect_gender,
    detect_sentiment,
    extract_name,
    extract_phone,
    mask_phone,
    parse_entities,
    score_pair,
)

client = TestClient(main.app)


@pytest.fixture(autouse=True)
def _reset_feedback():
    """每个用例前把在线学习权重复位，避免用例间互相污染。"""
    for d in main.FEEDBACK_WEIGHTS:
        main.FEEDBACK_WEIGHTS[d] = 1.0
        main.FEEDBACK_STATS[d] = {"accept": 0, "reject": 0}
    main.FEEDBACK_TOTAL.update({"accept": 0, "reject": 0, "applied": 0})
    yield


# ================================================================ happy path（原 smoke）
def test_health():
    r = client.get("/health")
    assert r.status_code == 200
    assert r.json() == {"status": "ok", "service": "queqiao-ai"}


def test_parse_happy_path():
    r = client.post("/parse", json={
        "text": "姓名：张伟，28岁，男生，上海人，本科，年收入30万，爱好摄影和旅行，手机13800138000"
    })
    assert r.status_code == 200
    body = r.json()
    f = body["fields"]
    assert f["name"] == "张伟"
    assert f["age"] == 28
    assert f["gender"] == "男"
    assert f["education"] == "本科"
    assert f["city"] == "上海"
    assert f["income"] == "30万+"
    assert set(f["tags"]) == {"摄影", "旅行"}
    assert body["field_count"] == len(f)
    assert len(body["entity_tags"]) >= 5


def test_match_happy_path():
    r = client.post("/match", json={
        "target": {"name": "李娜", "gender": "女", "age": 27, "city": "上海",
                   "education": "本科", "tags": ["摄影", "旅行"],
                   "requirements": {"ageMin": 26, "ageMax": 32}},
        "candidates": [
            {"name": "王强", "gender": "男", "age": 29, "city": "上海",
             "education": "本科", "tags": ["摄影"]},
            {"name": "赵磊", "gender": "男", "age": 45, "city": "北京",
             "education": "高中", "tags": []},
        ],
        "top": 5,
    })
    assert r.status_code == 200
    body = r.json()
    assert body["total"] == 2
    assert body["results"][0]["member"]["name"] == "王强"
    assert body["results"][0]["score"] > body["results"][1]["score"]
    assert 0 <= body["results"][0]["score"] <= 100


def test_audit_suggest_happy_path():
    r = client.post("/audit-suggest", json={
        "type": "会员升级", "applicant": "张伟", "gender": "男", "age": 28,
        "urgency": "普通", "memberId": "M1001", "material": "支付凭证",
        "changeField": "收入", "changeFrom": "30万", "changeTo": "80万",
        "payText": "已收SVIP费用", "paid": True, "history": ["前次实名认证通过"],
    })
    assert r.status_code == 200
    body = r.json()
    assert body["decision"] in ("approve", "review", "reject")
    assert 0 <= body["confidence"] <= 100
    assert any("收入" in x for x in body["risks"])


def test_reply_suggest_happy_path():
    r = client.post("/reply-suggest", json={
        "name": "张伟", "kind": "member",
        "messages": [
            {"sender": "member", "content": "你们这个会员费多少钱啊"},
            {"sender": "agent", "content": "我们分三档"},
            {"sender": "member", "content": "我再考虑一下费用的问题"},
        ],
    })
    assert r.status_code == 200
    body = r.json()
    assert body["intent"] in ("价格咨询", "犹豫顾虑")
    assert len(body["suggestions"]) == 3
    assert body["context_size"] == 3


def test_assign_suggest_happy_path():
    r = client.post("/assign-suggest", json={
        "member": {"id": "M1001", "name": "张伟", "city": "上海"},
        "memberLevel": "SVIP", "memberCity": "上海",
        "agents": [
            {"id": "A1", "name": "红娘小雨", "level": "金牌红娘", "status": "在职",
             "memberCount": 40, "capacity": 80, "successRate": 72, "rating": 4.9, "region": "上海"},
            {"id": "A2", "name": "红娘小美", "level": "资深红娘", "status": "在职",
             "memberCount": 70, "capacity": 80, "successRate": 60, "rating": 4.6, "region": "北京"},
            {"id": "A3", "name": "红娘小南", "level": "普通红娘", "status": "离职",
             "memberCount": 10, "capacity": 80, "successRate": 40, "rating": 4.2, "region": "上海"},
        ],
    })
    assert r.status_code == 200
    body = r.json()
    assert body["agentId"] == "A1"
    assert len(body["candidates"]) == 2          # 离职红娘被过滤


def test_next_action_happy_path():
    r = client.post("/next-action", json={
        "name": "张伟", "stage": "匹配推荐", "level": "SVIP",
        "stayDays": 12, "fresh": "已跟进", "follows": ["初访", "需求确认"],
    })
    assert r.status_code == 200
    body = r.json()
    assert body["priority"] == "高"
    assert body["slaDays"] == 3
    assert body["warnings"]


def test_followup_summary_and_coach_and_moderate_and_feedback():
    r = client.post("/followup-summary", json={
        "memberId": "M1", "name": "张伟", "level": "SVIP",
        "follows": [{"type": "电话", "content": "首访", "outcome": "成交意向"}],
    })
    assert r.status_code == 200 and r.json()["intention"] == "高意向"

    r = client.post("/coach/profile", json={"name": "张伟", "age": 28, "city": "上海"})
    assert r.status_code == 200 and 0 <= r.json()["score"] <= 100

    r = client.post("/moderate", json={"text": "加我微信 abcdef123 私下交易", "scene": "message"})
    assert r.status_code == 200 and r.json()["action"] in ("warn", "block")

    r = client.post("/feedback", json={"items": [
        {"scene": "match", "itemId": "p1", "action": "accept", "dimensions": ["age"]}
    ]})
    assert r.status_code == 200 and r.json()["appliedDimensions"] == 1
    assert client.get("/feedback/stats").status_code == 200


# ================================================================ 负路径 · /parse
def test_parse_empty_text():
    r = client.post("/parse", json={"text": ""})
    assert r.status_code == 200
    body = r.json()
    assert body["fields"] == {}
    assert body["field_count"] == 0
    assert body["entity_tags"] == []


def test_parse_missing_text_field_defaults_empty():
    r = client.post("/parse", json={})
    assert r.status_code == 200
    assert r.json()["field_count"] == 0


def test_parse_oversize_text_is_truncated_not_rejected():
    """条目 6：MB 级输入不应打挂服务，也不应 422，而是截断处理。"""
    text = "旅行" * 4000 + "28岁"          # 8000 字符，尾部年龄将被截掉
    assert len(text) > MAX_TEXT_LEN
    r = client.post("/parse", json={"text": text})
    assert r.status_code == 200
    assert "age" not in r.json()["fields"]   # 超出 5000 的内容未参与抽取


# --- 手机号词边界 / 脱敏 ---
def test_phone_masked_in_response():
    r = client.post("/parse", json={"text": "手机13800138000"})
    assert r.json()["fields"]["phone"] == "138****8000"
    assert "13800138000" not in r.text        # PII 不得明文回传


@pytest.mark.parametrize("text", [
    "身份证号110105198001011234",             # 18 位身份证
    "订单号20240013800138000",                # 长订单号
    "银行卡6222021234567890123",
    "工号138001380001",                       # 12 位，右侧多一位
    "QQ号13800138000",                        # QQ 上下文
])
def test_phone_word_boundary_no_false_positive(text):
    """条目 1：11 位数字不得从更长数字串或非手机上下文中被截出。"""
    assert extract_phone(text) is None
    assert "phone" not in parse_entities(text)


def test_phone_segment_validation():
    assert extract_phone("联系方式12800138000") is None   # 12 号段非法
    assert extract_phone("联系方式13800138000") == "13800138000"


def test_phone_context_priority():
    text = "会员编号15912345678888，手机号13512345678"
    assert extract_phone(text) == "13512345678"


# --- 年龄边界 ---
@pytest.mark.parametrize("text,expect", [
    ("28岁", 28), ("18岁", 18), ("99岁", 99),
])
def test_age_in_range(text, expect):
    assert parse_entities(text)["age"] == expect


@pytest.mark.parametrize("text", ["300岁", "5岁", "17岁", "120岁", "1000岁"])
def test_age_out_of_range_dropped(text):
    """条目 3：不得把「300岁」截成「30岁」，越界直接丢弃。"""
    w = []
    fields = parse_entities(text, w)
    assert "age" not in fields
    assert w


def test_parse_age_warning_exposed_in_response():
    r = client.post("/parse", json={"text": "300岁"})
    assert r.status_code == 200
    assert r.json()["warnings"]


# --- 收入误判 ---
@pytest.mark.parametrize("text", ["月薪2万", "有房子1500万", "存款50万", "彩礼20万"])
def test_income_false_positive_rejected(text):
    """条目 4：非「年收入/年薪」上下文的金额不得当作年收入。"""
    assert "income" not in parse_entities(text)


@pytest.mark.parametrize("text,expect", [
    ("年收入30万", "30万+"),
    ("年薪50万", "50万+"),
    ("年收入30-50万", "30-50万"),
    ("年收入 30 到 50 万", "30-50万"),
])
def test_income_valid_context(text, expect):
    assert parse_entities(text)["income"] == expect


def test_income_absurd_value_dropped():
    w = []
    assert "income" not in parse_entities("年薪9999万", w)
    assert w


# --- 职业最长匹配 ---
def test_job_longest_match():
    """条目 5：更具体的「互联网总监」应优先于「总监」。"""
    assert parse_entities("他是互联网总监")["job"] == "互联网总监"
    assert parse_entities("他是金融分析师")["job"] == "金融分析师"
    assert parse_entities("他是总监")["job"] == "总监"


# --- 性别冲突 ---
def test_gender_strong_conflict_returns_none():
    """条目 11：男/女强标记同现 -> 置空 + 告警，不再后者覆盖。"""
    w = []
    assert detect_gender("男生想找女生", w) is None
    assert w


def test_gender_weak_conflict_first_wins():
    w = []
    assert detect_gender("他和她都来了", w) == "男"
    assert w
    assert detect_gender("她和他都来了", []) == "女"


def test_gender_explicit_field_wins():
    assert detect_gender("性别：女，他的朋友也来了", []) == "女"


def test_gender_single_marker():
    assert detect_gender("28岁女士", []) == "女"
    assert detect_gender("28岁男士", []) == "男"


# --- 姓名称谓 ---
@pytest.mark.parametrize("text", ["王哥，28岁", "陈姐，30岁", "李叔，50岁", "张姨，48岁", "刘总，40岁"])
def test_surname_title_not_treated_as_name(text):
    """条目 16：称谓词不应被当作姓名。"""
    assert extract_name(text) is None


def test_real_name_still_extracted():
    assert extract_name("张伟，28岁") == "张伟"
    assert extract_name("姓名：李小明") == "李小明"
    assert extract_name("他叫王大锤") == "王大锤"


# ================================================================ 负路径 · /match
def test_match_same_gender_explicit_rejected():
    """条目 2：同性别不再静默丢弃，返回 rejected + reason。"""
    r = client.post("/match", json={
        "target": {"name": "李娜", "gender": "女", "age": 27},
        "candidates": [{"name": "陈晨", "gender": "女", "age": 28}],
        "top": 5,
    })
    assert r.status_code == 200
    body = r.json()
    assert body["total"] == 0 and body["results"] == []
    assert body["rejected_total"] == 1
    assert body["rejected"][0]["rejected"] is True
    assert body["rejected"][0]["reason"] == "same_gender"
    assert body["rejected"][0]["reason_label"]


def test_match_gender_missing_is_degraded_not_dropped():
    r = client.post("/match", json={
        "target": {"name": "李娜", "gender": "", "age": 27, "city": "上海"},
        "candidates": [{"name": "王强", "gender": "", "age": 28, "city": "上海"}],
        "top": 5,
    })
    body = r.json()
    assert body["total"] == 1
    item = body["results"][0]
    assert item["degraded"] is True
    assert item["reason"] == "gender_missing"
    assert "gender" in item["missing_features"]


def test_match_empty_candidates():
    r = client.post("/match", json={"target": {"name": "李娜", "gender": "女"}, "candidates": []})
    assert r.status_code == 200
    assert r.json() == {"total": 0, "results": [], "rejected_total": 0, "rejected": []}


@pytest.mark.parametrize("top", [0, -1, 101, 9999])
def test_match_top_out_of_range_rejected(top):
    """条目 13：top 必须落在 [1,100]。"""
    r = client.post("/match", json={
        "target": {"gender": "女"}, "candidates": [{"gender": "男"}], "top": top,
    })
    assert r.status_code == 422


def test_match_score_normalized_and_discriminative():
    """条目 12：不再出现大量并列 99，且完全不匹配不再白拿 50 分。"""
    target = {"gender": "女", "age": 28, "city": "上海", "education": "本科",
              "income": "30万", "tags": ["摄影", "旅行", "健身"],
              "requirements": {"ageMin": 27, "ageMax": 33, "heightMin": 170, "heightMax": 190}}
    r = client.post("/match", json={
        "target": target,
        "candidates": [
            {"name": "满分", "gender": "男", "age": 30, "city": "上海", "education": "硕士",
             "height": 178, "income": "40万", "tags": ["摄影", "旅行", "健身"]},
            {"name": "次优", "gender": "男", "age": 30, "city": "上海", "education": "本科",
             "height": 175, "income": "30万", "tags": ["摄影"]},
            {"name": "无关", "gender": "男", "age": 55, "city": "北京", "education": "高中",
             "height": 160, "income": "3万", "tags": ["宠物"]},
        ],
        "top": 10,
    })
    res = r.json()["results"]
    scores = [x["score"] for x in res]
    assert scores == sorted(scores, reverse=True)
    assert len(set(scores)) == 3                    # 有区分度，无并列
    assert scores[0] == 100
    assert scores[-1] < 20                          # 完全不匹配不再拿 50 分
    assert res[-1]["missing_features"]


def test_match_missing_features_recorded():
    r = client.post("/match", json={
        "target": {"gender": "女", "age": 28},
        "candidates": [{"gender": "男", "age": 29}],
    })
    item = r.json()["results"][0]
    for dim in ("education", "city", "hobby", "height", "income"):
        assert dim in item["missing_features"]


def test_match_single_bound_age_still_scored():
    """条目 15：只给 ageMin 或 ageMax 时年龄维度仍需参与打分。"""
    lo_only = score_pair(
        Member(gender="女", requirements={"ageMin": 30}), Member(gender="男", age=35))
    lo_bad = score_pair(
        Member(gender="女", requirements={"ageMin": 30}), Member(gender="男", age=20))
    assert lo_only["score"] > lo_bad["score"]

    hi_only = score_pair(
        Member(gender="女", requirements={"ageMax": 30}), Member(gender="男", age=28))
    hi_bad = score_pair(
        Member(gender="女", requirements={"ageMax": 30}), Member(gender="男", age=50))
    assert hi_only["score"] > hi_bad["score"]


def test_match_height_range_and_income_scored():
    """条目 14：heightMax 生效、income 参与评分。"""
    req = {"heightMin": 170, "heightMax": 180, "incomeMin": "30万"}
    ok = score_pair(Member(gender="女", requirements=req),
                    Member(gender="男", height=175, income="40万"))
    too_tall = score_pair(Member(gender="女", requirements=req),
                          Member(gender="男", height=200, income="40万"))
    poor = score_pair(Member(gender="女", requirements=req),
                      Member(gender="男", height=175, income="5万"))
    assert ok["score"] > too_tall["score"]
    assert ok["score"] > poor["score"]
    assert "身高符合要求" in ok["highlights"]
    assert "年收入满足要求" in ok["highlights"]


def test_score_pair_pure_function_contract():
    r = score_pair(Member(gender="女"), Member(gender="女"))
    assert r["rejected"] is True and r["score"] == 0 and r["reason"] == "same_gender"


# ================================================================ 负路径 · 其他端点
def test_audit_missing_materials():
    """缺材料应显著扣分并给出风险点。"""
    r = client.post("/audit-suggest", json={
        "type": "实名认证", "applicant": "李四", "materials": ["身份证正面"], "urgency": "紧急",
    })
    body = r.json()
    assert body["decision"] in ("review", "reject")
    assert any("缺少材料" in x for x in body["risks"])
    assert body["priority"] == "高"


def test_audit_unpaid_upgrade_rejected():
    r = client.post("/audit-suggest", json={"type": "会员升级", "paid": False, "materials": []})
    body = r.json()
    assert body["decision"] == "reject"
    assert any("款项未到账" in x for x in body["risks"])


def test_audit_empty_payload():
    r = client.post("/audit-suggest", json={})
    assert r.status_code == 200
    assert r.json()["decision"] in ("approve", "review", "reject")


@pytest.mark.parametrize("content,expect", [
    ("这个不好", "消极"),
    ("感觉不合适", "消极"),
    ("算了吧", "消极"),
    ("没感觉", "消极"),
    ("好的没问题", "积极"),
    ("挺不错的", "积极"),
    ("可以，期待", "积极"),
    ("嗯", "中性"),
])
def test_sentiment_negation_priority(content, expect):
    """条目 10：否定词优先，「不好」不得被判为积极。"""
    assert detect_sentiment(content) == expect


def test_reply_suggest_negative_sentiment_endpoint():
    r = client.post("/reply-suggest", json={
        "messages": [{"sender": "member", "content": "这个人不好，我不满意"}]
    })
    assert r.json()["sentiment"] == "消极"


def test_reply_suggest_empty_messages():
    r = client.post("/reply-suggest", json={"messages": []})
    body = r.json()
    assert body["intent"] == "常规沟通"
    assert body["context_size"] == 0
    assert len(body["suggestions"]) == 3


def test_assign_suggest_no_available_agent():
    r = client.post("/assign-suggest", json={
        "member": {"id": "M1"},
        "agents": [{"id": "A3", "name": "小南", "status": "离职"}],
    })
    body = r.json()
    assert body["agent"] is None and body["candidates"] == []


def test_next_action_unknown_stage():
    r = client.post("/next-action", json={"name": "x", "stage": "未知阶段", "stayDays": 0})
    body = r.json()
    assert "补全线索信息" in body["action"]
    assert body["priority"] in ("低", "中", "高")


def test_followup_summary_no_records():
    r = client.post("/followup-summary", json={"memberId": "M2", "follows": []})
    body = r.json()
    assert body["count"] == 0 and body["intention"] == "待定"
    assert any("未跟进" in x for x in body["reminders"])


# ================================================================ 安全配置
def test_docs_disabled_by_default():
    """条目 7：生产默认关闭 /docs 与 /openapi.json。"""
    assert main.DISABLE_DOCS is True
    assert client.get("/docs").status_code == 404
    assert client.get("/openapi.json").status_code == 404


def test_cors_not_wildcard():
    assert "*" not in main.ALLOWED_ORIGINS
    assert "http://localhost:5200" in main.ALLOWED_ORIGINS


def test_bind_host_is_loopback():
    assert main.BIND_HOST == "127.0.0.1"


# ================================================================ 响应契约
def test_response_contract_field_names_unchanged():
    """条目 17：收拢为 Pydantic 响应模型后，对外字段名必须与旧契约逐字一致。"""
    cases = [
        ("/health", {}, {"status", "service"}),
        ("/parse", {"text": "28岁"}, {"fields", "entity_tags", "field_count", "warnings"}),
        ("/match", {"target": {"gender": "女"}, "candidates": []},
         {"total", "results", "rejected_total", "rejected"}),
        ("/audit-suggest", {"type": "实名认证"},
         {"decision", "label", "confidence", "reasons", "risks", "suggestion", "priority"}),
        ("/reply-suggest", {"messages": []},
         {"intent", "sentiment", "tone", "suggestions", "context_size"}),
        ("/assign-suggest", {"member": {}, "agents": [
            {"id": "A1", "name": "小雨", "level": "金牌红娘", "status": "在职"}]},
         {"agentId", "agent", "score", "reason", "reasons", "candidates"}),
        ("/next-action", {"stage": "新线索"},
         {"action", "reason", "priority", "slaDays", "stayDays", "warnings"}),
        ("/followup-summary", {"follows": []},
         {"summary", "count", "latestType", "latestOutcome", "intention",
          "nextFollowUp", "daysSinceLast", "suggestion", "reminders"}),
        ("/coach/profile", {"name": "张伟"},
         {"score", "level", "verdict", "completedWeight", "totalWeight", "strengths",
          "missing", "quickWins", "projectedScore", "risks", "script"}),
        ("/moderate", {"text": "你好"},
         {"action", "riskLevel", "riskScore", "hits", "pii", "sanitized", "advice", "scene"}),
        ("/feedback", {"items": []},
         {"weights", "total", "bounds", "accepted", "appliedDimensions", "mode"}),
    ]
    for path, payload, keys in cases:
        r = client.get(path) if path == "/health" else client.post(path, json=payload)
        assert r.status_code == 200, path
        assert set(r.json().keys()) == keys, path

    r = client.get("/feedback/stats")
    assert set(r.json().keys()) == {"weights", "total", "bounds"}


def test_assign_candidate_contract():
    r = client.post("/assign-suggest", json={"member": {"city": "上海"}, "agents": [
        {"id": "A1", "name": "小雨", "level": "金牌红娘", "status": "在职",
         "memberCount": 10, "capacity": 80, "successRate": 70, "rating": 4.8, "region": "上海"}]})
    cand = r.json()["candidates"][0]
    assert set(cand.keys()) == {"agentId", "agent", "level", "score", "load", "reasons"}


def test_mask_phone_helper():
    assert mask_phone("13800138000") == "138****8000"
    assert mask_phone("") == "***"
