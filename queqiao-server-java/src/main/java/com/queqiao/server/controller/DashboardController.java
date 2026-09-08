package com.queqiao.server.controller;

import com.queqiao.server.common.Money;
import com.queqiao.server.entity.*;
import com.queqiao.server.repo.*;
import com.queqiao.server.security.RequestContext;
import com.queqiao.server.service.MatchService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

/** 工作台看板（对齐前端 dashboard/types.ts） */
@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {

    private final MemberRepository members;
    private final PairRepository pairs;
    private final AuditTaskRepository audits;
    private final FinanceOrderRepository orders;
    private final CalendarEventRepository events;
    private final CrmLeadRepository leads;
    private final MatchService matchService;
    private final java.util.concurrent.Executor aiExecutor;

    public DashboardController(MemberRepository members, PairRepository pairs, AuditTaskRepository audits,
                               FinanceOrderRepository orders, CalendarEventRepository events,
                               CrmLeadRepository leads, MatchService matchService,
                               @org.springframework.beans.factory.annotation.Qualifier("aiExecutor")
                               java.util.concurrent.Executor aiExecutor) {
        this.aiExecutor = aiExecutor;
        this.members = members;
        this.pairs = pairs;
        this.audits = audits;
        this.orders = orders;
        this.events = events;
        this.leads = leads;
        this.matchService = matchService;
    }

    @GetMapping
    public Map<String, Object> board() {
        Map<String, Object> res = new LinkedHashMap<>();
        res.put("kpis", kpis());
        res.put("recentMembers", scopedMembers().stream()
                .sorted(Comparator.comparing(Member::getCreatedAt, Comparator.nullsLast(Comparator.naturalOrder())).reversed())
                .limit(4).toList());
        res.put("kanban", kanban());
        res.put("todos", todos());
        res.put("recs", recs());
        return res;
    }

    /** KPI 卡片（红娘视角自动收敛为本人负责的会员/牵线/订单/待审） */
    @GetMapping("/kpis")
    public List<Map<String, Object>> kpis() {
        List<Member> myMembers = scopedMembers();
        LocalDateTime monthStart = LocalDateTime.now().withDayOfMonth(1).withHour(0).withMinute(0);
        long total = myMembers.size();
        long newMembers = myMembers.stream()
                .filter(m -> m.getCreatedAt() != null && m.getCreatedAt().isAfter(monthStart))
                .count();

        List<Pair> myPairs = scopedPairs();
        long allPairs = Math.max(1, myPairs.size());
        long success = myPairs.stream().filter(p -> "已成功".equals(p.getStatus())).count();
        long rate = Math.round(success * 100.0 / allPairs);

        BigDecimal revenue = Money.sum(scopedOrders().stream()
                .filter(o -> o.getCreatedAt() != null && o.getCreatedAt().isAfter(monthStart))
                .filter(o -> "已完成".equals(o.getStatus()))
                .toList(), FinanceOrder::getAmount);

        long pending = scopedAudits().stream().filter(a -> "待审核".equals(a.getStatus())).count();

        return List.of(
                kpi("总会员数", String.format("%,d", total), "人",
                        (newMembers >= 0 ? "+" : "") + newMembers + " 本月", true, "users", "gold"),
                kpi("牵线成功率", String.valueOf(rate), "%",
                        "共 " + allPairs + " 组牵线", rate >= 50, "heart", "burgundy"),
                kpi("本月业绩", "¥" + String.format("%,.0f", Money.toDouble(revenue)), "",
                        "已完成订单合计", Money.gtZero(revenue), "dollar", "gold"),
                kpi("待处理审核", String.valueOf(pending), "条",
                        pending > 0 ? "需尽快处理" : "已清空", pending == 0, "file-text", "warning")
        );
    }

    /** 牵线看板三列 */
    @GetMapping("/kanban")
    public List<Map<String, Object>> kanban() {
        Map<String, List<Pair>> grouped = new LinkedHashMap<>();
        grouped.put("牵线中", new ArrayList<>());
        grouped.put("待反馈", new ArrayList<>());
        grouped.put("已约见", new ArrayList<>());
        for (Pair p : scopedPairs()) {
            String st = p.getStatus() == null ? "" : p.getStatus();
            String col = switch (st) {
                case "已约见", "进行中" -> "已约见";
                case "待确认" -> "待反馈";
                case "双方同意" -> "牵线中";
                default -> null;
            };
            if (col != null) grouped.get(col).add(p);
        }
        List<Map<String, Object>> cols = new ArrayList<>();
        grouped.forEach((title, list) -> {
            List<Map<String, Object>> cards = list.stream().limit(4).map(this::toCard).toList();
            cols.add(Map.of("title", title, "count", list.size(), "cards", cards));
        });
        return cols;
    }

    /** 今日待办 */
    @GetMapping("/todos")
    public List<Map<String, Object>> todos() {
        List<Map<String, Object>> list = new ArrayList<>();
        long pendingAudit = scopedAudits().stream().filter(a -> "待审核".equals(a.getStatus())).count();
        if (pendingAudit > 0) {
            list.add(Map.of("text", "审核新会员资料（" + pendingAudit + " 份）", "priority", "high",
                    "link", "/audit"));
        }
        var today = scopedEvents().stream()
                .filter(e -> e.getDate() != null && e.getDate().equals(LocalDate.now()))
                .sorted(Comparator.comparing(e -> e.getStartTime() == null ? "" : e.getStartTime()))
                .limit(3).toList();
        for (var e : today) {
            list.add(Map.of("text", e.getStartTime() + " " + e.getTitle(), "priority", "medium",
                    "link", "/calendar"));
        }
        long staleLeads = scopedLeads().stream()
                .filter(l -> "danger".equals(l.getFresh())).count();
        if (staleLeads > 0) {
            list.add(Map.of("text", "跟进即将流失线索（" + staleLeads + " 条）", "priority", "high",
                    "link", "/crm"));
        }
        long waiting = scopedPairs().stream().filter(p -> "待确认".equals(p.getStatus())).count();
        if (waiting > 0) {
            list.add(Map.of("text", "催办待确认牵线反馈（" + waiting + " 组）", "priority", "low",
                    "link", "/match"));
        }
        if (list.isEmpty()) {
            list.add(Map.of("text", "今日暂无待办，去看看新会员吧", "priority", "low", "link", "/members"));
        }
        return list;
    }

    /** AI 最优匹配推荐（取本人最近活跃会员各推荐 1 位） */
    @GetMapping("/recs")
    public List<Map<String, Object>> recs() {
        List<Map<String, Object>> out = new ArrayList<>();
        List<Member> seeds = scopedMembers().stream()
                .sorted(Comparator.comparing(Member::getCreatedAt, Comparator.nullsLast(Comparator.naturalOrder())).reversed())
                .limit(2).toList();
        // P0-11：多个种子的推荐（含同步 AI 调用）并行执行，避免请求线程被串行的外部调用拖长；
        // 数据范围在请求线程内取好后显式传入，线程池线程不依赖 RequestContext
        final String scope = me();
        List<java.util.concurrent.CompletableFuture<List<Map<String, Object>>>> futures = seeds.stream()
                .map(seed -> java.util.concurrent.CompletableFuture.supplyAsync(
                        () -> matchService.recommend(seed.getId(), 1, scope), aiExecutor))
                .toList();
        for (int i = 0; i < seeds.size(); i++) {
            Member seed = seeds.get(i);
            List<Map<String, Object>> rec;
            try {
                rec = futures.get(i).join();
            } catch (Exception e) {
                continue;
            }
            if (rec.isEmpty()) continue;
            Map<String, Object> r = rec.get(0);
            Member m = (Member) r.get("member");
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("name", m.getName() + " · " + m.getAge());
            row.put("score", r.get("score"));
            row.put("detail", String.join(" · ", nz(m.getEducation()), nz(m.getJob()), nz(m.getCity())));
            @SuppressWarnings("unchecked")
            List<String> reasons = (List<String>) r.get("reasons");
            row.put("reason", String.join(" · ", reasons.stream().limit(3).toList()));
            row.put("avatarBg", m.getAvatarBg());
            row.put("initial", m.getName() == null || m.getName().isEmpty() ? "?" : m.getName().substring(0, 1));
            row.put("memberId", m.getId());
            row.put("forMemberId", seed.getId());
            row.put("forMemberName", seed.getName());
            out.add(row);
        }
        return out;
    }

    private Map<String, Object> toCard(Pair p) {
        Map<String, Object> c = new LinkedHashMap<>();
        Member male = members.findById(nz(p.getMaleId())).orElse(null);
        Member female = members.findById(nz(p.getFemaleId())).orElse(null);
        c.put("id", p.getId());
        c.put("male", p.getMaleName());
        c.put("female", p.getFemaleName());
        c.put("maleInfo", male == null ? "" : male.getAge() + "岁 · " + nz(male.getJob()));
        c.put("femaleInfo", female == null ? "" : female.getAge() + "岁 · " + nz(female.getJob()));
        c.put("score", p.getScore());
        c.put("tag", nz(p.getStage()));
        c.put("tagKind", (p.getScore() != null && p.getScore() >= 85) ? "gold" : "burgundy");
        c.put("agent", p.getAgent());
        c.put("time", ago(p.getUpdatedAt()));
        return c;
    }

    private static Map<String, Object> kpi(String label, String value, String unit, String trend,
                                           boolean up, String icon, String tone) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("label", label);
        m.put("value", value);
        m.put("unit", unit);
        m.put("trend", trend);
        m.put("up", up);
        m.put("icon", icon);
        m.put("tone", tone);
        return m;
    }

    // —— 数据范围辅助：红娘只看本人负责的数据，管理员/主管看全局 ——
    private static String me() {
        return RequestContext.isAgent() ? RequestContext.agentName() : null;
    }

    private List<Member> scopedMembers() {
        String me = me();
        return me == null ? members.findAll() : members.findByAgent(me);
    }

    private List<Pair> scopedPairs() {
        String me = me();
        return me == null ? pairs.findAll() : pairs.findByAgent(me);
    }

    private List<FinanceOrder> scopedOrders() {
        String me = me();
        return me == null ? orders.findAll() : orders.findByAgent(me);
    }

    private List<AuditTask> scopedAudits() {
        String me = me();
        return me == null ? audits.findAll() : audits.findByAgent(me);
    }

    private List<CalendarEvent> scopedEvents() {
        String me = me();
        return me == null ? events.findAll() : events.findByAgent(me);
    }

    private List<CrmLead> scopedLeads() {
        String me = me();
        return me == null ? leads.findAll() : leads.findByAgent(me);
    }

    static String ago(LocalDateTime t) {
        if (t == null) return "";
        long minutes = java.time.Duration.between(t, LocalDateTime.now()).toMinutes();
        if (minutes < 1) return "刚刚";
        if (minutes < 60) return minutes + "分钟前";
        if (minutes < 1440) return (minutes / 60) + "小时前";
        long days = minutes / 1440;
        if (days == 1) return "昨天";
        if (days < 7) return days + "天前";
        return (days / 7) + "周前";
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }
}
