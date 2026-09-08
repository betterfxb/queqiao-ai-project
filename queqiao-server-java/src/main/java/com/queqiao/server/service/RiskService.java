package com.queqiao.server.service;

import com.queqiao.server.common.BizException;
import com.queqiao.server.common.Ids;
import com.queqiao.server.entity.*;
import com.queqiao.server.repo.*;
import com.queqiao.server.security.RequestContext;
import com.queqiao.server.service.LogService;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 风控引擎：基于规则扫描会员异常行为，生成可查可处置的 RiskEvent。
 * 规则：被举报（≥2 次已受理）/ 退款频繁（≥3 笔退款）/ 短时间多牵线（7 天内 ≥5 次）/
 *      重复注册（同姓名+同城+同岁近似判定）/ 涉钱关键词（会话内容命中诈骗话术）/
 *      高频私信（近 24h 会话私信 ≥30 条疑似轰炸）。
 * 生成事件同时写入操作日志供运营追溯；反诈类高危事件（涉钱关键词/高频私信）命中后
 * 自动建「反诈工单」并附被害人救助引导文案。
 * 注：规则命中后可对接 AutomationRule 自动通知运营（此处以操作日志为统一出口）。
 */
@Service
public class RiskService {

    private final MemberRepository members;
    private final MemberReportRepository reports;
    private final FinanceOrderRepository orders;
    private final PairRepository pairs;
    private final RiskEventRepository risks;
    private final MessageItemRepository messages;
    private final ConversationRepository conversations;
    private final WorkOrderRepository workOrders;
    private final LogService logs;

    /** 被害人救助引导文案（反诈类高危事件通用） */
    private static final String RESCUE_GUIDANCE =
            "1. 立即停止与对方任何资金往来，不要继续转账、购买礼品卡或提供验证码/银行卡密码。\n" +
            "2. 完整保留聊天记录、对方账号与转账凭证截图，作为报警与平台取证材料。\n" +
            "3. 如已转账，第一时间拨打 96110 反诈专线或前往属地反诈中心报警，争取止付冻结。\n" +
            "4. 平台可协助冻结对方账号、阻断继续沟通，并配合公安机关调取证据。\n" +
            "5. 通过官方客服与公安联动通道核实，切勿相信「安全账户」「解冻金」等话术。";

    /** 涉钱/诈骗关键词（命中即纳入话术识别） */
    private static final String[] MONEY_KEYWORDS = {
            "转账", "汇款", "银行卡", "安全账户", "验证码", "垫付", "充值", "投资", "理财",
            "博彩", "彩票", "返利", "刷单", "保证金", "私下交易", "微信换", "解冻", "收益",
            "提现", "刷单返利", "网恋", "内部平台", "带单"
    };

    /** 高危关键词：命中任一即直接升级为高危 */
    private static final Set<String> HIGH_SEVERITY = Set.of(
            "转账", "汇款", "银行卡", "安全账户", "验证码", "垫付", "解冻");

    /** 近 24h 私信频次阈值（疑似轰炸式私聊） */
    private static final int HIGH_FREQ_THRESHOLD = 30;

    /** 单次扫描内新建的工单计数 */
    private int woCreated;

    public RiskService(MemberRepository members, MemberReportRepository reports,
                       FinanceOrderRepository orders, PairRepository pairs,
                       RiskEventRepository risks, MessageItemRepository messages,
                       ConversationRepository conversations, WorkOrderRepository workOrders,
                       LogService logs) {
        this.members = members;
        this.reports = reports;
        this.orders = orders;
        this.pairs = pairs;
        this.risks = risks;
        this.messages = messages;
        this.conversations = conversations;
        this.workOrders = workOrders;
        this.logs = logs;
    }

    /** 全量扫描；幂等：同会员+同类型已存在未处理事件则不重复生成 */
    public Map<String, Object> scan() {
        List<Member> all = members.findAll();
        int created = 0;
        woCreated = 0;
        LocalDateTime cutoff = LocalDateTime.now().minusDays(7);

        // 预加载：会话 -> 会员映射（仅 member 类会话，且其 memberId 非空）
        Map<String, String> convMember = new HashMap<>();
        for (Conversation c : conversations.findAll()) {
            if ("member".equals(c.getKind()) && c.getMemberId() != null) {
                convMember.put(c.getId(), c.getMemberId());
            }
        }
        // 预加载近 7 天消息，按会员分组（用于行为规则）
        Map<String, List<MessageItem>> byMember = new HashMap<>();
        for (MessageItem m : messages.findAll()) {
            if (m.getCreatedAt() == null || m.getCreatedAt().isBefore(cutoff)) continue;
            String mid = convMember.get(m.getConversationId());
            if (mid == null) continue;
            byMember.computeIfAbsent(mid, k -> new ArrayList<>()).add(m);
        }

        for (Member m : all) {
            // 1. 被举报
            long accepted = reports.countByTargetMemberIdAndStatus(m.getId(), "已受理");
            if (accepted >= 2) created += emit(m, "被举报", (int) Math.min(100, 40 + accepted * 10),
                    "近 30 天被受理举报 " + accepted + " 次", cutoff);
            // 2. 退款频繁
            long refunds = orders.findAll().stream()
                    .filter(o -> m.getId().equals(o.getMemberId()) && o.getType() != null && o.getType().contains("退款"))
                    .count();
            if (refunds >= 3) created += emit(m, "退款频繁", (int) Math.min(100, 40 + refunds * 8),
                    "累计退款订单 " + refunds + " 笔", cutoff);
            // 3. 短时间多牵线
            long recent = pairs.findAll().stream()
                    .filter(p -> (m.getId().equals(p.getMaleId()) || m.getId().equals(p.getFemaleId()))
                            && p.getCreatedAt() != null && p.getCreatedAt().isAfter(cutoff))
                    .count();
            if (recent >= 5) created += emit(m, "短时间多牵线", (int) Math.min(100, 30 + recent * 6),
                    "7 天内参与牵线 " + recent + " 次", cutoff);
            // 4. 重复注册（近似判定）
            long dup = all.stream().filter(x -> !x.getId().equals(m.getId())
                            && eq(nz(x.getName()), nz(m.getName()))
                            && eq(nz(x.getCity()), nz(m.getCity()))
                            && x.getAge() != null && x.getAge().equals(m.getAge()))
                    .count();
            if (dup >= 1) created += emit(m, "重复注册", 50,
                    "疑似重复注册账号 " + dup + " 个", cutoff);

            // 5/6. 行为规则：涉钱关键词 / 高频私信（按会话 memberId 归因，被害人视角）
            List<MessageItem> mmsgs = byMember.getOrDefault(m.getId(), List.of());
            created += scanMoney(m, mmsgs);
            created += scanHighFreq(m, mmsgs);
        }
        Map<String, Object> res = new LinkedHashMap<>();
        res.put("scanned", all.size());
        res.put("created", created);
        res.put("workOrders", woCreated);
        return res;
    }

    /** 涉钱关键词：命中 ≥2 个不同关键词，或命中任一高危关键词，即升级为反诈高危 */
    private int scanMoney(Member m, List<MessageItem> msgs) {
        Set<String> hits = new LinkedHashSet<>();
        for (MessageItem msg : msgs) {
            String c = msg.getContent() == null ? "" : msg.getContent();
            for (String kw : MONEY_KEYWORDS) {
                if (c.contains(kw)) hits.add(kw);
            }
        }
        boolean highSev = hits.stream().anyMatch(HIGH_SEVERITY::contains);
        if (hits.size() >= 2 || highSev) {
            int score = highSev ? 95 : 85;
            String detail = "会话内容命中涉钱关键词：" + String.join("、", hits);
            return emit(m, "涉钱关键词", score, detail, RESCUE_GUIDANCE, true);
        }
        return 0;
    }

    /** 高频私信：近 24h 会话私信 ≥ 阈值，疑似轰炸式私聊 */
    private int scanHighFreq(Member m, List<MessageItem> msgs) {
        long recent = msgs.stream()
                .filter(x -> x.getCreatedAt() != null && x.getCreatedAt().isAfter(LocalDateTime.now().minusHours(24)))
                .count();
        if (recent >= HIGH_FREQ_THRESHOLD) {
            String detail = "近 24 小时会话私信 " + recent + " 条，疑似高频私信轰炸";
            return emit(m, "高频私信", 80, detail, RESCUE_GUIDANCE, true);
        }
        return 0;
    }

    /** 通用建单（含可选的救助引导文案与自动建工单） */
    private int emit(Member m, String type, int score, String detail, String guidance, boolean createWO) {
        boolean exists = risks.findByMemberIdAndType(m.getId(), type).stream()
                .anyMatch(e -> !Boolean.TRUE.equals(e.getHandled()));
        if (exists) return 0;
        RiskEvent e = new RiskEvent();
        e.setId(Ids.biz("RK"));
        e.setMemberId(m.getId());
        e.setMemberName(m.getName());
        e.setType(type);
        e.setScore(score);
        e.setDetail(detail);
        e.setGuidance(guidance);
        e.setHandled(false);
        e.setCreatedAt(LocalDateTime.now());
        risks.save(e);
        logs.record("风险预警", m.getId(), type + "：" + detail);
        if (createWO) {
            createWorkOrder(m, type, score, detail, guidance, e.getId());
        }
        return 1;
    }

    /** 旧规则兼容：无救助引导、不自动建单 */
    private int emit(Member m, String type, int score, String detail, LocalDateTime cutoff) {
        return emit(m, type, score, detail, null, false);
    }

    /** 高危自动建「反诈工单」（幂等：同会员+同风险类型已有待处理工单则跳过） */
    private void createWorkOrder(Member m, String riskType, int score, String detail, String guidance, String riskId) {
        boolean exists = workOrders.findByRiskTypeAndStatus(riskType, "待处理").stream()
                .anyMatch(w -> w.getMemberId() != null && w.getMemberId().equals(m.getId()));
        if (exists) return;
        WorkOrder w = new WorkOrder();
        w.setId(Ids.biz("WO"));
        w.setTitle("反诈核查：" + (m.getName() == null ? m.getId() : m.getName()) + " " + riskType);
        w.setMemberId(m.getId());
        w.setMemberName(m.getName());
        w.setType("反诈核查");
        w.setRiskType(riskType);
        w.setStatus("待处理");
        w.setPriority(score >= 90 ? "高" : "中");
        w.setRelatedRiskId(riskId);
        w.setGuidance(guidance);
        w.setDetail(detail);
        w.setCreatedAt(LocalDateTime.now());
        workOrders.save(w);
        woCreated++;
        logs.record("反诈建单", m.getId(), w.getTitle());
    }

    public List<RiskEvent> list(Boolean handled, String type, String memberId) {
        List<RiskEvent> all = risks.findAll();
        return all.stream()
                .filter(e -> handled == null || handled.equals(Boolean.TRUE.equals(e.getHandled())))
                .filter(e -> type == null || type.isBlank() || type.equals(e.getType()))
                .filter(e -> memberId == null || memberId.isBlank() || memberId.equals(e.getMemberId()))
                .sorted((a, b) -> b.getCreatedAt() == null ? 1 : a.getCreatedAt() == null ? -1
                        : b.getCreatedAt().compareTo(a.getCreatedAt()))
                .collect(Collectors.toList());
    }

    public RiskEvent handle(String id, String handler, String note) {
        RiskEvent e = risks.findById(id).orElseThrow(() -> BizException.notFound("风险事件"));
        e.setHandled(true);
        e.setHandler(handler);
        e.setHandleNote(note);
        e.setHandledAt(LocalDateTime.now());
        risks.save(e);
        logs.record("处置风险事件", id, e.getType() + (note == null ? "" : " · " + note));
        return e;
    }

    public Map<String, Object> summary() {
        List<RiskEvent> all = risks.findAll();
        long unhandled = all.stream().filter(e -> !Boolean.TRUE.equals(e.getHandled())).count();
        Map<String, Long> byType = new LinkedHashMap<>();
        for (RiskEvent e : all) {
            byType.merge(e.getType() == null ? "其他" : e.getType(), 1L, Long::sum);
        }
        Map<String, Object> res = new LinkedHashMap<>();
        res.put("total", all.size());
        res.put("unhandled", unhandled);
        res.put("byType", byType);
        return res;
    }

    // —— 反诈工单 ——

    public List<WorkOrder> listWorkOrders(String status, String riskType) {
        List<WorkOrder> all = workOrders.findAll();
        return all.stream()
                .filter(w -> status == null || status.isBlank() || status.equals(w.getStatus()))
                .filter(w -> riskType == null || riskType.isBlank() || riskType.equals(w.getRiskType()))
                .sorted((a, b) -> b.getCreatedAt() == null ? 1 : a.getCreatedAt() == null ? -1
                        : b.getCreatedAt().compareTo(a.getCreatedAt()))
                .collect(Collectors.toList());
    }

    public WorkOrder handleWorkOrder(String id, String status, String note) {
        WorkOrder w = workOrders.findById(id).orElseThrow(() -> BizException.notFound("工单"));
        w.setStatus(status);
        w.setHandler(me());
        w.setHandleNote(note);
        w.setHandledAt(LocalDateTime.now());
        workOrders.save(w);
        logs.record("处置反诈工单", id, w.getTitle() + (note == null ? "" : " · " + note));
        return w;
    }

    public Map<String, Object> workOrderSummary() {
        List<WorkOrder> all = workOrders.findAll();
        long open = all.stream().filter(w -> !"已结案".equals(w.getStatus())).count();
        Map<String, Long> byRisk = new LinkedHashMap<>();
        for (WorkOrder w : all) {
            byRisk.merge(w.getRiskType() == null ? "其他" : w.getRiskType(), 1L, Long::sum);
        }
        Map<String, Object> res = new LinkedHashMap<>();
        res.put("total", all.size());
        res.put("open", open);
        res.put("byRiskType", byRisk);
        return res;
    }

    private static String me() {
        var c = RequestContext.get();
        return c == null ? "系统" : c.userName();
    }

    private static boolean eq(String a, String b) {
        return a != null && b != null && a.equals(b);
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }
}
