# -*- coding: utf-8 -*-
"""
鹊桥·红娘工作台 — 红娘 Web 端操作手册 PDF 生成器
数据来源：queqiao-admin 前端源码静态分析（路由 / 视图 / 导航 / 权限）
依赖：reportlab + pymupdf（均隔离安装），中文字体使用系统微软雅黑
"""
import sys, os
from reportlab.lib.pagesizes import A4
from reportlab.lib.units import mm
from reportlab.lib import colors
from reportlab.lib.styles import getSampleStyleSheet, ParagraphStyle
from reportlab.lib.enums import TA_CENTER, TA_LEFT, TA_JUSTIFY
from reportlab.platypus import (
    SimpleDocTemplate, Paragraph, Spacer, Table, TableStyle,
    PageBreak, ListFlowable, ListItem, HRFlowable
)
from reportlab.pdfbase import pdfmetrics
from reportlab.pdfbase.ttfonts import TTFont
import fitz

# ---------- 中文字体 ----------
FONT_DIR = r"C:/Windows/Fonts"
pdfmetrics.registerFont(TTFont("YaHei", os.path.join(FONT_DIR, "msyh.ttc")))
pdfmetrics.registerFont(TTFont("YaHei-Bold", os.path.join(FONT_DIR, "msyhbd.ttc")))
CN = "YaHei"
CNB = "YaHei-Bold"

# ---------- 品牌色 ----------
BURGUNDY = colors.HexColor("#8B254A")
GOLD = colors.HexColor("#D4AF37")
INK = colors.HexColor("#222222")
SUB = colors.HexColor("#666666")
LIGHT = colors.HexColor("#F7F1F4")
LINE = colors.HexColor("#E3D5DC")

OUT = r"d:/ai_project/queqiao-admin/红娘Web端操作手册.pdf"
COVER = r"d:/ai_project/queqiao-admin/_cover.pdf"
BODY = r"d:/ai_project/queqiao-admin/_body.pdf"

# ---------- 样式 ----------
ss = getSampleStyleSheet()
def S(name, **kw):
    base = kw.pop("parent", ss["Normal"])
    kw.setdefault("fontName", CN)
    return ParagraphStyle(name, parent=base, **kw)

title_style = S("title", fontName=CNB, fontSize=26, textColor=colors.white, leading=32, alignment=TA_CENTER)
subtitle_style = S("subtitle", fontName=CN, fontSize=13, textColor=colors.white, leading=20, alignment=TA_CENTER)
h1 = S("h1", fontName=CNB, fontSize=16, textColor=BURGUNDY, leading=22, spaceBefore=14, spaceAfter=8)
h2 = S("h2", fontName=CNB, fontSize=12.5, textColor=INK, leading=18, spaceBefore=10, spaceAfter=5)
body = S("body", fontSize=10, textColor=INK, leading=17, alignment=TA_JUSTIFY, spaceAfter=5)
bullet = S("bullet", fontSize=10, textColor=INK, leading=16)
note = S("note", fontSize=9, textColor=SUB, leading=14, spaceAfter=4)
cell = S("cell", fontSize=9, textColor=INK, leading=13)
cellb = S("cellb", fontSize=9, textColor=colors.white, leading=13, fontName=CNB)
toc_item = S("toc_item", fontSize=11, textColor=INK, leading=22)

def para(t, st=body): return Paragraph(t, st)

# ---------- 工具 ----------
def kv_table(rows, col1=40*mm, header=None):
    data = []
    if header:
        data.append([Paragraph(header[0], cellb), Paragraph(header[1], cellb)])
    for k, v in rows:
        data.append([Paragraph(k, S("k", fontSize=9, fontName=CNB, textColor=BURGUNDY, leading=13)),
                     Paragraph(v, cell)])
    t = Table(data, colWidths=[col1, 150*mm-col1])
    t.setStyle(TableStyle([
        ("BACKGROUND",(0,0),(-1,0),BURGUNDY),
        ("GRID",(0,0),(-1,-1),0.5,LINE),
        ("VALIGN",(0,0),(-1,-1),"TOP"),
        ("TOPPADDING",(0,0),(-1,-1),5),("BOTTOMPADDING",(0,0),(-1,-1),5),
        ("LEFTPADDING",(0,0),(-1,-1),7),("RIGHTPADDING",(0,0),(-1,-1),7),
        ("ROWBACKGROUNDS",(0,1),(-1,-1),[colors.white, LIGHT]),
    ]))
    return t

def steps(items):
    flow = []
    for i, (t, d) in enumerate(items, 1):
        flow.append(ListItem(Paragraph(f"<b>步骤 {i}：{t}</b><br/>{d}", bullet), value=i))
    return ListFlowable(flow, bulletType="1", leftIndent=14, bulletFontName=CNB, bulletColor=BURGUNDY)

def bullets(items):
    flow = [ListItem(Paragraph(x, bullet)) for x in items]
    return ListFlowable(flow, bulletType="bullet", leftIndent=12, bulletColor=GOLD)

def header_footer(c, d):
    c.saveState()
    c.setFillColor(BURGUNDY)
    c.rect(0, A4[1]-16*mm, A4[0], 16*mm, fill=1, stroke=0)
    c.setFillColor(colors.white); c.setFont(CNB, 9)
    c.drawString(20*mm, A4[1]-10.5*mm, "鹊桥·红娘工作台 — 红娘 Web 端操作手册")
    c.setFont(CN, 8)
    c.drawRightString(A4[0]-20*mm, A4[1]-10.5*mm, "运营中心 · 内部资料")
    c.setStrokeColor(LINE); c.setLineWidth(0.6)
    c.line(20*mm, 14*mm, A4[0]-20*mm, 14*mm)
    c.setFillColor(SUB); c.setFont(CN, 8)
    c.drawString(20*mm, 9*mm, "© 鹊桥·红娘工作台")
    c.drawRightString(A4[0]-20*mm, 9*mm, "第 %d 页" % c.getPageNumber())
    c.restoreState()

# ---------- 封面内容 ----------
cover = []
cover.append(Spacer(1, 52*mm))
cover.append(Paragraph("鹊桥·红娘工作台", title_style))
cover.append(Spacer(1, 6*mm))
cover.append(Paragraph("红娘 Web 端 · 操作手册", subtitle_style))
cover.append(Spacer(1, 4*mm))
cover.append(Paragraph("牵线运营中心 · 用户指南", subtitle_style))
cover.append(Spacer(1, 62*mm))
cover.append(Paragraph("版本 V1.0　|　面向角色：红娘 / 经理 / 管理员",
                       S("cv", fontName=CN, fontSize=10.5, textColor=colors.white, alignment=TA_CENTER, leading=16)))
cover.append(Paragraph("编制日期：2026 年 8 月",
                       S("cv2", fontName=CN, fontSize=10.5, textColor=colors.white, alignment=TA_CENTER, leading=16)))

def draw_cover_background(c, d):
    c.saveState()
    c.setFillColor(BURGUNDY)
    c.rect(0, 0, A4[0], A4[1], fill=1, stroke=0)
    c.setFillColor(GOLD)
    c.rect(0, A4[1]-38*mm, A4[0], 3*mm, fill=1, stroke=0)
    c.rect(0, 36*mm, A4[0], 3*mm, fill=1, stroke=0)
    c.restoreState()

# ---------- 正文内容 ----------
story = []

# 目录
story.append(Paragraph("目　录", h1))
toc = [
    ("一、系统概述", "1"),
    ("二、登录与工作台首页", "2"),
    ("三、角色与权限说明", "4"),
    ("四、核心业务流程总览", "6"),
    ("五、会员管理", "8"),
    ("六、会员档案录入与编辑", "11"),
    ("七、牵线管理（撮合）", "14"),
    ("八、审核中心", "17"),
    ("九、红娘团队管理", "19"),
    ("十、客户跟进 CRM", "21"),
    ("十一、活动、日程与消息", "23"),
    ("十二、财务、支付与佣金", "25"),
    ("十三、信任合规、产品与资金存管", "27"),
    ("十四、报表导出、智能运营与增长", "29"),
    ("十五、积分、情感测评与系统设置", "31"),
    ("十六、常见问题与操作贴士", "33"),
]
for name, pg in toc:
    t = Table([[Paragraph(name, toc_item), Paragraph(pg, S("pg", fontSize=11, textColor=SUB, alignment=TA_CENTER))]],
              colWidths=[150*mm, 20*mm])
    t.setStyle(TableStyle([
        ("LINEBELOW",(0,0),(-1,-1),0.4,LINE),
        ("VALIGN",(0,0),(-1,-1),"MIDDLE"),
        ("TOPPADDING",(0,0),(-1,-1),4),("BOTTOMPADDING",(0,0),(-1,-1),4),
    ]))
    story.append(t)
story.append(PageBreak())

# 章节页辅助
def section(num, name, intro):
    story.append(Paragraph(f"{num}　{name}", h1))
    if intro:
        story.append(para(intro))
    story.append(HRFlowable(width="100%", thickness=1, color=GOLD, spaceBefore=2, spaceAfter=8))

# 一、系统概述
section("一、", "系统概述", "鹊桥·红娘工作台是面向婚恋红娘群体的全栈运营系统。红娘 Web 端（牵线运营中心）是红娘与运营人员在电脑浏览器上使用的管理后台，承载会员资料管理、智能撮合、审核认证、客户跟进、财务结算等核心工作。本手册基于 Web 端实际功能模块编写，帮助红娘快速上手日常操作。")
story.append(kv_table([
    ("系统名称", "鹊桥·红娘工作台 — 红娘 Web 端（牵线运营中心）"),
    ("访问方式", "浏览器访问运营中心地址，使用账号密码登录（路由 /login）"),
    ("技术形态", "Vue3 + TypeScript + Element Plus 单页应用，数据均与后端实时接口联动，无本地假数据"),
    ("主要角色", "红娘（AGENT）、经理（MANAGER）、管理员（ADMIN）"),
    ("核心目标", "高效完成会员建档、审核、智能匹配、牵线跟进直至成交结算的闭环"),
], header=("项目","说明")))
story.append(Spacer(1,4))
story.append(para("提示：本手册所述功能均对应 Web 端真实页面；部分高级管理功能仅对经理/管理员开放，详见第三章权限说明。", note))

# 二、登录与工作台首页
section("二、", "登录与工作台首页", "首次使用请在浏览器打开运营中心地址，进入登录页（/login）输入账号密码登录。登录成功后默认进入仪表盘（/），即工作台首页。")
story.append(Paragraph("2.1　登录", h2))
story.append(steps([
    ("打开登录页", "在浏览器地址栏输入运营中心网址，系统自动跳转至 /login 登录页。"),
    ("输入凭证", "填写管理员分配的账号与密码，勾选必要的登录协议后点击「登录」。"),
    ("进入系统", "登录成功后系统跳转至仪表盘首页；如账号无权限会提示联系管理员。"),
]))
story.append(Paragraph("2.2　工作台首页（仪表盘）", h2))
story.append(para("仪表盘是每日工作的起点，集中呈现关键指标与待办，帮助红娘一眼掌握当天重点："))
story.append(bullets([
    "<b>KPI 概览</b>：会员总数、本月新增、牵线成功数、待跟进数等核心指标卡片。",
    "<b>最近会员</b>：最新录入或活跃的会员列表，可点击快速进入档案。",
    "<b>待办看板</b>：待审核资料、待推进牵线、逾期跟进等任务聚合。",
    "<b>智能推荐入口</b>：系统根据当前会员自动推荐可匹配的候选对象。",
]))
story.append(para("建议每日开工先浏览仪表盘，按待办优先级安排当日工作。", note))

# 三、角色与权限
section("三、", "角色与权限说明", "系统区分三类角色，菜单对所有登录用户可见，但实际可操作范围由按钮级权限控制。下表列出各角色的典型操作边界。")
story.append(kv_table([
    ("红娘（AGENT）", "日常操作主力：会员管理、档案编辑、牵线撮合、审核、CRM 跟进、消息收发等。"),
    ("经理（MANAGER）", "在红娘权限基础上，可查看团队报表、进行佣金结算中的管理操作、分配会员。"),
    ("管理员（ADMIN）", "拥有全部权限，额外负责增长中心（渠道/合伙人/下线管理）、支付中心管理等系统级设置。"),
], header=("角色","权限范围")))
story.append(Spacer(1,4))
story.append(para("说明：导航菜单会展示全部模块，但无权限的按钮会处于禁用（置灰）状态。若某功能点击无响应或显示无权限，请联系管理员确认角色配置。", note))

# 四、核心业务流程
section("四、", "核心业务流程总览", "红娘的日常工作在 Web 端形成一条清晰闭环。理解以下主线，有助于在各模块间高效切换。")
story.append(steps([
    ("录入会员资料", "在「会员管理」新建会员，填写基本信息、择偶偏好、服务信息（第六章）。"),
    ("审核与认证", "资料提交后进入「审核中心」，由审核角色通过或驳回（第八章）。"),
    ("智能匹配", "在「牵线管理」用 AI 匹配推荐候选对象，查看匹配度与维度分（第七章）。"),
    ("建立牵线", "为双方发起牵线，进入阶段推进流程，安排约见与催进提醒。"),
    ("CRM 跟进", "在「客户跟进」用看板记录每次沟通，借助 AI 下一步建议推动转化。"),
    ("成交与结算", "成交后由财务/佣金模块完成收款与佣金结算（第十二章）。"),
]))
story.append(Spacer(1,4))
story.append(para("后续章节按模块逐一展开具体操作。", note))

# 五、会员管理
section("五、", "会员管理", "会员管理（/members）是红娘最高频的模块，用于查看、搜索、筛选、新建与维护全部会员档案。")
story.append(Paragraph("5.1　列表与筛选", h2))
story.append(bullets([
    "<b>快捷筛选 chips</b>：按性别、年龄、等级、状态、所属红娘一键筛选。",
    "<b>高级筛选</b>：展开面板按身高、收入、婚况、房车等条件精细过滤。",
    "<b>关键词搜索</b>：输入姓名/电话实时防抖搜索；支持按最近活跃、最新注册、牵线次数排序。",
    "<b>分页浏览</b>：列表分页展示，可调整每页条数。",
]))
story.append(Paragraph("5.2　单行操作", h2))
story.append(para("鼠标悬停或点击列表行命令菜单，可执行以下操作："))
story.append(kv_table([
    ("新建会员", "点击「新建」打开档案编辑页（见第六章）。"),
    ("编辑", "进入会员档案编辑页修改资料。"),
    ("发起牵线", "直接进入牵线管理为该会员推荐并建线。"),
    ("分配红娘", "将会员指派给指定红娘（团队场景）。"),
    ("认证 / 暂停 / 恢复", "切换会员认证状态或暂停、恢复其服务。"),
    ("删除", "移除会员（谨慎操作，通常建议先暂停）。"),
    ("导出", "将当前筛选结果导出为表格文件（exportMembers）。"),
], header=("操作","说明")))
story.append(Paragraph("5.3　快速查看", h2))
story.append(para("点击列表项可在右侧抽屉快速预览会员关键信息，无需离开列表页即可判断下一步动作。", body))

# 六、会员档案
section("六、", "会员档案录入与编辑", "会员档案编辑页（/members/edit/:id）分为三块表单，结构清晰、便于完整采集会员信息。新建时 id 可缺省，保存即创建。")
story.append(Paragraph("6.1　基本信息", h2))
story.append(kv_table([
    ("姓名 / 性别 / 年龄", "会员基础身份字段，必填。"),
    ("身高 / 学历 / 职业", "影响匹配维度的关键硬性条件。"),
    ("收入 / 城市 / 籍贯", "经济与地域信息，城市支持省—市—区/县三级联动。"),
    ("婚况 / 子女 / 房车", "婚恋状态相关字段，直接用于匹配过滤。"),
], header=("字段组","说明")))
story.append(Paragraph("6.2　择偶偏好", h2))
story.append(bullets([
    "期望年龄区间、身高区间：用于系统自动筛除不匹配候选。",
    "期望学历、期望城市：地域与硬条件偏好。",
    "其他要求：自由文本补充，供红娘人工判断。",
]))
story.append(Paragraph("6.3　服务信息", h2))
story.append(bullets([
    "会员等级、服务红娘、服务状态：决定资源分配与跟进优先级。",
    "联系电话、标签：便于联系与分类检索（标签支持自定义）。",
]))
story.append(Paragraph("6.4　保存与提交", h2))
story.append(steps([
    ("填写三块表单", "依次完成基本信息、择偶偏好、服务信息。"),
    ("校验提交", "系统对必填与格式做前端校验，确认无误后保存。"),
    ("进入审核", "新会员资料通常需经审核中心认证后方可正式参与匹配。"),
]))
story.append(para("城市字段支持三级联动：选择省份后联动市级，再联动区/县；东莞、中山等不设区市自动以市名作区/县占位，避免无法提交。", note))

# 七、牵线管理
section("七、", "牵线管理（撮合）", "牵线管理（/match）是撮合核心模块，红娘在此为会员寻找合适对象并推进关系发展。支持从会员详情携带参数直接进入（/match?member=）。")
story.append(Paragraph("7.1　界面与匹配", h2))
story.append(bullets([
    "<b>配对切换</b>：在两侧选择两位会员作为撮合对象。",
    "<b>AI 匹配度与维度分</b>：系统给出综合匹配度及各维度评分（如价值观、生活习惯等）。",
    "<b>解释型匹配报告</b>：不仅给分数，还说明匹配/不匹配的依据，辅助红娘决策。",
    "<b>智能推荐建线</b>：基于当前会员一键推荐高匹配候选并快速发起牵线。",
]))
story.append(Paragraph("7.2　阶段推进", h2))
story.append(steps([
    ("查看阶段 stepper", "牵线按阶段划分，stepper 可点击查看/推进当前所处阶段。"),
    ("双方资料卡", "同一页面并排展示双方档案，方便对比与向用户介绍。"),
    ("AI 沟通建议 / 约会教练", "系统提供话术建议与约会安排参考，降低红娘沟通成本。"),
    ("安排约见与催进提醒", "记录约见安排，对停滞牵线发送催进提醒。"),
    ("记录反馈 / 标记终止", "每次互动记录反馈；无果可标记终止并填写原因。"),
]))
story.append(para("牵线时间线会按操作自动沉淀，便于回溯全过程与复盘。", note))

# 八、审核中心
section("八、", "审核中心", "审核中心（/audit）负责会员资料与真实性的认证把关，是保障平台信任的关键环节。")
story.append(Paragraph("8.1　审核队列", h2))
story.append(bullets([
    "顶部 KPI 展示待审数量与今日已审量。",
    "审核队列列出待处理会员，点击查看资料详情。",
    "AI 审核建议：系统对证件清晰度、一致性给出预审提示，提升效率。",
]))
story.append(Paragraph("8.2　审核操作", h2))
story.append(kv_table([
    ("通过审核", "资料真实合规，认证会员，使其可参与匹配与展示。"),
    ("驳回", "选择驳回原因（如证件模糊、证件过期、非本人证件等），退回补充。"),
], header=("操作","说明")))
story.append(para("审核需严谨：驳回时应选明确原因，方便会员针对性补正，减少反复提交。", note))

# 九、红娘团队管理
section("九、", "红娘团队管理", "红娘团队（/agents）用于管理红娘成员与会员分配，适合多红娘协作的运营场景。")
story.append(bullets([
    "<b>新建红娘</b>：录入红娘账号与基本信息，纳入团队管理。",
    "<b>智能分配会员</b>：系统按负载/专长将待分配会员推荐给合适红娘。",
    "<b>查看详情与报表</b>：查看单个红娘的业绩、负责会员与跟进情况。",
]))
story.append(para("经理与管理员常用此模块做资源调度；红娘本人通常仅查看自己的被分配情况。", note))

# 十、CRM
section("十、", "客户跟进 CRM", "客户跟进（/crm）以看板形式管理会员跟进过程，帮助红娘把控每个客户的转化节奏。")
story.append(Paragraph("10.1　六列看板", h2))
story.append(para("看板按客户阶段分为六列，拖拽卡片即可变更阶段，直观反映 pipeline 状态。"))
story.append(Paragraph("10.2　跟进记录与 AI 辅助", h2))
story.append(bullets([
    "<b>记录跟进</b>：每次沟通（电话、微信、约见）填写跟进记录，沉淀客户历史。",
    "<b>AI 下一步建议</b>：系统根据当前阶段与客户特征，建议最合理的下一步动作。",
    "<b>转化漏斗</b>：可视化呈现各阶段转化率，定位流失环节。",
]))
story.append(para("CRM 与牵线管理互补：牵线关注「配对关系」，CRM 关注「单客户转化」。", note))

# 十一、活动日程消息
section("十一、", "活动、日程与消息", "这三个模块围绕红娘的日常协同与触达。")
story.append(kv_table([
    ("活动（/activities）", "策划与发布线下相亲会、主题沙龙等活动，管理报名与现场。"),
    ("日程（/calendar）", "日历视图安排约见、活动与跟进提醒，避免遗漏重要节点。"),
    ("消息（/messages）", "站内消息中心，红娘间及与会员的沟通渠道，未读以角标提示。"),
], header=("模块","用途")))
story.append(Spacer(1,4))
story.append(para("建议将每次约见与重要跟进同步到日程，开启消息提醒，确保及时响应。", note))

# 十二、财务支付佣金
section("十二、", "财务、支付与佣金", "成交之后的资金与激励由以下模块支撑，主要由经理/管理员操作，红娘可查看相关进度。")
story.append(kv_table([
    ("财务（/finance）", "财务报表总览，查看营收、收款与对账数据。"),
    ("支付中心（/pay）", "支付管理（支付渠道与订单），仅管理员可管理配置。"),
    ("佣金（/commission）", "佣金结算，经理/管理员可执行结算管理，红娘查看应得佣金。"),
], header=("模块","用途")))
story.append(para("涉及资金的操作须谨慎，权限受角色严格控制；红娘如对佣金有疑问，可在本模块查看明细并联系经理核对。", note))

# 十三、信任合规产品存管
section("十三、", "信任合规、产品与资金存管", "这组模块保障平台合规与商业化运转。")
story.append(kv_table([
    ("信任合规（/trust）", "管理实名认证、风控与合规策略，是平台信任基石。"),
    ("服务产品（/products）", "配置可售卖的红娘服务套餐与权益，支撑商业化。"),
    ("资金存管（/escrow）", "资金第三方存管相关管理，保障交易资金安全。"),
], header=("模块","用途")))
story.append(para("以上模块的变更会影响全局业务规则，通常由管理员在充分评估后操作。", note))

# 十四、报表智能运营增长
section("十四、", "报表导出、智能运营与增长", "面向管理与运营分析的模块。")
story.append(kv_table([
    ("报表导出（/reports）", "模板化报表，支持预览后导出，便于周报/月报与上级汇报。"),
    ("智能运营（/smart-ops）", "运营自动化与策略工具，提升撮合与触达效率。"),
    ("增长中心（/growth）", "渠道、合伙人、商户与下线管理，仅管理员可新增与发奖。"),
], header=("模块","用途")))
story.append(para("红娘常用「报表导出」生成自身业绩报表；增长相关管理操作需管理员权限。", note))

# 十五、积分测评设置
section("十五、", "积分、情感测评与系统设置", "辅助激励与个性化模块。")
story.append(kv_table([
    ("积分榜（/points）", "红娘业绩积分排行，激励良性竞争。"),
    ("情感测评（/members/quiz）", "会员情感/性格测评工具，丰富档案维度，辅助匹配。"),
    ("系统设置（/settings）", "个人与系统级配置，如通知偏好、账号安全等。"),
], header=("模块","用途")))
story.append(para("鼓励红娘引导会员完成情感测评，可显著提升匹配精准度与成交率。", note))

# 十六、常见问题
section("十六、", "常见问题与操作贴士", None)
story.append(Paragraph("常见问题", h2))
story.append(kv_table([
    ("登录后空白/无菜单？", "确认账号已分配角色；联系管理员检查权限配置。"),
    ("某按钮点不动？", "多为角色权限限制（置灰），需经理/管理员授权。"),
    ("城市选不到区/县？", "东莞、中山等不设区市以市名作占位区/县，正常提交即可。"),
    ("资料提交后去哪了？", "进入审核中心排队，通过认证后才参与匹配。"),
    ("导出没反应？", "确认当前有筛选结果且浏览器允许下载弹窗。"),
], header=("问题","处理方式")))
story.append(Paragraph("操作贴士", h2))
story.append(bullets([
    "每日先查仪表盘待办，按优先级处理审核与跟进。",
    "善用高级筛选与标签，把会员分层管理，提升撮合效率。",
    "牵线与 CRM 双线记录，既管「配对」又管「转化」。",
    "重要约见同步到日程并设置提醒，避免爽约。",
    "引导会员补全测评与认证，匹配更准、信任更高。",
]))
story.append(Spacer(1,6))
story.append(HRFlowable(width="100%", thickness=1, color=GOLD, spaceAfter=6))
story.append(para("—— 本手册依据鹊桥·红娘工作台 Web 端（牵线运营中心）实际功能模块编写，如系统升级导致界面差异，请以线上最新版本为准。——", note))

# ---------- 生成封面 PDF ----------
cover_doc = SimpleDocTemplate(COVER, pagesize=A4,
                              leftMargin=20*mm, rightMargin=20*mm,
                              topMargin=22*mm, bottomMargin=18*mm)
cover_doc.build(cover, onFirstPage=draw_cover_background)

# ---------- 生成正文 PDF ----------
body_doc = SimpleDocTemplate(BODY, pagesize=A4,
                             leftMargin=20*mm, rightMargin=20*mm,
                             topMargin=28*mm, bottomMargin=22*mm)
body_doc.build(story, onFirstPage=header_footer, onLaterPages=header_footer)

# ---------- 合并 PDF ----------
merged = fitz.open()
for p in [COVER, BODY]:
    with fitz.open(p) as tmp:
        merged.insert_pdf(tmp)
merged.save(OUT)
merged.close()

for p in [COVER, BODY]:
    try:
        os.remove(p)
    except Exception:
        pass

print("PDF 已生成:", OUT, os.path.getsize(OUT), "字节")
