# -*- coding: utf-8 -*-
"""
鹊桥 · 红娘工作台 — Python AI 服务

基础能力：
  POST /parse           语音/粘贴文本 -> 会员档案实体抽取（姓名/年龄/性别/职业/学历/手机/城市/身高/收入/兴趣标签）
  POST /match           目标会员 + 候选列表 -> 智能匹配打分与亮点理由

增强能力（运营中心）：
  POST /audit-suggest   审核工单 -> 通过/驳回建议 + 风险点
  POST /reply-suggest   会话上下文 -> 3 条沟通话术建议
  POST /assign-suggest  会员 + 红娘列表 -> 最佳分配红娘与理由
  POST /next-action     CRM 线索 -> 下一步动作与优先级
  POST /followup-summary 会员跟进时间线 -> 意向判断 + 下一步建议

AI 增效（B 类）：
  POST /coach/profile   会员档案 -> 完整度评分 + 按收益排序的补全清单 + 沟通脚本
  POST /coach/next-step 牵线进展 -> 下一步约会建议 + 话题/穿搭话术库（约会教练）
  POST /moderate        待发送内容 -> 诈骗/色情/违法/引流/辱骂检测 + 联系方式脱敏
  POST /feedback        红娘采纳/否决 -> 匹配维度权重在线学习（支持历史回灌）
  GET  /feedback/stats  当前维度权重与采纳率

运行：uvicorn main:app --host 127.0.0.1 --port 8090

安全约定：本服务是被 Java 后端调用的**内部服务**，不对公网暴露。
  - 仅绑定内网地址（默认 127.0.0.1，可用 QQAI_HOST 覆盖）
  - CORS 收敛为调用方白名单（QQAI_ALLOWED_ORIGINS，逗号分隔）
  - 生产默认关闭 /docs 与 /openapi.json（DISABLE_DOCS=0 可在本地开发打开）
  - /parse 返回的手机号已脱敏，日志中亦只记录截断值
"""
import logging
import math
import os
import re
from datetime import datetime
from logging.handlers import RotatingFileHandler
from typing import Any, Optional

from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware
from pydantic import BaseModel, ConfigDict, Field, field_validator, model_validator
from pydantic.alias_generators import to_camel

# ================================================================ 配置常量
# 说明：所有阈值/魔数集中在此，便于运维调参，避免散落在函数体内。

# --- 输入约束 ---
MAX_TEXT_LEN = 5000            # /parse 单次文本上限（超出截断，防 DoS）
MAX_TOP = 100                  # /match top 上限
MIN_TOP = 1

# --- 实体抽取阈值 ---
AGE_MIN, AGE_MAX = 18, 99      # 合法年龄区间，越界丢弃该特征
HEIGHT_MIN, HEIGHT_MAX = 120, 250
INCOME_MAX_WAN = 1000          # 年收入上限（万），超出视为误识别
NAME_MAX_LEN = 4

# --- 打分归一化 ---
SCORE_FULL = 100
SCORE_MIN = 0
GENDER_MISSING_FACTOR = 0.85   # 性别缺失时的显式降级系数（不再静默按满分算）
# 维度「未参与打分」（双方资料缺失）时，其基础分计入分母的折算系数。
# 取值含义：1.0 = 与旧行为一致（资料缺失全额压分，常导致真实候选分数被系统性
# 压到阈值以下、推荐永远为空）；0.0 = 完全忽略缺失维度（资料不全可与资料完整
# 同分，掩盖不确定性）。取 0.5 为折中：既保留资料不全的惩罚，又不至于把
# 「多数维度命中」的候选压到无法推荐。
MISSING_DIM_CREDIT = 0.5
PENALTY = {                    # 硬性条件违背的负分（归一化前，作用于原始得分）
    "age_out_of_range": 6.0,
    "height_out_of_range": 6.0,
    "income_below_requirement": 4.0,
}
AGE_NEAR_TOLERANCE = 8         # 落在需求区间外但接近中值的容忍岁数
AGE_SINGLE_BOUND_TOLERANCE = 3  # 单边界需求的容忍岁数
AGE_SIMILAR_STRICT = 3         # 无需求时「年龄相仿」判定
AGE_SIMILAR_LOOSE = 6
AGE_SIMILAR_FAR = 10
HOBBY_FULL_SHARED = 3.0        # 兴趣交集达到该数量即满分
INCOME_OK_RATIO = 0.8          # 收入比值达标线（双向度量：min/max）
INCOME_HALF_RATIO = 0.6
# 收入差距较大时的基础分。婚恋中一方收入明显高于另一方属常见组合，
# 视为「非契合项」而非「否决项」，不应判 0 分。
INCOME_GAP_SCORE = 0.25
# 兴趣标签稀疏（每人仅 2-3 个）时交集为空是常态，兴趣互补同样成立，
# 给基础分而非 0，避免该项系统性拖低总分。
HOBBY_NO_OVERLAP_SCORE = 0.25
MAX_HIGHLIGHTS = 3

# --- 运行时/安全 ---
DEFAULT_ORIGINS = (
    "http://localhost:5200,http://127.0.0.1:5200,"
    "http://localhost:8080,http://127.0.0.1:8080"
)
ALLOWED_ORIGINS = [
    o.strip() for o in os.getenv("QQAI_ALLOWED_ORIGINS", DEFAULT_ORIGINS).split(",") if o.strip()
]
# 默认生产态：关闭接口文档；本地开发可 DISABLE_DOCS=0 打开
DISABLE_DOCS = os.getenv("DISABLE_DOCS", "1").lower() not in ("0", "false", "no")
BIND_HOST = os.getenv("QQAI_HOST", "127.0.0.1")
BIND_PORT = int(os.getenv("QQAI_PORT", "8090"))
LOG_FILE = os.getenv("QQAI_LOG_FILE", os.path.join(os.path.dirname(os.path.abspath(__file__)), "ai.log"))
LOG_MAX_BYTES = 10 * 1024 * 1024
LOG_BACKUP_COUNT = 5


# ================================================================ 日志
def _init_logger() -> logging.Logger:
    lg = logging.getLogger("queqiao.ai")
    if lg.handlers:
        return lg
    lg.setLevel(os.getenv("QQAI_LOG_LEVEL", "INFO").upper())
    fmt = logging.Formatter(
        "%(asctime)s | %(levelname)-5s | %(name)s | %(message)s",
        datefmt="%Y-%m-%d %H:%M:%S",
    )
    try:
        fh = RotatingFileHandler(
            LOG_FILE, maxBytes=LOG_MAX_BYTES, backupCount=LOG_BACKUP_COUNT, encoding="utf-8"
        )
        fh.setFormatter(fmt)
        lg.addHandler(fh)
    except OSError:  # 只读盘/权限不足时退化为仅控制台，不影响服务启动
        pass
    sh = logging.StreamHandler()
    sh.setFormatter(fmt)
    lg.addHandler(sh)
    lg.propagate = False
    return lg


logger = _init_logger()


def mask_phone(phone: str) -> str:
    """手机号脱敏：13800138000 -> 138****8000"""
    if not phone or len(phone) < 7:
        return "***"
    return phone[:3] + "****" + phone[-4:]


class CamelModel(BaseModel):
    """Java 后端发送 camelCase，此处双向兼容（snake_case 亦可），未知字段直接忽略。"""

    model_config = ConfigDict(
        alias_generator=to_camel,
        populate_by_name=True,
        extra="ignore",
    )


class CamelOut(BaseModel):
    """出参基类：字段以 snake_case 声明、以 camelCase 序列化，
    与既有前端契约逐字段对齐（不改对外字段名，仅收拢为 Pydantic 模型）。"""

    model_config = ConfigDict(alias_generator=to_camel, populate_by_name=True)


app = FastAPI(
    title="QueQiao AI Service",
    version="0.3.0",
    docs_url=None if DISABLE_DOCS else "/docs",
    redoc_url=None if DISABLE_DOCS else "/redoc",
    openapi_url=None if DISABLE_DOCS else "/openapi.json",
)
app.add_middleware(
    CORSMiddleware,
    allow_origins=ALLOWED_ORIGINS,
    allow_credentials=True,
    allow_methods=["GET", "POST", "OPTIONS"],
    allow_headers=["Content-Type", "Authorization", "X-Request-Id"],
)
logger.info(
    "service boot | docs_disabled=%s | allowed_origins=%s", DISABLE_DOCS, ALLOWED_ORIGINS
)

# ---------------------------------------------------------------- 词表
EDU_LEVELS = ["博士", "硕士", "本科", "大专", "高中"]
CITIES = [
    "上海", "北京", "广州", "深圳", "杭州", "南京", "苏州", "成都",
    "重庆", "武汉", "西安", "天津", "青岛", "长沙", "郑州", "合肥",
]
JOBS = [
    "工程师", "医生", "教师", "分析师", "设计师", "总监", "高管", "律师",
    "会计师", "公务员", "产品经理", "程序员", "建筑师", "咨询师", "创业者",
    "金融分析师", "大学教师", "互联网总监", "护士", "编辑", "运营",
]
HOBBIES = [
    "摄影", "徒步", "旅行", "瑜伽", "咖啡", "阅读", "电影", "烹饪",
    "健身", "文艺", "音乐", "绘画", "跑步", "游泳", "宠物", "美食",
]

SURNAME = (
    "王李张刘陈杨赵黄周吴徐孙马朱胡郭何罗高林郑梁谢宋唐许韩冯邓曹彭"
    "曾肖田董潘袁蔡蒋余于杜叶程苏魏吕丁任沈姚卢姜崔钟谭陆汪范金石"
)

# 称谓/尊称白名单：句首「姓氏+X」兜底时排除，避免把「王哥」「陈姐」当姓名
TITLE_WORDS = (
    "哥", "姐", "叔", "姨", "婶", "嫂", "爷", "奶", "伯", "妈", "爸", "弟", "妹",
    "总", "工", "老师", "先生", "女士", "小姐", "同志", "师傅", "医生", "律师",
    "经理", "主任", "院长", "校长", "队长", "老板",
)

# 职业按词长降序匹配，保证「互联网总监」优先于「总监」命中（最长匹配）
JOBS_BY_LEN = sorted(JOBS, key=len, reverse=True)

# 手机号号段校验（工信部在用号段）
PHONE_SEGMENT_RE = re.compile(r"^1(?:3\d|4[5-9]|5[0-35-9]|6[2567]|7[0-8]|8\d|9[0-35-9])\d{8}$")
# 词边界：左右都不能再是数字，避免从身份证/订单号/银行卡中截出「手机号」
PHONE_PLAIN_RE = re.compile(r"(?<!\d)(1[3-9]\d{9})(?!\d)")
# 上下文关联：显式出现「手机/电话/联系方式」等提示词时优先采信
PHONE_CTX_RE = re.compile(
    r"(?:手机号?|电话|联系方式|联系电话|号码|微信号?|tel|mobile)\s*(?:是|为)?\s*[:：]?\s*(?<!\d)(1[3-9]\d{9})(?!\d)",
    re.I,
)
# 明确的非手机号上下文（身份证/订单/银行卡/QQ），命中后其内数字不做手机号解析
NON_PHONE_CTX_RE = re.compile(
    r"(?:身份证号?|证件号|订单号?|流水号|卡号|银行卡|工号|学号|会员号|编号|qq号?|扣扣号?)"
    r"\s*(?:是|为)?\s*[:：]?\s*([A-Za-z0-9Xx\-]{6,32})",
    re.I,
)

# 性别标记：strong 为自述性强标记，weak 为易受上下文干扰的弱标记
GENDER_STRONG = {
    "男": ("男生", "男士", "男性", "帅哥", "先生", "男方", "男孩"),
    "女": ("女生", "女士", "女性", "姑娘", "小姐", "女方", "女孩"),
}
GENDER_WEAK = {"男": ("他", "男"), "女": ("她", "女")}
GENDER_EXPLICIT_RE = re.compile(r"性别\s*[:：是]?\s*([男女])")
# 择偶意向子句：描述理想对象（如「找28岁以下温柔女生」「想找有房男士」），
# 其中的性别词不能作为「本人性别」的证据——不剥离会把「男，找女生」误判为女。
GENDER_INTENT_RE = re.compile(
    r"[，,。；;！!\s]*"
    r"(?:想找|要找|希望找|想寻找|寻找|寻求|期望|期待|意向是?|择偶|对象是?|喜欢|偏好|要求是?|找)"
    r"[^，,。；;！!？?]{0,40}"
)

# 年龄：不再固定截前两位，配合 AGE_MIN/AGE_MAX 区间校验剔除「300岁」「1000岁」
AGE_RE = re.compile(r"(?<!\d)(\d{1,4})\s*岁")
HEIGHT_RE = re.compile(r"(?<!\d)(\d{2,3})\s*(?:cm|CM|厘米|公分)")
# 收入必须带「年收入/年薪/年入」上下文，避免把「月薪2万」「房子1500万」当年收入
INCOME_RANGE_RE = re.compile(r"(?:年收入|年薪|年入)\s*(?:大概|大约|约)?\s*[:：]?\s*(\d{1,4})\s*[-~到至]\s*(\d{1,4})\s*万")
INCOME_SINGLE_RE = re.compile(r"(?:年收入|年薪|年入)\s*(?:大概|大约|约)?\s*[:：]?\s*(\d{1,4})\s*万")


# ---------------------------------------------------------------- 模型
class ParseRequest(CamelModel):
    text: str = Field(default="", max_length=MAX_TEXT_LEN)

    @field_validator("text", mode="before")
    @classmethod
    def _truncate(cls, v: Any) -> Any:
        """超长输入截断而非 422，兼顾防 DoS 与调用方体验。"""
        if isinstance(v, str) and len(v) > MAX_TEXT_LEN:
            logger.warning("parse text truncated: %d -> %d", len(v), MAX_TEXT_LEN)
            return v[:MAX_TEXT_LEN]
        return v


class ParseResponse(BaseModel):
    """响应保持 snake_case，与既有前端/Java 契约一致（warnings 为新增可选字段）。"""

    fields: dict[str, Any]
    entity_tags: list[dict[str, str]]
    field_count: int
    warnings: list[str] = []


class Member(CamelModel):
    id: Optional[str] = None
    name: str = ""
    gender: str = ""
    age: Optional[int] = None
    job: str = ""
    education: str = ""
    city: str = ""
    height: Optional[int] = None
    income: str = ""
    tags: list[str] = []
    requirements: Optional[dict[str, Any]] = None
    requirement: str = ""           # 自由文本择偶要求（Java toMap 以 `requirement` 下发）

    @field_validator("tags", mode="before")
    @classmethod
    def _coerce_tags(cls, v):
        # Java toMap 以逗号/顿号分隔的字符串下发 tags；这里归一化为 list，兼容两种形态
        if v is None:
            return []
        if isinstance(v, str):
            return [t.strip() for t in re.split(r"[,，、]", v) if t.strip()]
        return v

    @model_validator(mode="before")
    @classmethod
    def _none_to_empty(cls, data):
        # Java toMap 会把空字段以 null 下发；pydantic v2 对 str 字段不接受 None，
        # 这里把已知字符串字段的 None 归一化为 ""，避免 422 导致 AI 匹配整体降级。
        if isinstance(data, dict):
            for k in ("name", "gender", "job", "education", "city", "income", "requirement"):
                if data.get(k) is None:
                    data[k] = ""
        return data


class MatchRequest(CamelModel):
    target: Member
    candidates: list[Member] = []
    top: int = Field(default=10, ge=MIN_TOP, le=MAX_TOP)


class MatchResultItem(BaseModel):
    """命中候选。保留 member/score/highlights 原字段名，新增字段为增量扩展。"""

    member: dict[str, Any]
    score: int
    highlights: list[str]
    rejected: bool = False
    reason: Optional[str] = None
    missing_features: list[str] = []
    degraded: bool = False
    semantic_score: int = 0
    semantic_keywords: list[str] = []


class MatchRejectedItem(BaseModel):
    member: dict[str, Any]
    score: int = 0
    rejected: bool = True
    reason: str
    reason_label: str


class MatchResponse(BaseModel):
    """total/results 与旧契约一致；rejected 为新增，用于区分「不匹配」与「未参与打分」。"""

    total: int
    results: list[MatchResultItem]
    rejected_total: int = 0
    rejected: list[MatchRejectedItem] = []


# ---------------------------------------------------------------- 实体抽取
def _is_title(word: str) -> bool:
    """判断「王哥」「陈姐」这类称谓，不应作为姓名。"""
    tail = word[1:]
    return any(tail == t or tail.startswith(t) for t in TITLE_WORDS)


def extract_name(text: str) -> Optional[str]:
    m = re.search(rf"(?:姓名|名字)\s*[:：是]?\s*([一-龥]{{2,{NAME_MAX_LEN}}})", text)
    if m and not _is_title(m.group(1)):
        return m.group(1)
    m = re.search(rf"(?:叫|称呼)\s*([一-龥]{{2,{NAME_MAX_LEN}}})", text)
    if m and not _is_title(m.group(1)):
        return m.group(1)
    # 句首「姓氏+名」兜底（排除称谓词）
    m = re.search(rf"^([{SURNAME}][一-龥]{{1,2}})[，,。、\s]", text.strip())
    if m and not _is_title(m.group(1)):
        return m.group(1)
    return None


def _mask_non_phone_zones(text: str) -> str:
    """把身份证/订单号/卡号等上下文中的数字替换掉，避免被误判为手机号。"""

    def _blank(m: re.Match) -> str:
        return m.group(0).replace(m.group(1), "#" * len(m.group(1)))

    return NON_PHONE_CTX_RE.sub(_blank, text)


def extract_phone(text: str) -> Optional[str]:
    """提取手机号：词边界 + 号段校验 + 上下文关联。返回原始号码（调用方负责脱敏）。"""
    cleaned = _mask_non_phone_zones(text)
    m = PHONE_CTX_RE.search(cleaned)
    if m and PHONE_SEGMENT_RE.match(m.group(1)):
        return m.group(1)
    for cand in PHONE_PLAIN_RE.findall(cleaned):
        if PHONE_SEGMENT_RE.match(cand):
            return cand
    return None


def detect_gender(text: str, warnings: Optional[list[str]] = None) -> Optional[str]:
    """性别判定：先剥离择偶意向子句，再按 显式字段 > 强标记（首个出现） > 弱标记（首个出现）。

    「找28岁以下温柔女生」等意向子句描述的是理想对象，其中的性别词
    不能作为本人性别证据。强标记出现相反性别时视为冲突，置空并告警，
    避免「男生想找女生」被后者覆盖。
    """
    warnings = warnings if warnings is not None else []
    text = GENDER_INTENT_RE.sub("，", text)

    m = GENDER_EXPLICIT_RE.search(text)
    if m:
        return m.group(1)

    def first_index(words: tuple[str, ...]) -> Optional[int]:
        idxs = [text.find(w) for w in words if text.find(w) >= 0]
        return min(idxs) if idxs else None

    s_male, s_female = first_index(GENDER_STRONG["男"]), first_index(GENDER_STRONG["女"])
    if s_male is not None and s_female is not None:
        warnings.append("性别标记冲突（同时出现男/女强标记），已置空待人工确认")
        logger.warning("gender conflict (strong markers) at %d/%d", s_male, s_female)
        return None
    if s_male is not None:
        return "男"
    if s_female is not None:
        return "女"

    w_male, w_female = first_index(GENDER_WEAK["男"]), first_index(GENDER_WEAK["女"])
    if w_male is not None and w_female is not None:
        winner = "男" if w_male < w_female else "女"
        warnings.append("文本中同时出现他/她（或男/女），按首个出现的主位人称判定为「%s」" % winner)
        logger.warning("gender weak-marker conflict -> %s (%d vs %d)", winner, w_male, w_female)
        return winner
    if w_male is not None:
        return "男"
    if w_female is not None:
        return "女"
    return None


def parse_entities(text: str, warnings: Optional[list[str]] = None) -> dict[str, Any]:
    """从自由文本抽取会员档案字段。phone 字段返回**脱敏值**。"""
    warnings = warnings if warnings is not None else []
    text = (text or "")[:MAX_TEXT_LEN]
    fields: dict[str, Any] = {}

    name = extract_name(text)
    if name:
        fields["name"] = name

    m = AGE_RE.search(text)
    if m:
        age = int(m.group(1))
        if AGE_MIN <= age <= AGE_MAX:
            fields["age"] = age
        else:
            warnings.append("年龄「%d岁」超出合法范围（%d~%d），已丢弃" % (age, AGE_MIN, AGE_MAX))
            logger.warning("age out of range, dropped: %d", age)

    gender = detect_gender(text, warnings)
    if gender:
        fields["gender"] = gender

    phone = extract_phone(text)
    if phone:
        fields["phone"] = mask_phone(phone)          # PII：仅回传脱敏值
        logger.info("phone extracted (masked): %s", mask_phone(phone))

    for edu in EDU_LEVELS:
        if edu in text:
            fields["education"] = edu
            break
    if "海归" in text and "education" not in fields:
        fields["education"] = "硕士"

    for job in JOBS_BY_LEN:                          # 最长匹配，避免「互联网总监」被「总监」抢先
        if job in text:
            fields["job"] = job
            break

    for city in CITIES:
        if city in text:
            fields["city"] = city
            break

    m = HEIGHT_RE.search(text)
    if m:
        height = int(m.group(1))
        if HEIGHT_MIN <= height <= HEIGHT_MAX:
            fields["height"] = height
        else:
            warnings.append("身高「%dcm」超出合法范围，已丢弃" % height)

    m = INCOME_RANGE_RE.search(text)
    if m:
        lo, hi = int(m.group(1)), int(m.group(2))
        if 0 < lo <= hi <= INCOME_MAX_WAN:
            fields["income"] = f"{lo}-{hi}万"
        else:
            warnings.append("年收入区间「%d-%d万」不合理，已丢弃" % (lo, hi))
    else:
        m = INCOME_SINGLE_RE.search(text)
        if m:
            val = int(m.group(1))
            if 0 < val <= INCOME_MAX_WAN:
                fields["income"] = f"{val}万+"
            else:
                warnings.append("年收入「%d万」不合理，已丢弃" % val)

    tags = [h for h in HOBBIES if h in text]
    if tags:
        fields["tags"] = tags

    logger.info(
        "parse done | len=%d | fields=%s | warnings=%d",
        len(text), sorted(fields.keys()), len(warnings),
    )
    return fields


def to_entity_tags(fields: dict[str, Any]) -> list[dict[str, str]]:
    label_map = {
        "name": "姓名", "age": "年龄", "gender": "性别", "phone": "手机号",
        "education": "学历", "job": "职业", "city": "城市", "height": "身高",
        "income": "年收入",
    }
    tags = []
    for k, v in fields.items():
        if k == "tags":
            for t in v:
                tags.append({"label": "兴趣", "value": t})
        else:
            unit = {"age": "岁", "height": "cm"}.get(k, "")
            tags.append({"label": label_map.get(k, k), "value": f"{v}{unit}"})
    return tags


# ---------------------------------------------------------------- 匹配打分
EDU_RANK = {e: i for i, e in enumerate(reversed(EDU_LEVELS))}

# 各匹配维度的基础分值。反馈学习只调整维度「权重」，不改动基础分值，
# 便于随时回滚到出厂配置（权重全部置 1.0）。
DIM_BASE = {"age": 18, "education": 12, "city": 12, "hobby": 12, "height": 6, "income": 10}

# 显式拒绝原因（前端可据此区分「不匹配」与「未参与打分」）
REJECT_LABELS = {
    "same_gender": "同性别，不参与异性匹配打分",
}
MISSING_LABELS = {
    "age": "年龄信息缺失",
    "education": "学历信息缺失",
    "city": "城市信息缺失",
    "hobby": "兴趣标签缺失或无交集",
    "height": "身高信息或身高要求缺失",
    "income": "收入信息或收入要求缺失",
    "gender": "性别信息缺失",
}


def _income_to_wan(value: Any) -> Optional[float]:
    """把「30万」「30-50万」「30」等收入表述折算成数值（万），取区间中值。"""
    if value is None:
        return None
    if isinstance(value, (int, float)):
        return float(value) if value > 0 else None
    s = str(value)
    nums = [float(x) for x in re.findall(r"\d+(?:\.\d+)?", s)]
    if not nums:
        return None
    is_range = len(nums) >= 2 and any(sep in s for sep in ("-", "~", "到", "至"))
    val = (nums[0] + nums[1]) / 2 if is_range else nums[0]
    return val if 0 < val <= INCOME_MAX_WAN else None


def _score_age(target: Member, cand: Member, req: dict) -> tuple[float, Optional[str], float]:
    """返回 (命中强度 0~1, 亮点, 惩罚分)。区间单边界也参与打分。"""
    lo, hi = req.get("ageMin"), req.get("ageMax")
    if not cand.age:
        return 0.0, None, 0.0
    if lo and hi:
        if lo <= cand.age <= hi:
            return 1.0, "年龄区间契合", 0.0
        if abs(cand.age - (lo + hi) / 2) <= AGE_NEAR_TOLERANCE:
            return 0.45, None, 0.0
        return 0.0, None, PENALTY["age_out_of_range"]
    if lo and not hi:                                  # 只给下界
        if cand.age >= lo:
            return 1.0, "年龄满足下限要求", 0.0
        if cand.age >= lo - AGE_SINGLE_BOUND_TOLERANCE:
            return 0.5, None, 0.0
        return 0.0, None, PENALTY["age_out_of_range"]
    if hi and not lo:                                  # 只给上界
        if cand.age <= hi:
            return 1.0, "年龄满足上限要求", 0.0
        if cand.age <= hi + AGE_SINGLE_BOUND_TOLERANCE:
            return 0.5, None, 0.0
        return 0.0, None, PENALTY["age_out_of_range"]
    if target.age:                                     # 无需求：与目标年龄比对
        diff = abs(cand.age - target.age)
        if diff <= AGE_SIMILAR_STRICT:
            return 1.0, "年龄相仿", 0.0
        if diff <= AGE_SIMILAR_LOOSE:
            return 0.67, "年龄相仿", 0.0
        if diff <= AGE_SIMILAR_FAR:
            return 0.3, None, 0.0
        return 0.0, None, 0.0
    # 目标会员也无年龄、且无需求区间：该维度未参与打分，必须返回三元组，
    # 否则调用方解包会得到 None 并抛 TypeError（此前为裸 return）。
    return 0.0, None, 0.0


def _score_height(cand: Member, req: dict) -> tuple[float, Optional[str], float]:
    lo, hi = req.get("heightMin"), req.get("heightMax")
    if not cand.height or not (lo or hi):
        return 0.0, None, 0.0
    if lo and cand.height < lo:
        return 0.0, None, PENALTY["height_out_of_range"]
    if hi and cand.height > hi:
        return 0.0, None, PENALTY["height_out_of_range"]
    return 1.0, "身高符合要求", 0.0


def _score_income(target: Member, cand: Member, req: dict) -> tuple[float, Optional[str], float]:
    cand_income = _income_to_wan(cand.income)
    if cand_income is None:
        return 0.0, None, 0.0
    need = _income_to_wan(req.get("incomeMin"))
    if need is not None:
        if cand_income >= need:
            return 1.0, "年收入满足要求", 0.0
        if cand_income >= need * INCOME_OK_RATIO:
            return 0.5, None, 0.0
        return 0.0, None, PENALTY["income_below_requirement"]
    target_income = _income_to_wan(target.income)
    if target_income is None:
        return 0.0, None, 0.0
    # 无明确需求时按「双向收入比值」度量：min/max。
    # 旧实现单向要求 候选收入 >= 目标收入×系数，导致高收入方（常见于男方）
    # 几乎匹配不到任何人——那不是真实的不匹配，而是度量方向造成的系统性偏差。
    # 改为对称度量后，「A 看 B」与「B 看 A」结论一致。
    lo, hi = sorted((cand_income, target_income))
    ratio = (lo / hi) if hi else 0.0
    if ratio >= INCOME_OK_RATIO:
        return 1.0, "收入水平相当", 0.0
    if ratio >= INCOME_HALF_RATIO:
        return 0.5, None, 0.0
    return INCOME_GAP_SCORE, None, 0.0


# ---------------------------------------------------------------- 语义契合
# 离线中文文本语义相似度：字 bigram + 拉丁词 的 TF 余弦（零外部依赖）。
# 两两比较时 IDF 退化为常数，故采用 TF 加权余弦（等同 IDF≡1 的 TF-IDF），
# 保证 AI 服务不可用时 Java 侧用同源算法仍能产出「语义契合」维度。
SEM_STOP2 = {
    "的的", "了了", "是是", "我我", "你你", "他他", "她她", "们们", "在在", "和和",
    "也也", "都都", "就就", "不不", "人人", "一一", "有有", "个个", "这这", "那那",
    "着着", "说说", "到到", "会会", "要要", "去去", "上上", "下下", "里里", "后后",
    "前前", "能能", "对对", "很很", "给给", "等等", "与与", "及及", "或或", "让让",
    "把把", "被被", "从从", "向向", "为为", "以以", "之之", "而而", "于于", "其其",
    "此此", "该该", "自己", "我们", "你们", "他们", "她们", "一个", "可以", "这个",
    "那个", "没有", "因为", "所以", "但是", "如果", "希望", "喜欢", "觉得", "比较",
    "非常", "一样", "一些", "什么", "怎么", "这样", "那种", "的话",
}


def _sem_text(m: "Member") -> str:
    """拼接用于语义比对的文本：自由文本择偶要求 + 标签 + 职业/城市/学历。"""
    parts: list[str] = []
    req = getattr(m, "requirement", "") or ""
    if req:
        parts.append(req)
    tags = getattr(m, "tags", None)
    if tags:
        parts.append(" ".join(tags) if isinstance(tags, (list, tuple)) else str(tags))
    for f in ("job", "city", "education"):
        v = getattr(m, f, "") or ""
        if v:
            parts.append(v)
    return " ".join(parts)


def _sem_tokens(text: str) -> dict[str, int]:
    """生成词频向量：拉丁词（英文职业/数字）+ 连续汉字的 2-gram，过滤常见停用 2-gram。"""
    if not text:
        return {}
    tf: dict[str, int] = {}
    for w in re.findall(r"[a-zA-Z][a-zA-Z0-9+#]*", text.lower()):
        tf["w:" + w] = tf.get("w:" + w, 0) + 1
    for run in re.findall(r"[一-鿿]+", text):
        for i in range(len(run) - 1):
            bg = run[i:i + 2]
            if bg in SEM_STOP2:
                continue
            tf["b:" + bg] = tf.get("b:" + bg, 0) + 1
    return tf


def _cosine_tf(a: dict[str, int], b: dict[str, int]) -> float:
    if not a or not b:
        return 0.0
    if len(a) > len(b):
        a, b = b, a
    dot = 0.0
    na = 0.0
    for t, c in a.items():
        na += c * c
        if t in b:
            dot += c * b[t]
    nb = 0.0
    for v in b.values():
        nb += v * v
    if na == 0.0 or nb == 0.0:
        return 0.0
    return dot / (math.sqrt(na) * math.sqrt(nb))


def _semantic(target: "Member", cand: "Member") -> tuple[int, list[str]]:
    """返回 (语义契合度 0~100, 共同关注关键词)。"""
    ta = _sem_tokens(_sem_text(target))
    tb = _sem_tokens(_sem_text(cand))
    cos = _cosine_tf(ta, tb)
    score = int(round(cos * 100))
    shared = [(min(c, tb[t]), t) for t, c in ta.items() if t in tb]
    shared.sort(reverse=True)
    kws: list[str] = []
    for _, t in shared[:6]:
        kws.append(t[2:] if t.startswith("b:") else t[1:])
    return score, kws


def score_pair(target: Member, cand: Member) -> dict[str, Any]:
    """归一化匹配打分。

    返回结构：
      {score: 0~100, rejected: bool, reason: str|None, highlights: [...],
       missing_features: [...], degraded: bool}
    同性别显式 rejected（不再静默丢弃）；性别缺失显式降级并记录失分项。
    """
    # 性别互补是前提：同性别显式拒绝，前端可区分「不匹配」与「未参与打分」
    if target.gender and cand.gender and target.gender == cand.gender:
        logger.info("match reject | cand=%s | reason=same_gender", cand.id or cand.name)
        return {
            "score": 0, "rejected": True, "reason": "same_gender",
            "highlights": [], "missing_features": ["gender"], "degraded": False,
        }

    req = target.requirements or {}
    missing: list[str] = []
    highlights: list[str] = []
    earned = 0.0
    possible = 0.0
    penalty = 0.0

    # 学历 / 同城 / 兴趣：简单维度
    if target.education and cand.education:
        gap = EDU_RANK.get(cand.education, 0) - EDU_RANK.get(target.education, 0)
        ratio_edu = 1.0 if gap >= 0 else (0.5 if gap == -1 else 0.0)
        hl_edu = "学历匹配" if gap >= 0 else None
    else:
        ratio_edu, hl_edu = 0.0, None
        missing.append("education")

    if target.city and cand.city:
        ratio_city = 1.0 if target.city == cand.city else 0.0
        hl_city = "同城生活" if ratio_city else None
    else:
        ratio_city, hl_city = 0.0, None
        missing.append("city")

    shared = sorted(set(target.tags) & set(cand.tags))
    if target.tags and cand.tags:
        if shared:
            ratio_hobby = min(1.0, len(shared) / HOBBY_FULL_SHARED)
            hl_hobby = "共同兴趣：" + "、".join(shared[:2])
        else:
            # 标签稀疏时交集为空是常态，兴趣互补同样成立：给基础分，不判 0
            ratio_hobby = HOBBY_NO_OVERLAP_SCORE
            hl_hobby = None
            missing.append("hobby")
    else:
        ratio_hobby, hl_hobby = 0.0, None
        missing.append("hobby")

    ratio_age, hl_age, pen_age = _score_age(target, cand, req)
    ratio_height, hl_height, pen_height = _score_height(cand, req)
    ratio_income, hl_income, pen_income = _score_income(target, cand, req)
    penalty += pen_age + pen_height + pen_income

    if not cand.age:
        missing.append("age")
    if not cand.height or not (req.get("heightMin") or req.get("heightMax")):
        missing.append("height")
    if _income_to_wan(cand.income) is None or (
        _income_to_wan(req.get("incomeMin")) is None and _income_to_wan(target.income) is None
    ):
        missing.append("income")

    # 语义契合：离线中文文本相似度（字 bigram TF 余弦，零外部依赖）
    sem_score, sem_kw = _semantic(target, cand)
    if sem_score >= 55 and sem_kw:
        highlights.append("资料语义契合（共同关注：" + "、".join(sem_kw[:3]) + "）")

    # 判断每个维度是否「真正参与打分」——即双方均有可比对的数据。
    # 判定条件必须与各 _score_* 子函数的提前返回条件严格一致，否则会出现
    # 「分母算作已评估、分子却是 0」的隐性压分。
    evaluated: dict[str, bool] = {
        # 候选有年龄，且（目标有年龄 或 存在任一年龄需求边界）
        "age": bool(cand.age)
        and (bool(target.age) or bool(req.get("ageMin")) or bool(req.get("ageMax"))),
        "education": bool(target.education and cand.education),
        "city": bool(target.city and cand.city),
        "hobby": bool(target.tags and cand.tags),
        # 候选有身高，且存在任一身高需求边界
        "height": bool(cand.height and (req.get("heightMin") or req.get("heightMax"))),
        # 候选收入可解析，且（存在收入需求 或 目标收入可解析）
        "income": _income_to_wan(cand.income) is not None
        and (
            _income_to_wan(req.get("incomeMin")) is not None
            or _income_to_wan(target.income) is not None
        ),
    }

    for dim, ratio, hl in (
        ("age", ratio_age, hl_age),
        ("education", ratio_edu, hl_edu),
        ("city", ratio_city, hl_city),
        ("hobby", ratio_hobby, hl_hobby),
        ("height", ratio_height, hl_height),
        ("income", ratio_income, hl_income),
    ):
        full = DIM_BASE[dim] * dim_weight(dim)
        if evaluated[dim]:
            possible += full
            earned += full * ratio
        else:
            # 未参与打分：分子不得分，分母按折算系数计入，避免系统性压分
            possible += full * MISSING_DIM_CREDIT
        if hl:
            highlights.append(hl)

    logger.debug(
        "match dims | cand=%s | evaluated=%s | possible=%.1f | earned=%.1f",
        cand.id or cand.name,
        {d: v for d, v in evaluated.items() if v},
        possible,
        earned,
    )

    degraded = False
    if not target.gender or not cand.gender:           # 性别缺失：显式降级 + 记录，不再静默
        degraded = True
        if "gender" not in missing:
            missing.append("gender")
        earned *= GENDER_MISSING_FACTOR
        logger.info("match degraded | cand=%s | reason=gender_missing", cand.id or cand.name)

    raw = max(0.0, earned - penalty)
    score = int(round(raw * SCORE_FULL / possible)) if possible > 0 else SCORE_MIN
    score = max(SCORE_MIN, min(SCORE_FULL, score))

    if not highlights:
        highlights.append("暂无明显契合项，建议人工复核")

    logger.info(
        "match score | cand=%s | score=%d | penalty=%.1f | missing=%s",
        cand.id or cand.name, score, penalty, missing,
    )
    return {
        "score": score,
        "rejected": False,
        "reason": "gender_missing" if degraded else None,
        "highlights": highlights[:MAX_HIGHLIGHTS],
        "missing_features": missing,
        "degraded": degraded,
        "semantic_score": sem_score,
        "semantic_keywords": sem_kw,
    }


# ---------------------------------------------------------------- 路由
class HealthResponse(BaseModel):
    status: str
    service: str


@app.get("/health", response_model=HealthResponse)
def health() -> dict[str, str]:
    return {"status": "ok", "service": "queqiao-ai"}


@app.post("/parse", response_model=ParseResponse)
def parse(req: ParseRequest) -> ParseResponse:
    warnings: list[str] = []
    fields = parse_entities(req.text, warnings)
    return ParseResponse(
        fields=fields,
        entity_tags=to_entity_tags(fields),
        field_count=len(fields),
        warnings=warnings,
    )


@app.post("/match", response_model=MatchResponse)
def match(req: MatchRequest) -> MatchResponse:
    scored: list[MatchResultItem] = []
    rejected: list[MatchRejectedItem] = []
    for cand in req.candidates:
        r = score_pair(req.target, cand)
        member = cand.model_dump()
        if r["rejected"]:
            rejected.append(MatchRejectedItem(
                member=member,
                reason=r["reason"],
                reason_label=REJECT_LABELS.get(r["reason"], r["reason"]),
            ))
            continue
        scored.append(MatchResultItem(
            member=member,
            score=r["score"],
            highlights=r["highlights"],
            reason=r["reason"],
            missing_features=r["missing_features"],
            degraded=r["degraded"],
            semantic_score=r["semantic_score"],
            semantic_keywords=r["semantic_keywords"],
        ))
    scored.sort(key=lambda x: x.score, reverse=True)
    logger.info(
        "match done | target=%s | candidates=%d | matched=%d | rejected=%d | top=%d",
        req.target.id or req.target.name, len(req.candidates), len(scored), len(rejected), req.top,
    )
    return MatchResponse(
        total=len(scored),
        results=scored[: req.top],
        rejected_total=len(rejected),
        rejected=rejected,
    )


# ================================================================ 增强能力
# 说明：以下四个接口采用「规则引擎 + 加权评分」实现，不依赖外部大模型，
# 保证离线可用、响应稳定（<50ms），且结论可解释（每条建议都附理由）。
# 后续接入 LLM 时只需替换各 suggest_* 函数内部实现，出入参契约保持不变。


# ---------------------------------------------------------------- 审核建议
class AuditRequest(CamelModel):
    type: str = ""                     # 实名认证 / 资料修改 / 会员升级 / 活动报名
    applicant: str = ""
    gender: str = ""
    age: Optional[int] = None
    urgency: str = ""                  # 紧急 / 普通 / 低
    member_id: str = ""
    material: Optional[str] = None     # 原始材料串（逗号分隔）
    materials: list[str] = []          # 拆分后的材料列表
    change_field: Optional[str] = None
    change_from: Optional[str] = None
    change_to: Optional[str] = None
    pay_text: Optional[str] = None
    paid: Optional[bool] = None
    history: list[str] = []            # 同会员历史审核摘要


# 各审核类型要求的材料
REQUIRED_MATERIALS = {
    "实名认证": ["身份证正面", "身份证反面", "手持照"],
    "会员升级": ["支付凭证"],
    "活动报名": ["报名表"],
}

# 敏感字段：修改这些字段需要人工复核
SENSITIVE_FIELDS = ["身份证", "手机号", "婚姻状况", "姓名", "收入", "年龄"]


class AuditSuggestResponse(BaseModel):
    decision: str
    label: str
    confidence: int
    reasons: list[str]
    risks: list[str]
    suggestion: str
    priority: str


@app.post("/audit-suggest", response_model=AuditSuggestResponse)
def audit_suggest(req: AuditRequest) -> dict[str, Any]:
    risks: list[str] = []
    reasons: list[str] = []
    score = 70  # 通过倾向分，>=75 建议通过

    # 材料完整性（兼容 materials 列表与 material 字符串两种传法）
    provided = list(req.materials)
    if not provided and req.material:
        provided = [x.strip() for x in re.split(r"[,，、;；]", req.material) if x.strip()]

    need = REQUIRED_MATERIALS.get(req.type, [])
    if need:
        missing = [m for m in need if not any(m in p for p in provided)]
        if missing:
            score -= 35
            risks.append("缺少材料：" + "、".join(missing))
        else:
            score += 10
            reasons.append("材料齐全（%s）" % "、".join(need))
    elif provided:
        score += 5
        reasons.append("已提交材料：" + "、".join(provided[:3]))

    # 支付状态
    if req.paid is not None:
        if req.paid:
            score += 15
            reasons.append("款项已到账%s" % ("：" + req.pay_text if req.pay_text else ""))
        else:
            score -= 40
            risks.append("款项未到账，升级/报名不应先行通过")

    # 字段变更风险
    field = req.change_field or ""
    if field:
        old, new = str(req.change_from or ""), str(req.change_to or "")
        if any(k in field for k in SENSITIVE_FIELDS):
            score -= 25
            risks.append("修改敏感字段「%s」（%s → %s），建议核对证件原件" % (field, old, new))
        else:
            score += 5
            reasons.append("变更字段「%s」属常规信息" % field)
        # 年龄跳变检测
        if "年龄" in field:
            try:
                if abs(int(new) - int(old)) > 2:
                    score -= 20
                    risks.append("年龄变动超过 2 岁，存在虚报嫌疑")
            except ValueError:
                pass
        # 收入大幅上调
        if "收入" in field:
            try:
                on = int(re.sub(r"\D", "", old) or 0)
                nn = int(re.sub(r"\D", "", new) or 0)
                if on and nn > on * 1.5:
                    score -= 18
                    risks.append("收入由 %s 上调至 %s，涨幅超 50%%，建议要求收入证明" % (old, new))
            except ValueError:
                pass

    # 历史不良记录
    bad = [h for h in req.history if ("驳回" in h or "违规" in h or "投诉" in h)]
    if bad:
        score -= 20
        risks.append("该会员历史存在 %d 条驳回/违规记录" % len(bad))

    # 年龄合规
    if req.age is not None and req.age < 22:
        score -= 30
        risks.append("申请人年龄 %d 岁，低于法定婚龄相关服务门槛，需二次确认" % req.age)

    score = max(0, min(100, score))
    if score >= 75:
        decision, label = "approve", "建议通过"
    elif score >= 55:
        decision, label = "review", "建议人工复核"
    else:
        decision, label = "reject", "建议驳回"

    if not reasons:
        reasons.append("未发现明显加分项，按常规流程处理")
    if not risks:
        risks.append("未识别到风险点")

    logger.info(
        "audit-suggest | type=%s | member=%s | decision=%s | score=%d | risks=%d",
        req.type, req.member_id or req.applicant, decision, score, len(risks),
    )
    return {
        "decision": decision,
        "label": label,
        "confidence": score,
        "reasons": reasons,
        "risks": risks,
        "suggestion": "%s（置信度 %d%%）：%s" % (label, score, risks[0] if decision != "approve" else reasons[0]),
        "priority": "高" if req.urgency == "紧急" else "中" if req.urgency == "普通" else "低",
    }


# ---------------------------------------------------------------- 话术建议
class ReplyMessage(CamelModel):
    sender: str = ""
    content: str = ""


class ReplyRequest(CamelModel):
    name: str = ""
    kind: str = "member"
    messages: list[ReplyMessage] = []


# 意图 -> (标签, 三条话术模板)
INTENT_RULES: list[tuple[str, list[str], list[str]]] = [
    ("约见意向", ["见面", "约", "碰面", "见个面", "什么时候有空"], [
        "好的，我这边帮您协调时间。周末下午茶或工作日晚餐都可以安排，您更偏好哪种？",
        "对方也表达了见面意愿，我先约在本周六 15:00，地点选在环境安静的咖啡馆，您看合适吗？",
        "见面前我会把对方的基本情况再同步一份给您，避免初次见面找不到话题。您有想特别了解的方面吗？",
    ]),
    ("索要资料", ["资料", "照片", "简历", "条件", "情况怎么样"], [
        "资料我已经整理好，稍后发您。有任何想补充确认的条件随时告诉我。",
        "这边先发一份脱敏版资料给您过目，如果感兴趣我再安排双方深入了解。",
        "除了基础资料，我还整理了对方的生活状态和择偶偏好，一并发您参考。",
    ]),
    ("价格咨询", ["多少钱", "费用", "价格", "收费", "会员费"], [
        "我们的服务分基础、VIP、SVIP 三档，差异主要在专属红娘时长和匹配池范围，我把详细对比发您。",
        "费用方面可以根据您的实际需求来配置，先聊聊您希望达成的目标，我给您一个最合适的方案。",
        "现在有阶段性优惠，如果本月确认可以省一部分，我把优惠明细整理给您看看。",
    ]),
    ("犹豫顾虑", ["再想想", "考虑", "不太确定", "犹豫", "看看再说", "算了"], [
        "完全理解，这类决定确实需要时间。我先不打扰，把资料留给您，想聊的时候随时找我。",
        "方便说说主要顾虑在哪吗？如果是匹配方向的问题，我可以调整推荐范围再给您几个选择。",
        "我们支持先看匹配结果再决定，您可以零成本了解一轮，觉得合适再往下走。",
    ]),
    ("负面反馈", ["不合适", "没感觉", "不满意", "失望", "太差"], [
        "感谢您坦诚反馈，这对我调整方向很有帮助。能具体说说哪一点最不匹配吗？",
        "抱歉这次没能匹配到位，我会重新梳理您的核心诉求，三天内给出新一轮人选。",
        "我理解您的感受。接下来我会把筛选标准收紧，宁缺毋滥，确保推荐质量。",
    ]),
    ("表达感谢", ["谢谢", "感谢", "辛苦", "麻烦你了"], [
        "不客气，这是我应该做的。后续有任何想法都可以直接找我。",
        "能帮到您就好，我会持续关注匹配进展，有合适人选第一时间同步。",
        "客气了～祝您顺利，我这边随时待命。",
    ]),
    ("时间协调", ["时间", "有空", "周末", "工作日", "几点"], [
        "好的，我按您方便的时间来安排。工作日晚上和周末全天，您倾向哪个时段？",
        "我先跟对方确认档期，敲定后第一时间回您，尽量安排在您方便的时段。",
        "时间上完全配合您，确定后我会提前一天再提醒一次，避免临时冲突。",
    ]),
]

# 情绪词表：否定词优先于肯定词匹配，先剔除「不错/没问题」这类反转短语，
# 避免「不好」因同时含「不」与「好」被误判为积极。
SENTIMENT_POSITIVE_OVERRIDE = ("不错", "没问题", "没错", "不用客气", "不客气", "无妨")
SENTIMENT_NEGATIVE = ("不", "没", "无", "别", "算了", "失望", "差", "拒绝", "取消", "放弃")
SENTIMENT_POSITIVE = ("好", "可以", "行", "期待", "感兴趣", "谢谢", "愿意", "满意", "同意")


def detect_sentiment(text: str) -> str:
    """返回 消极 / 积极 / 中性。否定词优先级高于肯定词。"""
    s = text or ""
    for w in SENTIMENT_POSITIVE_OVERRIDE:
        s = s.replace(w, "＋")
    if any(k in s for k in SENTIMENT_NEGATIVE):
        return "消极"
    if "＋" in s or any(k in s for k in SENTIMENT_POSITIVE):
        return "积极"
    return "中性"


DEFAULT_REPLIES = [
    "收到，我先了解一下您的具体想法，方便的话简单说说您比较看重的三点条件？",
    "好的，我记录下来了。这两天我会筛选几位合适的人选发您参考。",
    "感谢反馈，我会据此调整推荐方向，稍后给您一个更新的方案。",
]


class ReplySuggestResponse(BaseModel):
    """context_size 保持既有 snake_case 拼写，不做改名。"""

    intent: str
    sentiment: str
    tone: str
    suggestions: list[str]
    context_size: int


@app.post("/reply-suggest", response_model=ReplySuggestResponse)
def reply_suggest(req: ReplyRequest) -> dict[str, Any]:
    # 取最近一条对方发来的消息作为意图判断依据
    incoming = [m.content for m in req.messages if m.sender not in ("agent", "红娘", "me")]
    last = incoming[-1] if incoming else (req.messages[-1].content if req.messages else "")

    intent, suggestions = "常规沟通", DEFAULT_REPLIES
    for name, keywords, templates in INTENT_RULES:
        if any(k in last for k in keywords):
            intent, suggestions = name, templates
            break

    # 情绪判断：影响话术语气提示（否定词优先）
    sentiment = detect_sentiment(last)
    logger.info("reply-suggest | intent=%s | sentiment=%s | ctx=%d", intent, sentiment, len(req.messages))

    tone = {
        "消极": "建议放低推进节奏，先共情再给选项，避免连续追问",
        "积极": "对方意愿明确，建议直接给出具体时间/方案推动落地",
        "中性": "建议用开放式问题引导对方多表达，收集匹配偏好",
    }[sentiment]

    return {
        "intent": intent,
        "sentiment": sentiment,
        "tone": tone,
        "suggestions": suggestions,
        "context_size": len(req.messages),
    }


# ---------------------------------------------------------------- 分配建议
class AgentInfo(CamelModel):
    id: str = ""
    name: str = ""
    level: str = ""            # 金牌红娘 / 资深红娘 / 普通红娘
    status: str = "在职"
    member_count: int = 0
    capacity: int = 80
    success_rate: int = 0
    rating: float = 0.0
    region: str = ""


class AssignRequest(CamelModel):
    member: Member
    member_level: str = ""     # 普通 / VIP / SVIP / 钻石VIP / 金卡
    member_city: str = ""
    agents: list[AgentInfo] = []


LEVEL_WEIGHT = {"金牌红娘": 20, "资深红娘": 12, "普通红娘": 5}
HIGH_VALUE = ("SVIP", "钻石VIP", "金卡", "VIP")


class AssignCandidate(CamelOut):
    agent_id: str = ""
    agent: str = ""
    level: str = ""
    score: float = 0.0
    load: float = 0.0
    reasons: list[str] = []


class AssignSuggestResponse(CamelOut):
    agent_id: Optional[str] = None
    agent: Optional[str] = None
    score: Optional[float] = None
    reason: str = ""
    reasons: list[str] = []
    candidates: list[AssignCandidate] = []


@app.post("/assign-suggest", response_model=AssignSuggestResponse)
def assign_suggest(req: AssignRequest) -> dict[str, Any]:
    city = req.member_city or req.member.city or ""
    level = req.member_level or ""
    ranked: list[dict[str, Any]] = []

    for a in req.agents:
        if a.status != "在职":
            continue
        score = 40.0
        reasons: list[str] = []

        # 负载：越空越优先（占满 100% 直接大幅减分）
        cap = a.capacity if a.capacity > 0 else 80
        load = a.member_count / cap
        if load < 0.6:
            score += 22
            reasons.append("当前负载 %d%%，服务余量充足" % int(load * 100))
        elif load < 0.85:
            score += 10
            reasons.append("负载 %d%%，仍可承接" % int(load * 100))
        else:
            score -= 25
            reasons.append("负载已达 %d%%，接单会摊薄服务质量" % int(load * 100))

        # 成功率
        score += a.success_rate * 0.25
        if a.success_rate >= 65:
            reasons.append("历史成功率 %d%%，位居前列" % a.success_rate)

        # 评分
        score += (a.rating - 4.0) * 10
        if a.rating >= 4.7:
            reasons.append("会员评分 %.1f，口碑优秀" % a.rating)

        # 高价值会员优先配金牌
        if level in HIGH_VALUE:
            score += LEVEL_WEIGHT.get(a.level, 0)
            if a.level == "金牌红娘":
                reasons.append("%s 会员建议由金牌红娘对接" % level)

        # 属地匹配
        if a.region and city and (a.region in city or city in a.region):
            score += 15
            reasons.append("负责区域与会员所在地一致（%s）" % a.region)

        ranked.append({
            "agentId": a.id,
            "agent": a.name,
            "level": a.level,
            "score": round(min(100.0, max(0.0, score)), 1),
            "load": round(load, 2),
            "reasons": reasons[:3],
        })

    ranked.sort(key=lambda x: x["score"], reverse=True)
    if not ranked:
        logger.warning("assign-suggest | no available agent | member=%s", req.member.id or req.member.name)
        return {"agent": None, "reason": "当前没有在职红娘可分配", "candidates": []}

    best = ranked[0]
    logger.info(
        "assign-suggest | member=%s | level=%s | best=%s | score=%.1f",
        req.member.id or req.member.name, level, best["agent"], best["score"],
    )
    return {
        "agentId": best["agentId"],
        "agent": best["agent"],
        "score": best["score"],
        "reason": "；".join(best["reasons"]) or "综合负载与业绩最优",
        "reasons": best["reasons"],
        "candidates": ranked[:5],
    }


# ---------------------------------------------------------------- 下一步动作
class NextActionRequest(CamelModel):
    name: str = ""
    stage: str = ""            # 新线索 / 初次沟通 / 需求分析 / 匹配推荐 / 约见安排 / 已成交
    level: str = ""
    stay_days: int = 0
    fresh: str = ""
    follows: list[str] = []


STAGE_PLAYBOOK = {
    "新线索": ("24 小时内完成首次电话沟通，确认择偶基本诉求与服务预期",
               "线索新鲜度最高，首次触达时效直接决定后续转化率", 2),
    "初次沟通": ("整理需求清单并发送服务方案资料，约定下一次沟通时间",
                 "已建立联系，需要在对方兴趣衰减前推进到需求确认", 3),
    "需求分析": ("产出 3 位候选人的匹配方案，并预约 30 分钟讲解",
                 "需求已明确，进入匹配推荐阶段可显著提升成单概率", 4),
    "匹配推荐": ("跟进候选人反馈，锁定 1 位安排见面",
                 "方案已送达，需推动到约见环节才能形成真实体验", 3),
    "约见安排": ("确认见面时间地点，做行前提醒与着装建议",
                 "见面履约率是成交前最后一道关口，需重点保障", 2),
    "已成交": ("回访满意度，邀请转介绍并录入服务档案",
               "已成交客户是成本最低的获客来源", 7),
}


class NextActionResponse(CamelOut):
    action: str
    reason: str
    priority: str
    sla_days: int
    stay_days: int
    warnings: list[str] = []


@app.post("/next-action", response_model=NextActionResponse)
def next_action(req: NextActionRequest) -> dict[str, Any]:
    action, reason, sla = STAGE_PLAYBOOK.get(
        req.stage, ("补全线索信息并确认当前阶段", "阶段信息缺失，需先归位再制定动作", 3))

    warnings: list[str] = []
    priority = "低"
    if req.stay_days >= sla * 3:
        priority = "高"
        warnings.append("已停留 %d 天，超出该阶段 SLA（%d 天）%d 倍，流失风险显著"
                        % (req.stay_days, sla, req.stay_days // max(1, sla)))
        action = "【流失预警】" + action + "；建议叠加一次专属优惠或红娘升级触达"
    elif req.stay_days >= sla:
        priority = "中"
        warnings.append("已停留 %d 天，接近该阶段 SLA（%d 天）" % (req.stay_days, sla))

    # 高价值线索提优先级
    if req.level in HIGH_VALUE and priority != "高":
        priority = "高" if priority == "中" else "中"
        warnings.append("%s 级线索，建议优先排期" % req.level)

    # 跟进记录密度
    if not req.follows:
        warnings.append("尚无跟进记录，建议本次沟通后立即补录")
    elif len(req.follows) >= 5 and req.stage in ("新线索", "初次沟通"):
        warnings.append("已跟进 %d 次仍停留在前期阶段，建议更换沟通策略或转交他人" % len(req.follows))

    return {
        "action": action,
        "reason": reason,
        "priority": priority,
        "slaDays": sla,
        "stayDays": req.stay_days,
        "warnings": warnings,
    }


# ---------------------------------------------------------------- 跟进摘要
class FollowupItem(CamelModel):
    type: str = ""
    content: str = ""
    outcome: str = ""
    next_follow_up: Optional[str] = None
    created_at: Optional[str] = None


class FollowupSummaryRequest(CamelModel):
    member_id: str = ""
    name: str = ""
    gender: str = ""
    age: Optional[int] = None
    level: str = ""
    last_follow_up: Optional[str] = None
    follows: list[FollowupItem] = []


OUTCOME_INTENT = {"成交意向": "高意向", "无意向": "低意向", "待定": "待定"}


class FollowupSummaryResponse(CamelOut):
    summary: str
    count: int
    latest_type: str
    latest_outcome: str
    intention: str
    next_follow_up: Optional[str] = None
    days_since_last: Optional[int] = None
    suggestion: str
    reminders: list[str] = []


@app.post("/followup-summary", response_model=FollowupSummaryResponse)
def followup_summary(req: FollowupSummaryRequest) -> dict[str, Any]:
    """会员跟进摘要：汇总时间线 -> 意向判断 + 下一步建议（离线可解释）。"""
    follows = req.follows
    count = len(follows)
    latest = follows[-1] if follows else None
    latest_outcome = (latest.outcome or "无") if latest else "无"
    latest_type = (latest.type or "无") if latest else "无"
    intention = OUTCOME_INTENT.get(latest_outcome, "待定")
    types = sorted({f.type for f in follows if f.type})
    next_due = latest.next_follow_up if latest else None

    # 距上次跟进天数（用于提醒，无记录则提示首触达）
    days_since = None
    last_ts = None
    if follows:
        for f in reversed(follows):
            if f.created_at:
                try:
                    last_ts = f.created_at[:10]
                    break
                except Exception:
                    last_ts = None
    if last_ts:
        try:
            from datetime import date
            y, m, d = (int(x) for x in last_ts.split("-")[:3])
            days_since = (date.today() - date(y, m, d)).days
        except Exception:
            days_since = None

    # 下一步建议
    if count == 0:
        suggestion = "尚无跟进记录，建议尽快电话回访一次，确认择偶诉求并约定下次沟通时间"
    elif latest_outcome == "成交意向":
        suggestion = "客户意向明确，建议 3 天内约见并推进匹配方案，锁定服务档位"
    elif latest_outcome == "无意向":
        suggestion = "客户当前无意向，建议暂缓高频打扰，改为每月 1 次轻触达（节日关怀 / 新资料推荐）"
    elif next_due:
        suggestion = "下次跟进建议安排在 %s，围绕择偶要求沟通并推进到需求确认" % next_due
    else:
        suggestion = "下次跟进建议安排在 3 天内，通过 %s 沟通确认择偶要求并推进匹配" % latest_type

    reminders: list[str] = []
    if count == 0:
        reminders.append("建档至今未跟进，请优先安排首次回访（24 小时内转化率最高）")
    if days_since is not None and days_since >= 7:
        reminders.append("已 %d 天未跟进，建议今日安排一次回访避免意向流失" % days_since)
    if latest_outcome == "无意向" and count >= 3:
        reminders.append("多次「无意向」反馈，建议调整沟通策略或换红娘跟进")
    if req.level in HIGH_VALUE:
        reminders.append("%s 级会员属高价值客户，建议优先排期约见" % req.level)
    if not reminders:
        reminders.append("跟进节奏正常，保持按计划回访即可")

    return {
        "summary": "共跟进 %d 次，覆盖 %s；最近一次（%s）意向：%s"
                   % (count, "、".join(types) if types else "—", latest_type, latest_outcome),
        "count": count,
        "latestType": latest_type,
        "latestOutcome": latest_outcome,
        "intention": intention,
        "nextFollowUp": next_due,
        "daysSinceLast": days_since,
        "suggestion": suggestion,
        "reminders": reminders,
    }


# ================================================================ B 类 · AI 增效
# 1) /coach/profile  画像教练：档案完整度诊断 + 可执行优化清单
# 2) /moderate       内容风控：发送前的违规/诈骗/引流检测与脱敏
# 3) /feedback       反馈学习：红娘采纳/否决 -> 维度权重在线调整
# 三者同样是「规则引擎 + 加权评分」，离线可用、结论可解释、可回滚。


# ---------------------------------------------------------------- 反馈学习
WEIGHT_MIN, WEIGHT_MAX = 0.5, 1.6
WEIGHT_STEP = 0.04          # 单条反馈的最大调整幅度，防止个别样本剧烈扰动
DIM_LABELS = {
    "age": "年龄", "education": "学历", "city": "地域",
    "hobby": "兴趣", "height": "身高", "income": "收入",
}

# 内存态权重与统计。Java 侧持久化全量反馈，服务重启后由 /feedback/replay 回灌，
# 因此 AI 服务本身保持无状态可横向扩容。
FEEDBACK_WEIGHTS: dict[str, float] = {d: 1.0 for d in DIM_BASE}
FEEDBACK_STATS: dict[str, dict[str, int]] = {
    d: {"accept": 0, "reject": 0} for d in DIM_BASE
}
FEEDBACK_TOTAL = {"accept": 0, "reject": 0, "applied": 0}


def dim_weight(dim: str) -> float:
    return FEEDBACK_WEIGHTS.get(dim, 1.0)


def _clamp(v: float) -> float:
    return round(max(WEIGHT_MIN, min(WEIGHT_MAX, v)), 4)


class FeedbackItem(CamelModel):
    scene: str = "match"                 # match / reply / assign / audit
    item_id: str = ""                    # 被评价对象（如 pairId）
    action: str = "accept"               # accept 采纳 / reject 否决
    dimensions: list[str] = []           # 命中的维度键（age/education/...）
    reason: str = ""
    operator: str = ""


class FeedbackRequest(CamelModel):
    items: list[FeedbackItem] = []
    replay: bool = False                 # true = 历史回灌（先重置再应用，保证幂等）


def _apply_feedback(items: list[FeedbackItem]) -> int:
    applied = 0
    for it in items:
        delta = WEIGHT_STEP if it.action == "accept" else -WEIGHT_STEP
        key = "accept" if it.action == "accept" else "reject"
        FEEDBACK_TOTAL[key] += 1
        # 未指定维度时不调权，仅计入总量（避免无信息样本污染模型）
        for dim in it.dimensions:
            if dim not in FEEDBACK_WEIGHTS:
                continue
            FEEDBACK_WEIGHTS[dim] = _clamp(FEEDBACK_WEIGHTS[dim] + delta)
            FEEDBACK_STATS[dim][key] += 1
            applied += 1
    FEEDBACK_TOTAL["applied"] += applied
    return applied


def _weights_view() -> dict[str, Any]:
    rows = []
    for dim, w in FEEDBACK_WEIGHTS.items():
        st = FEEDBACK_STATS[dim]
        total = st["accept"] + st["reject"]
        rows.append({
            "dimension": dim,
            "label": DIM_LABELS.get(dim, dim),
            "weight": w,
            "baseScore": DIM_BASE[dim],
            "effectiveScore": round(DIM_BASE[dim] * w, 1),
            "accept": st["accept"],
            "reject": st["reject"],
            "acceptRate": round(st["accept"] / total, 3) if total else None,
            "trend": "上调" if w > 1.001 else ("下调" if w < 0.999 else "持平"),
        })
    rows.sort(key=lambda r: r["weight"], reverse=True)
    return {
        "weights": rows,
        "total": dict(FEEDBACK_TOTAL),
        "bounds": {"min": WEIGHT_MIN, "max": WEIGHT_MAX, "step": WEIGHT_STEP},
    }


class FeedbackStatsResponse(CamelOut):
    weights: list[dict[str, Any]] = []
    total: dict[str, int] = {}
    bounds: dict[str, float] = {}


class FeedbackResponse(FeedbackStatsResponse):
    accepted: int = 0
    applied_dimensions: int = 0
    mode: str = "incremental"


@app.post("/feedback", response_model=FeedbackResponse)
def feedback(req: FeedbackRequest) -> dict[str, Any]:
    """接收红娘对 AI 建议的采纳/否决，在线微调匹配维度权重。

    replay=true 时先回到出厂权重再全量重放，保证「同一批历史反馈 → 同一组权重」，
    避免服务重启或多实例之间出现权重漂移。
    """
    if req.replay:
        for d in FEEDBACK_WEIGHTS:
            FEEDBACK_WEIGHTS[d] = 1.0
            FEEDBACK_STATS[d] = {"accept": 0, "reject": 0}
        FEEDBACK_TOTAL.update({"accept": 0, "reject": 0, "applied": 0})

    applied = _apply_feedback(req.items)
    res = _weights_view()
    res["accepted"] = len(req.items)
    res["appliedDimensions"] = applied
    res["mode"] = "replay" if req.replay else "incremental"
    return res


@app.get("/feedback/stats", response_model=FeedbackStatsResponse)
def feedback_stats() -> dict[str, Any]:
    return _weights_view()


# ---------------------------------------------------------------- 内容风控
RE_PHONE = re.compile(r"(?<!\d)1[3-9]\d{9}(?!\d)")
RE_WECHAT = re.compile(r"(?:微信|weixin|wx|vx|v信|威信)\s*[:：]?\s*([A-Za-z][-_A-Za-z0-9]{5,19})", re.I)
RE_QQ = re.compile(r"(?:qq|扣扣)\s*[:：]?\s*(\d{5,12})", re.I)
RE_EMAIL = re.compile(r"[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Za-z]{2,}")
RE_CARD = re.compile(r"(?<!\d)\d{16,19}(?!\d)")
RE_ID = re.compile(r"(?<!\d)\d{17}[\dXx](?!\d)")

# category -> (标签, 严重度 1~3, 关键词)
RISK_RULES: dict[str, tuple[str, int, tuple[str, ...]]] = {
    "fraud": ("涉嫌资金诈骗", 3, (
        "转账", "汇款", "打款", "借钱", "借款", "垫付", "返利", "刷单", "杀猪盘",
        "投资理财", "内幕消息", "稳赚", "包赚", "USDT", "虚拟币", "博彩", "菠菜",
        "验证码", "银行卡号", "扫码支付",
    )),
    "porn": ("色情低俗", 3, (
        "约炮", "一夜情", "开房", "包养", "援交", "裸聊", "情色", "上床",
    )),
    "illegal": ("违法违规", 3, (
        "代孕", "办证", "假证", "枪支", "毒品", "迷药", "高利贷", "套现",
    )),
    "offsite": ("引导脱离平台", 2, (
        "私下交易", "线下交易", "绕过平台", "不走平台", "别通过公司", "私聊",
        "加我好友", "加个微信", "留个联系方式", "退单", "退费返现",
    )),
    "abuse": ("辱骂攻击", 2, (
        "傻逼", "煞笔", "sb", "废物", "垃圾东西", "滚蛋", "去死", "神经病", "贱",
    )),
    "promise": ("过度承诺", 1, (
        "保证成功", "百分百", "100%成功", "包成", "一定能成", "无效退全款",
    )),
}


class ModerateRequest(CamelModel):
    text: str = ""
    scene: str = "message"       # message 会话 / bio 自我介绍 / activity 活动文案
    role: str = "AGENT"          # 发送方角色，用于差异化策略


def _excerpt(text: str, idx: int, width: int = 12) -> str:
    lo = max(0, idx - width // 2)
    return text[lo: lo + width].replace("\n", " ")


class ModerateResponse(CamelOut):
    action: str
    risk_level: str
    risk_score: int
    hits: list[dict[str, Any]] = []
    pii: list[dict[str, str]] = []
    sanitized: str = ""
    advice: list[str] = []
    scene: str = ""


@app.post("/moderate", response_model=ModerateResponse)
def moderate(req: ModerateRequest) -> dict[str, Any]:
    """发送前内容风控：识别诈骗/色情/违法/引流/辱骂/过度承诺 + 联系方式外泄。

    返回 action：pass 放行 / warn 二次确认 / block 拦截。
    同时给出脱敏文本，前端可一键替换后再发送。
    """
    text = req.text or ""
    low = text.lower()
    hits: list[dict[str, Any]] = []

    for cat, (label, severity, words) in RISK_RULES.items():
        for w in words:
            i = low.find(w.lower())
            if i >= 0:
                hits.append({
                    "category": cat, "label": label, "severity": severity,
                    "keyword": w, "excerpt": _excerpt(text, i),
                })
                break  # 同类命中一次即可，避免刷屏

    # 个人信息外泄（会话场景下红娘向会员透露联系方式属高风险）
    pii: list[dict[str, str]] = []
    sanitized = text
    for name, label, pattern, mask in (
        ("phone", "手机号", RE_PHONE, lambda m: m.group()[:3] + "****" + m.group()[-4:]),
        ("idCard", "身份证号", RE_ID, lambda m: m.group()[:6] + "********" + m.group()[-4:]),
        ("bankCard", "银行卡号", RE_CARD, lambda m: "**** **** **** " + m.group()[-4:]),
        ("email", "邮箱", RE_EMAIL, lambda m: m.group()[0] + "***@" + m.group().split("@")[-1]),
        ("wechat", "微信号", RE_WECHAT, lambda m: m.group(0).replace(m.group(1), m.group(1)[:2] + "****")),
        ("qq", "QQ号", RE_QQ, lambda m: m.group(0).replace(m.group(1), m.group(1)[:2] + "****")),
    ):
        found = pattern.findall(text)
        if found:
            pii.append({"type": name, "label": label, "count": str(len(found))})
            sanitized = pattern.sub(mask, sanitized)

    if pii:
        sev = 2 if req.scene == "message" else 3
        hits.append({
            "category": "pii", "label": "联系方式/证件号外泄", "severity": sev,
            "keyword": "、".join(p["label"] for p in pii), "excerpt": "",
        })

    top = max((h["severity"] for h in hits), default=0)
    score = min(100, sum(h["severity"] * 18 for h in hits))
    if top >= 3:
        action, risk = "block", "高"
    elif top == 2:
        action, risk = "warn", "中"
    elif top == 1:
        action, risk = "warn", "低"
    else:
        action, risk = "pass", "无"

    advice: list[str] = []
    cats = {h["category"] for h in hits}
    if "fraud" in cats:
        advice.append("涉及资金往来的表述一律不得发送，如确需收费请引导至平台内订单流程")
    if "pii" in cats:
        advice.append("请使用平台内会话沟通，联系方式由双方确认意向后再由系统交换")
    if "offsite" in cats:
        advice.append("引导线下私单会导致服务无保障且违反平台规则，已记录本次拦截")
    if "abuse" in cats or "porn" in cats:
        advice.append("请调整为专业、尊重的表达方式后再发送")
    if "promise" in cats:
        advice.append("避免使用绝对化承诺，改为「我们会全力协助」等合规表述")
    if not advice:
        advice.append("内容合规，可以发送")

    return {
        "action": action,
        "riskLevel": risk,
        "riskScore": score,
        "hits": sorted(hits, key=lambda h: -h["severity"]),
        "pii": pii,
        "sanitized": sanitized,
        "advice": advice,
        "scene": req.scene,
    }


# ---------------------------------------------------------------- 画像教练
class CoachRequest(CamelModel):
    id: Optional[str] = None
    name: str = ""
    gender: str = ""
    age: Optional[int] = None
    city: str = ""
    education: str = ""
    job: str = ""
    income: str = ""
    height: Optional[int] = None
    marriage: str = ""
    house: str = ""
    car: str = ""
    hometown: str = ""
    tags: list[str] = []
    bio: str = ""
    requirement: str = ""
    verified: Optional[bool] = None
    photo_count: int = 0
    match_count: int = 0
    success_rate: int = 0


# 字段键 -> (展示名, 权重分, 补全动作, 曝光收益)
PROFILE_FIELDS: list[tuple[str, str, int, str, str]] = [
    ("age", "年龄", 8, "补充准确年龄", "年龄是首要筛选项，缺失会被大部分搜索过滤"),
    ("city", "常住城市", 8, "填写常住城市（精确到区）", "同城匹配权重最高，可提升约 25% 曝光"),
    ("education", "学历", 7, "补充最高学历", "学历是高频筛选条件"),
    ("job", "职业", 7, "填写具体职业与行业", "职业描述影响首屏点击率"),
    ("income", "收入区间", 6, "选择收入区间（可选择区间而非具体数字）", "缺失会被收入筛选直接排除"),
    ("height", "身高", 5, "补充身高", "身高是硬性筛选项之一"),
    ("marriage", "婚况", 6, "如实填写婚姻状况", "婚况不明会显著降低信任度"),
    ("house", "房产", 4, "补充住房情况", "影响长期规划匹配"),
    ("car", "车辆", 3, "补充车辆情况", "锦上添花项"),
    ("hometown", "籍贯", 3, "补充籍贯", "部分会员看重家乡文化契合"),
    ("tags", "兴趣标签", 10, "至少添加 3 个兴趣标签", "兴趣交集是破冰话题的主要来源"),
    ("bio", "自我介绍", 12, "撰写 80 字以上的自我介绍", "自我介绍完整的档案沟通转化率高约 40%"),
    ("requirement", "择偶要求", 10, "写清 3 条最看重的择偶条件", "择偶要求决定匹配算法的准确度"),
    ("photoCount", "生活照", 8, "上传 3 张以上清晰生活照", "有照片档案的应答率是无照片的 3 倍以上"),
    ("verified", "实名认证", 3, "完成实名认证", "认证标识可显著提升对方信任"),
]


def _profile_value(req: CoachRequest, key: str) -> Any:
    if key == "photoCount":
        return req.photo_count if req.photo_count > 0 else None
    if key == "tags":
        return req.tags if len(req.tags) >= 3 else None
    if key == "bio":
        return req.bio if len(req.bio.strip()) >= 80 else None
    if key == "requirement":
        return req.requirement if len(req.requirement.strip()) >= 15 else None
    if key == "verified":
        return True if req.verified else None
    return getattr(req, key, None) or None


class CoachProfileResponse(CamelOut):
    score: int
    level: str
    verdict: str
    completed_weight: int
    total_weight: int
    strengths: list[str] = []
    missing: list[dict[str, Any]] = []
    quick_wins: list[dict[str, Any]] = []
    projected_score: int
    risks: list[str] = []
    script: str


@app.post("/coach/profile", response_model=CoachProfileResponse)
def coach_profile(req: CoachRequest) -> dict[str, Any]:
    """会员画像教练：给出完整度评分、缺口清单与按收益排序的优化动作。"""
    total_weight = sum(f[2] for f in PROFILE_FIELDS)
    got = 0
    missing: list[dict[str, Any]] = []
    strengths: list[str] = []

    for key, label, weight, action, impact in PROFILE_FIELDS:
        if _profile_value(req, key) is not None:
            got += weight
            if weight >= 8:
                strengths.append(label + "已完善")
        else:
            missing.append({
                "field": key, "label": label, "weight": weight,
                "action": action, "impact": impact,
                "priority": "高" if weight >= 8 else ("中" if weight >= 5 else "低"),
            })

    score = round(got * 100 / total_weight)
    if score >= 90:
        level, verdict = "优秀", "档案信息完整，可直接进入优先推荐池"
    elif score >= 75:
        level, verdict = "良好", "档案基本完整，补齐高优先级缺口后即可进入优先推荐池"
    elif score >= 55:
        level, verdict = "待完善", "关键信息缺失较多，当前匹配曝光受限"
    else:
        level, verdict = "不合格", "档案信息严重不足，建议红娘协助一次性补录后再启动牵线"

    missing.sort(key=lambda m: -m["weight"])
    quick_wins = missing[:3]
    gain = sum(m["weight"] for m in quick_wins)
    projected = round(min(100, (got + gain) * 100 / total_weight))

    # 质量风险：有量无质
    risks: list[str] = []
    if req.match_count >= 5 and req.success_rate <= 10:
        risks.append("已牵线 %d 次但成功率仅 %d%%，建议重估择偶要求或调整推荐策略"
                     % (req.match_count, req.success_rate))
    if req.bio and len(req.bio.strip()) < 30:
        risks.append("自我介绍过短（%d 字），难以形成记忆点" % len(req.bio.strip()))
    if req.requirement and len(req.requirement.strip()) > 200:
        risks.append("择偶要求过长且条件可能过多，会大幅压缩候选池")
    if len(req.tags) > 12:
        risks.append("兴趣标签过多（%d 个），重点不突出，建议精简到 5~8 个" % len(req.tags))
    if not risks:
        risks.append("暂未发现明显质量风险")

    # 给红娘的一句话沟通脚本
    if quick_wins:
        script = "您好%s，为了让推荐更精准，还差%s没有填写，%s，方便现在补充一下吗？" % (
            ("，" + req.name) if req.name else "",
            "、".join(m["label"] for m in quick_wins),
            quick_wins[0]["impact"],
        )
    else:
        script = "您好%s，您的档案已经很完整了，我这边会优先为您安排匹配推荐。" % (
            ("，" + req.name) if req.name else "")

    return {
        "score": score,
        "level": level,
        "verdict": verdict,
        "completedWeight": got,
        "totalWeight": total_weight,
        "strengths": strengths[:5],
        "missing": missing,
        "quickWins": quick_wins,
        "projectedScore": projected,
        "risks": risks,
        "script": script,
    }


# ---------------------------------------------------------------- 约会教练
class NextStepRequest(CamelModel):
    pairId: Optional[str] = None
    stage: str = ""                      # 规范键 matched/contacted/chatting/met_once/dating/deepening/committed 或中文
    meetCount: int = 0                   # 已见面次数（影响阶段推导）
    lastContactDays: int = 0             # 距上次联系天数（用于停滞预警）
    interests: list[str] = []            # 双方兴趣标签，用于话题库定制
    recentTopics: list[str] = []         # 已聊过的话题，避免重复
    gender: str = ""                     # 查看教练一方性别（穿搭建议，可空）
    season: str = ""                     # 春/夏/秋/冬，空则按当前月份推导


_STAGE_ALIASES = {
    "matched": "matched", "刚牵线": "matched", "匹配": "matched", "匹配成功": "matched", "初步推荐": "matched",
    "contacted": "contacted", "已联系": "contacted", "交换": "contacted", "加微信": "contacted",
    "chatting": "chatting", "聊得不错": "chatting", "沟通中": "chatting", "线上": "chatting", "安排见面": "chatting",
    "met_once": "met_once", "见过一次": "met_once", "首次见面": "met_once", "已见面": "met_once", "已约见": "met_once",
    "dating": "dating", "约会中": "dating", "稳定约会": "dating",
    "deepening": "deepening", "升温": "deepening", "关系升温": "deepening",
    "committed": "committed", "确定关系": "committed", "谈婚论嫁": "committed", "已成功": "committed",
}
_STAGE_LABEL = {
    "matched": "刚牵线", "contacted": "已建立联系", "chatting": "线上沟通中",
    "met_once": "已首次见面", "dating": "稳定约会中", "deepening": "关系升温中", "committed": "已确定关系",
}

# 每个阶段的建议库：下一步动作 / 行动窗口 / 通用话题 / 破冰话术 / 升温提问 / 教练提示
_NEXT = {
    "matched": {
        "window": "3 天内",
        "next": "先互发一条轻松的自我介绍，本周内约一次 15 分钟线上语音破冰，先别急着线下见面。",
        "genericTopics": ["各自的城市生活", "最近一次旅行", "喜欢的食物口味"],
        "openers": ["你好呀，我是红娘牵线的 XX，终于联系上啦~", "看到你资料里也喜欢 XX，感觉有点缘分"],
        "deepen": ["你理想中的周末一般怎么过？", "最近工作节奏忙吗，平时怎么放松？"],
        "tips": ["破冰以轻松话题为主，别一上来盘问收入房车", "先建立舒适感，再谈见面"],
    },
    "contacted": {
        "window": "7 天内",
        "next": "趁热打铁把首次见面定下来：选一个双方都方便的时间，约在人流适中的公共场所。",
        "genericTopics": ["附近的咖啡馆", "都想尝试的活动", "一周里哪天比较闲"],
        "openers": ["这周末有空吗，要不要去喝杯咖啡聊聊？", "我发现一家很安静的书店咖啡，感觉你会喜欢"],
        "deepen": ["你第一次约会一般喜欢什么形式？", "有什么一直想吃但没去成的店吗？"],
        "tips": ["给出具体时间地点，比「改天约」更容易成行", "首次见面控制在 1~1.5 小时，留有余地"],
    },
    "chatting": {
        "window": "本周内",
        "next": "锁定首次见面的时间与地点，提前发好定位和预计时长，让对方有安全感。",
        "genericTopics": ["见面的具体安排", "交通是否方便", "有没有忌口或过敏"],
        "openers": ["那我们就周六下午两点老地方见？", "我把定位发你，到了给我发个消息"],
        "deepen": ["见面你一般喜欢安静还是热闹？", "有没有什么你特别在意的相处细节？"],
        "tips": ["把模糊的好感变成具体的约会", "主动确认细节，显得靠谱"],
    },
    "met_once": {
        "window": "10 天内",
        "next": "安排第二次约会，比首次更互动一些：手工、运动、市集或小型展览，制造共同体验。",
        "genericTopics": ["上次没聊完的话题", "一起做点什么", "下次约在哪座城市或商圈"],
        "openers": ["上次聊得挺开心的，要不要一起去做个陶艺？", "这周末有个市集好像不错，一起去逛逛？"],
        "deepen": ["你觉得我们相处起来舒服吗？", "你期待的恋爱节奏大概是怎样的？"],
        "tips": ["第二次约会重在共同体验而非吃饭聊天", "尊重对方身体边界与节奏"],
    },
    "dating": {
        "window": "每 1~2 周",
        "next": "关系进入稳定期，安排半日以上的深度相处：近郊一日游、一起做饭、看一场展览，制造专属记忆。",
        "genericTopics": ["共同的周末计划", "对未来的大致设想", "彼此的家庭与朋友"],
        "openers": ["这周要不要来我家一起做顿饭？", "天气好了，我们去近郊转转吧"],
        "deepen": ["你理想的生活状态是什么样的？", "我们在哪些事情上特别聊得来？"],
        "tips": ["稳定期要制造只属于两人的回忆", "适时聊价值观与长期打算，但别变成面试"],
    },
    "deepening": {
        "window": "近期",
        "next": "关系已升温，是明确走向的关键节点：选一个放松的场合，坦诚聊聊彼此对未来的规划与期待。",
        "genericTopics": ["对婚姻或同居的看法", "事业与城市的规划", "双方家庭的相处边界"],
        "openers": ["其实我挺享受和你在一起的状态，想听听你对我们的期待", "我们认真聊聊以后吧，你是怎么想的？"],
        "deepen": ["如果我们继续走下去，你最在意的是什么？", "哪些事是你绝对不能接受的？"],
        "tips": ["升温期要确认关系而非一直暧昧", "坦诚但温和，给对方表达空间"],
    },
    "committed": {
        "window": "持续",
        "next": "关系已确定，重心转向共同经营：建立日常沟通仪式、规划见家长与未来节点。",
        "genericTopics": ["日常的小仪式感", "见家长的节奏", "共同的储蓄或旅行目标"],
        "openers": ["下个月带你去见我家人吧？", "我们一起攒个旅行基金怎么样？"],
        "deepen": ["我们怎么分工家务比较舒服？", "对财务和大事的决策方式想怎么定？"],
        "tips": ["确定关系后日常经营比惊喜更重要", "尽早对齐家庭与金钱观，减少后续摩擦"],
    },
}

_SEASON_OUTFIT = {
    "春": ["轻薄针织或衬衫，颜色清爽", "风衣或薄外套应对温差"],
    "夏": ["透气棉麻短袖或连衣裙，避免过于暴露", "浅色为主，注意防晒与体味"],
    "秋": ["风衣或卫衣叠穿有层次感", "暖色调显得温暖亲切"],
    "冬": ["大衣或羽绒内搭利落", "围巾点缀提升精致度"],
}


def _norm_stage(req: NextStepRequest) -> str:
    s = (req.stage or "").strip().lower()
    if s in _STAGE_ALIASES:
        return _STAGE_ALIASES[s]
    raw = req.stage or ""
    for key, val in _STAGE_ALIASES.items():
        if key and key in raw:
            return val
    if req.meetCount >= 5:
        return "deepening"
    if req.meetCount >= 2:
        return "dating"
    if req.meetCount == 1:
        return "met_once"
    if req.lastContactDays > 0:
        return "chatting"
    return "matched"


def _season(req: NextStepRequest) -> str:
    if req.season in ("春", "夏", "秋", "冬"):
        return req.season
    m = datetime.now().month
    return "春" if 3 <= m <= 5 else "夏" if 6 <= m <= 8 else "秋" if 9 <= m <= 11 else "冬"


def _pick_topics(stage_key: str, interests: list[str], recent: list[str] = []) -> list[str]:
    base = _NEXT[stage_key]["genericTopics"]
    out: list[str] = []
    seen: set[str] = set()
    recent_low = {t.lower() for t in recent}
    for it in interests[:3]:
        t = "聊聊你们都喜欢的「%s」" % it
        if t.lower() not in seen and t.lower() not in recent_low:
            seen.add(t.lower())
            out.append(t)
    for t in base:
        if t.lower() in recent_low:
            continue
        if t.lower() not in seen:
            seen.add(t.lower())
            out.append(t)
    return out[:5]


class CoachNextStepResponse(CamelOut):
    stage: str
    stageLabel: str
    nextStep: str
    actionWindow: str
    topics: list[str] = []
    outfit: list[str] = []
    openers: list[str] = []
    deepenQuestions: list[str] = []
    tips: list[str] = []
    warnings: list[str] = []
    source: str = "rule"


@app.post("/coach/next-step", response_model=CoachNextStepResponse)
def coach_next_step(req: NextStepRequest) -> dict[str, Any]:
    """约会教练：基于牵线进展，给出下一步约会建议 + 话题/穿搭话术库。"""
    stage_key = _norm_stage(req)
    advice = _NEXT[stage_key]
    season = _season(req)
    topics = _pick_topics(stage_key, req.interests, req.recentTopics)
    outfit = list(_SEASON_OUTFIT[season])

    warnings: list[str] = []
    if req.lastContactDays >= 7:
        warnings.append("已 %d 天未联系，关系有变冷风险，建议今天就发起一个轻松话题重启。" % req.lastContactDays)
    if req.meetCount >= 3 and stage_key in ("matched", "contacted", "chatting"):
        warnings.append("已见面 %d 次却仍停留在初识阶段，建议明确关系走向，避免陷入「朋友区」。" % req.meetCount)
    if not warnings:
        warnings.append("进展节奏健康，保持当前频率即可。")

    if req.gender == "女":
        outfit.append("可适度用心仪色系提升辨识度")
    elif req.gender == "男":
        outfit.append("干净得体比潮流更重要，注意指甲与鞋面整洁")

    return {
        "stage": stage_key,
        "stageLabel": _STAGE_LABEL[stage_key],
        "nextStep": advice["next"],
        "actionWindow": advice["window"],
        "topics": topics,
        "outfit": outfit,
        "openers": advice["openers"],
        "deepenQuestions": advice["deepen"],
        "tips": advice["tips"],
        "warnings": warnings,
        "source": "rule",
    }


# ============================================================ 1-F 运营中心深化
class ChurnPredictRequest(BaseModel):
    activity_slope: float = Field(0.0, ge=-1.0, le=1.0, description='活跃度下降斜率，负表示下滑')
    feedback_sentiment: float = Field(0.0, ge=-1.0, le=1.0, description='反馈情绪，负为差评倾向')
    match_hit_rate: float = Field(0.5, ge=0.0, le=1.0, description='匹配命中率')


class ChurnPredictResponse(BaseModel):
    score: float
    level: str
    predicted_days: int = 14
    reasons: list[str]


@app.post('/churn/predict', response_model=ChurnPredictResponse)
def churn_predict(req: ChurnPredictRequest) -> dict[str, Any]:
    '''预测式流失：活跃度下滑 + 反馈情绪 + 匹配命中率 → 14 天流失概率。'''
    gap_risk = 0.45 * max(0.0, -req.activity_slope)
    if req.feedback_sentiment < 0:
        fb_risk = 0.25
    elif req.feedback_sentiment > 0:
        fb_risk = -0.15
    else:
        fb_risk = 0.0
    hit_risk = 0.30 * (1 - req.match_hit_rate)
    score = max(0.0, min(1.0, 0.15 + gap_risk + fb_risk + hit_risk))
    level = '高' if score >= 0.7 else ('中' if score >= 0.4 else '低')
    reasons: list[str] = []
    if req.activity_slope < 0:
        reasons.append('活跃度下滑（斜率 %.2f）' % req.activity_slope)
    if req.feedback_sentiment < 0:
        reasons.append('反馈情绪偏负面')
    if req.match_hit_rate < 0.3:
        reasons.append('匹配命中率偏低（%.0f%%）' % (req.match_hit_rate * 100))
    if not reasons:
        reasons.append('当前指标平稳')
    return {'score': round(score, 2), 'level': level, 'predicted_days': 14, 'reasons': reasons}


class FunnelAttrRequest(BaseModel):
    stages: list[dict[str, Any]]


class FunnelAttrResponse(BaseModel):
    worst_step: str
    worst_drop: int
    reasons: list[str]


@app.post('/funnel/attribute', response_model=FunnelAttrResponse)
def funnel_attribute(req: FunnelAttrRequest) -> dict[str, Any]:
    '''漏斗归因：找出掉点最多的步骤 + 候选原因标签。'''
    stages = req.stages or []
    worst = stages[0] if stages else {'name': '注册', 'count': 0}
    worst_drop = -1
    for i in range(1, len(stages)):
        prev = stages[i - 1].get('count', 0)
        cur = stages[i].get('count', 0)
        drop = prev - cur
        if drop > worst_drop:
            worst_drop = drop
            worst = stages[i]
    name = worst.get('name', '注册')
    reasons_map = {
        '认证': ['认证流程过长', '实人认证未自动化', '引导话术不足'],
        '付费': ['价格敏感', '缺乏分期/优惠券', '未触发紧迫感'],
        '牵线': ['匹配精准度待提升', '红娘产能瓶颈', '会员画像不完整'],
        '成功': ['约见转化弱', '缺乏约会教练', '双方家庭阻力'],
    }
    return {'worst_step': name, 'worst_drop': max(0, worst_drop), 'reasons': reasons_map.get(name, ['获客渠道质量波动'])}


if __name__ == '__main__':
    import uvicorn

    # 仅绑定内网地址，公网访问必须经由网关（带鉴权）转发
    logger.info("uvicorn start at %s:%d", BIND_HOST, BIND_PORT)
    uvicorn.run(app, host=BIND_HOST, port=BIND_PORT)
