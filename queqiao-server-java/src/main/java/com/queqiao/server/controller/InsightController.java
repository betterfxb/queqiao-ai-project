package com.queqiao.server.controller;

import com.queqiao.server.entity.Member;
import com.queqiao.server.entity.Pair;
import com.queqiao.server.entity.FinanceOrder;
import com.queqiao.server.entity.RenewalReminder;
import com.queqiao.server.repo.MemberRepository;
import com.queqiao.server.repo.PairRepository;
import com.queqiao.server.repo.FinanceOrderRepository;
import com.queqiao.server.security.RequestContext;
import com.queqiao.server.service.SettingService;
import com.queqiao.server.service.RenewalService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 智能洞察：流失预警 / 续费提醒 / 预测式流失评分 / 漏斗归因。
 * 基于真实会员数据计算，供「智能运营中心」前端消费并驱动真实跟进动作。
 */
@RestController
@RequestMapping("/api/insights")
public class InsightController {

    private final MemberRepository members;
    private final PairRepository pairs;
    private final FinanceOrderRepository orders;
    private final SettingService settings;
    private final RenewalService renewals;

    public InsightController(MemberRepository members, PairRepository pairs,
                            FinanceOrderRepository orders, SettingService settings,
                            RenewalService renewals) {
        this.members = members;
        this.pairs = pairs;
        this.orders = orders;
        this.settings = settings;
        this.renewals = renewals;
    }

    /** 终态（不参与预警） */
    private static final Set<String> TERMINAL = Set.of("已脱单", "已流失", "已终止", "已暂停");

    private static final int CHURN_DAYS = 14;
    private static final int RENEWAL_DAYS = 15;

    /** 流失预警：活跃会员超过 N 天未跟进，或近期反馈为差评 */
    @GetMapping("/churn")
    public Map<String, Object> churn() {
        RequestContext.requireManager();
        int churnDays = settings.getInt("biz.churnDays", CHURN_DAYS);
        LocalDate today = LocalDate.now();
        List<Map<String, Object>> list = members.findAll().stream()
                .filter(m -> !TERMINAL.contains(nz(m.getStatus())))
                .map(m -> {
                    LocalDate lf = m.getLastFollowUp() != null
                            ? m.getLastFollowUp()
                            : (m.getUpdatedAt() != null ? m.getUpdatedAt().toLocalDate() : today);
                    long days = ChronoUnit.DAYS.between(lf, today);
                    boolean bad = "差评".equals(m.getFeedback());
                    if (days >= churnDays || bad) {
                        Map<String, Object> r = new LinkedHashMap<>();
                        r.put("member", m);
                        r.put("days", days);
                        r.put("feedback", nz(m.getFeedback()));
                        r.put("reason", bad ? "近期反馈不佳（差评），需调整策略" : ("已 " + days + " 天未跟进"));
                        return r;
                    }
                    return null;
                })
                .filter(Objects::nonNull)
                .sorted((a, b) -> Long.compare((Long) b.get("days"), (Long) a.get("days")))
                .collect(Collectors.toList());
        Map<String, Object> res = new LinkedHashMap<>();
        res.put("list", list);
        res.put("total", list.size());
        return res;
    }

    /**
     * 续费提醒（兼容旧路径）。
     * 1-G 一致性：不再按会员表 expire 单独推导，统一委托 {@link #renewals()}，
     * 与 finance 真实订单同源，避免「卡片数字与列表条目不一致」。
     */
    @GetMapping("/renewal")
    public Map<String, Object> renewal() {
        return renewals();
    }

    /**
     * 1-F 预测式流失评分（提前 14 天）。
     * 综合「未跟进时长 / 反馈情绪 / 牵线命中率」给出 0~1 的 14 天内流失概率，
     * 并给出可解释的理由标签；仅回传中高风险（>=0.4）会员，按风险降序。
     */
    @GetMapping("/churn-score")
    public Map<String, Object> churnScore() {
        RequestContext.requireManager();
        LocalDate today = LocalDate.now();
        List<Pair> allPairs = pairs.findAll();
        List<Map<String, Object>> list = members.findAll().stream()
                .filter(m -> !TERMINAL.contains(nz(m.getStatus())))
                .map(m -> {
                    LocalDate lf = m.getLastFollowUp() != null
                            ? m.getLastFollowUp()
                            : (m.getUpdatedAt() != null ? m.getUpdatedAt().toLocalDate() : today);
                    long days = ChronoUnit.DAYS.between(lf, today);
                    double gapRisk = 0.45 * Math.min(days / 30.0, 1.0);
                    double fbAdj = "差评".equals(m.getFeedback()) ? 0.25 : ("好评".equals(m.getFeedback()) ? -0.15 : 0.0);
                    long total = allPairs.stream().filter(p -> eq(p.getMaleId(), m.getId()) || eq(p.getFemaleId(), m.getId())).count();
                    long succ = allPairs.stream().filter(p -> (eq(p.getMaleId(), m.getId()) || eq(p.getFemaleId(), m.getId())) && "已成功".equals(p.getStatus())).count();
                    double hitRate = total == 0 ? 0.5 : (double) succ / total;
                    double score = 0.15 + gapRisk + fbAdj + 0.30 * (1 - hitRate);
                    score = Math.max(0.0, Math.min(1.0, score));
                    String level = score >= 0.7 ? "高" : (score >= 0.4 ? "中" : "低");
                    List<String> reasons = new ArrayList<>();
                    if (days >= 14) reasons.add("已 " + days + " 天未跟进");
                    if ("差评".equals(m.getFeedback())) reasons.add("近期反馈差评");
                    if (total > 0 && succ == 0) reasons.add("已有牵线但全部未成功");
                    if (total == 0) reasons.add("尚无牵线推进");
                    Map<String, Object> r = new LinkedHashMap<>();
                    r.put("member", m);
                    r.put("score", Math.round(score * 100) / 100.0);
                    r.put("level", level);
                    r.put("predictedAt", today.plusDays(14).toString());
                    r.put("reasons", reasons);
                    return r;
                })
                .filter(r -> (Double) r.get("score") >= 0.4)
                .sorted((a, b) -> Double.compare((Double) b.get("score"), (Double) a.get("score")))
                .collect(Collectors.toList());
        Map<String, Object> res = new LinkedHashMap<>();
        res.put("list", list);
        res.put("total", list.size());
        return res;
    }

    /**
     * 1-F 漏斗归因：注册→认证→付费→牵线→成功 逐级转化，
     * 定位掉点最多的步骤并给出该步骤的候选原因标签。
     */
    @GetMapping("/funnel-attribution")
    public Map<String, Object> funnelAttribution() {
        RequestContext.requireManager();
        long reg = members.findAll().stream().filter(m -> !TERMINAL.contains(nz(m.getStatus()))).count();
        long verified = members.findAll().stream().filter(m -> "已认证".equals(m.getStatus())).count();
        long paid = orders.findAll().stream()
                .filter(o -> "已完成".equals(o.getStatus()) && List.of("新购", "续费", "增值").contains(nz(o.getOrderType())))
                .count();
        long paired = pairs.findAll().size();
        long success = pairs.findAll().stream().filter(p -> "已成功".equals(p.getStatus())).count();

        List<Map<String, Object>> stages = new ArrayList<>();
        addStage(stages, "注册", reg, null);
        addStage(stages, "认证", verified, reg);
        addStage(stages, "付费", paid, verified);
        addStage(stages, "牵线", paired, paid);
        addStage(stages, "成功", success, paired);

        Map<String, Object> worst = stages.get(0);
        long worstDrop = -1;
        for (int i = 1; i < stages.size(); i++) {
            long prev = (long) stages.get(i - 1).get("count");
            long cur = (long) stages.get(i).get("count");
            long drop = prev - cur;
            if (drop > worstDrop) {
                worstDrop = drop;
                worst = stages.get(i);
            }
        }
        Map<String, Object> res = new LinkedHashMap<>();
        res.put("stages", stages);
        res.put("worstStep", worst.get("name"));
        res.put("worstDrop", worstDrop);
        res.put("reasons", reasonFor((String) worst.get("name")));
        return res;
    }

    private void addStage(List<Map<String, Object>> stages, String name, long count, Long prev) {
        Map<String, Object> s = new LinkedHashMap<>();
        s.put("name", name);
        s.put("count", count);
        s.put("prevCount", prev);
        s.put("conversion", prev == null ? 1.0 : (prev == 0 ? 0.0 : (double) count / prev));
        stages.add(s);
    }

    private List<String> reasonFor(String step) {
        switch (step) {
            case "认证": return List.of("认证流程过长", "实人认证未自动化", "引导话术不足");
            case "付费": return List.of("价格敏感", "缺乏分期/优惠券", "未触发紧迫感");
            case "牵线": return List.of("匹配精准度待提升", "红娘产能瓶颈", "会员画像不完整");
            case "成功": return List.of("约见转化弱", "缺乏约会教练", "双方家庭阻力");
            default: return List.of("获客渠道质量波动");
        }
    }

    private static boolean eq(String a, String b) {
        return a != null && a.equals(b);
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }

    /**
     * 1-G 续费提醒（与 finance 同源）：直接引用真实交易订单的到期日，
     * 消除「会员 expire 字段」与「财务订单」双源口径。续费成功后对应提醒自动关闭。
     */
    @GetMapping("/renewals")
    public Map<String, Object> renewals() {
        RequestContext.requireManager();
        LocalDate today = LocalDate.now();
        List<RenewalReminder> list = renewals.listOpen();
        List<Map<String, Object>> out = list.stream().map(r -> {
            Map<String, Object> mem = new LinkedHashMap<>();
            Member mm = r.getMemberId() != null ? members.findById(r.getMemberId()).orElse(null) : null;
            if (mm != null) {
                mem.put("id", mm.getId());
                mem.put("name", mm.getName());
                mem.put("level", mm.getLevel());
                mem.put("agent", mm.getAgent());
                mem.put("gender", mm.getGender());
                mem.put("age", mm.getAge());
            } else {
                mem.put("id", r.getMemberId());
                mem.put("name", r.getMemberName());
                mem.put("agent", r.getAgent());
            }
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("reminderId", r.getId());
            m.put("orderId", r.getOrderId());
            m.put("member", mem);
            m.put("expire", r.getExpireDate().toString());
            m.put("daysLeft", ChronoUnit.DAYS.between(today, r.getExpireDate()));
            return m;
        }).collect(Collectors.toList());
        Map<String, Object> res = new LinkedHashMap<>();
        res.put("list", out);
        res.put("total", out.size());
        return res;
    }
}
