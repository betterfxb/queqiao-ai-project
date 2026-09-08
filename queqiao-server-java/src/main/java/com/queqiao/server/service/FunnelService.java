package com.queqiao.server.service;

import com.queqiao.server.entity.*;
import com.queqiao.server.repo.*;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 转化获客漏斗：以「会员」为 cohorts 主体，统计六个里程碑层级的去重人数，
 * 并给出层间转化率、整体成功率/付费率（对比行业基准）。
 *
 * 漏斗层级：注册(建档会员) → 认证(已认证会员) → 付费(有已完成非退款订单) →
 *           牵线(出现在任一牵线) → 约见(出现在「见面安排」日程) → 成功(出现在「已成功」牵线)
 *
 * 以会员去重计数可保证层级单调递减、贴近真实转化漏斗；time window 仅按各里程碑实体的
 * createdAt 过滤，便于观察指定周期内的转化表现。
 */
@Service
public class FunnelService {

    private final MemberRepository members;
    private final FinanceOrderRepository orders;
    private final CrmLeadRepository leads;
    private final PairRepository pairs;
    private final CalendarEventRepository events;
    private final AgentRepository agents;

    public FunnelService(MemberRepository members, FinanceOrderRepository orders,
                         CrmLeadRepository leads, PairRepository pairs,
                         CalendarEventRepository events, AgentRepository agents) {
        this.members = members;
        this.orders = orders;
        this.leads = leads;
        this.pairs = pairs;
        this.events = events;
        this.agents = agents;
    }

    /** 行业基准：注册→成功 整体转化率约 8% */
    public static final double BENCHMARK_SUCCESS = 0.08;
    /** 行业基准：注册→付费 整体转化率约 16.5% */
    public static final double BENCHMARK_PAID = 0.165;

    public Map<String, Object> funnel(Integer days) {
        LocalDateTime cutoff = (days == null || days <= 0) ? null : LocalDateTime.now().minusDays(days);

        // P0-10：时间窗过滤下沉 DB，且每类数据只加载一次，避免同一请求内多次整表扫描
        List<Member> memberWin = cutoff == null ? members.findAll() : members.findByCreatedAtAfter(cutoff);
        List<FinanceOrder> orderWin = cutoff == null ? orders.findAll() : orders.findByCreatedAtAfter(cutoff);
        List<Pair> pairWin = cutoff == null ? pairs.findAll() : pairs.findByCreatedAtAfter(cutoff);
        List<CalendarEvent> eventWin = cutoff == null ? events.findAll() : events.findByCreatedAtAfter(cutoff);

        // 1) 注册：建档会员
        Set<String> register = memberWin.stream()
                .map(Member::getId).collect(Collectors.toSet());

        // 2) 认证：已认证会员（verified 标记 或 状态含「认证」）
        Set<String> verified = memberWin.stream()
                .filter(m -> Boolean.TRUE.equals(m.getVerified())
                        || (m.getStatus() != null && m.getStatus().contains("认证")))
                .map(Member::getId).collect(Collectors.toSet());

        // 3) 付费：有「已完成且非退款」订单的会员（去重）
        Set<String> paid = orderWin.stream()
                .filter(o -> "已完成".equals(o.getStatus()) && !"退款".equals(o.getType()) && o.getMemberId() != null)
                .map(FinanceOrder::getMemberId).collect(Collectors.toSet());

        // 4) 牵线：出现在任一牵线中的会员（男女双方）
        Set<String> matched = new HashSet<>();
        for (Pair p : pairWin) {
            if (p.getMaleId() != null) matched.add(p.getMaleId());
            if (p.getFemaleId() != null) matched.add(p.getFemaleId());
        }

        // 5) 约见：出现在「见面安排」日程中的会员
        Set<String> met = new HashSet<>();
        for (CalendarEvent e : eventWin) {
            if (e.getType() != null && e.getType().contains("见面") && e.getMemberIds() != null) {
                for (String id : e.getMemberIds().split(",")) {
                    String t = id.trim();
                    if (!t.isEmpty()) met.add(t);
                }
            }
        }

        // 6) 成功：出现在「已成功」牵线中的会员
        Set<String> success = new HashSet<>();
        for (Pair p : pairWin) {
            if ("已成功".equals(p.getStatus())) {
                if (p.getMaleId() != null) success.add(p.getMaleId());
                if (p.getFemaleId() != null) success.add(p.getFemaleId());
            }
        }

        long cRegister = register.size();
        long cVerified = verified.size();
        long cPaid = paid.size();
        long cMatched = matched.size();
        long cMet = met.size();
        long cSuccess = success.size();

        double overallSuccessRate = cRegister == 0 ? 0 : (double) cSuccess / cRegister;
        double overallPaidRate = cRegister == 0 ? 0 : (double) cPaid / cRegister;

        List<Map<String, Object>> stages = new ArrayList<>();
        addStage(stages, "注册", cRegister, null);
        addStage(stages, "认证", cVerified, cRegister);
        addStage(stages, "付费", cPaid, cVerified);
        addStage(stages, "牵线", cMatched, cPaid);
        addStage(stages, "约见", cMet, cMatched);
        addStage(stages, "成功", cSuccess, cMet);

        // 红娘人均单产（复用 metrics 口径）
        List<Map<String, Object>> agentEff = agents.findAll().stream().map(a -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("name", a.getName());
            m.put("monthMatch", a.getMonthMatch());
            m.put("monthRevenue", a.getMonthRevenue());
            m.put("successRate", a.getSuccessRate());
            return m;
        }).collect(Collectors.toList());

        Map<String, Object> res = new LinkedHashMap<>();
        res.put("days", days == null ? 0 : days);
        res.put("window", cutoff == null ? "全部" : "近 " + days + " 天");
        res.put("stages", stages);
        res.put("overallSuccessRate", round4(overallSuccessRate));
        res.put("overallPaidRate", round4(overallPaidRate));
        res.put("benchmarkSuccessRate", BENCHMARK_SUCCESS);
        res.put("benchmarkPaidRate", BENCHMARK_PAID);
        res.put("agentEfficiency", agentEff);
        res.put("updatedAt", LocalDateTime.now().toString());
        return res;
    }

    private void addStage(List<Map<String, Object>> stages, String name, long count, Long prev) {
        Map<String, Object> s = new LinkedHashMap<>();
        s.put("name", name);
        s.put("count", count);
        s.put("prevCount", prev);
        double conv = (prev == null || prev == 0) ? 1.0 : (double) count / prev;
        s.put("conversion", round4(conv));
        stages.add(s);
    }

    private static double round4(double v) {
        return Math.round(v * 10000.0) / 10000.0;
    }
}
