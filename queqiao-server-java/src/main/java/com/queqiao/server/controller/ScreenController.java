package com.queqiao.server.controller;

import com.queqiao.server.common.Money;
import com.queqiao.server.entity.*;
import com.queqiao.server.repo.*;
import com.queqiao.server.service.SatisfactionService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 数据大屏聚合接口。
 * 一次性返回所有面板所需数据，避免大屏轮询发起 10 个请求。
 */
@RestController
@RequestMapping("/api/screen")
public class ScreenController {

    /** 大屏聚合结果缓存时长（毫秒） */
    private static final long CACHE_TTL_MS = 10_000L;

    private volatile Map<String, Object> screenCache;
    private volatile long screenCacheUntil;

    private final MemberRepository memberRepo;
    private final AgentRepository agentRepo;
    private final PairRepository pairRepo;
    private final ActivityRepository activityRepo;
    private final FinanceOrderRepository orderRepo;
    private final OperationLogRepository logRepo;
    private final PairFeedbackRepository feedbackRepo;

    public ScreenController(MemberRepository memberRepo, AgentRepository agentRepo,
                            PairRepository pairRepo, ActivityRepository activityRepo,
                            FinanceOrderRepository orderRepo, OperationLogRepository logRepo,
                            PairFeedbackRepository feedbackRepo) {
        this.memberRepo = memberRepo;
        this.agentRepo = agentRepo;
        this.pairRepo = pairRepo;
        this.activityRepo = activityRepo;
        this.orderRepo = orderRepo;
        this.logRepo = logRepo;
        this.feedbackRepo = feedbackRepo;
    }

    /**
     * 大屏全量数据。
     *
     * <p>P0-10 折中：大屏是「全域统计」，天然需要全量数据，但它由前端定时轮询（秒级），
     * 每次都做多表全扫描会直接压垮库。这里对聚合结果做 {@value #CACHE_TTL_MS} ms 级缓存，
     * 把 N 次轮询压成 1 次扫描；数据延迟对大屏展示无影响。</p>
     */
    @GetMapping
    public Map<String, Object> screen() {
        Map<String, Object> cached = screenCache;
        if (cached != null && System.currentTimeMillis() < screenCacheUntil) return cached;
        Map<String, Object> fresh = buildScreen();
        screenCache = fresh;
        screenCacheUntil = System.currentTimeMillis() + CACHE_TTL_MS;
        return fresh;
    }

    private Map<String, Object> buildScreen() {
        List<Member> members = memberRepo.findAll();
        List<Agent> agents = agentRepo.findAll();
        List<Pair> pairs = pairRepo.findAll();
        List<Activity> activities = activityRepo.findAll();
        List<FinanceOrder> orders = orderRepo.findAll();

        Map<String, Object> res = new LinkedHashMap<>();
        res.put("updatedAt", LocalDateTime.now().toString());
        res.put("statTiles", statTiles(members, pairs, activities, orders, agents));
        res.put("matchTrend", matchTrend(pairs));
        res.put("agentRank", agentRank(agents));
        res.put("memberLevel", groupCount(members, Member::getLevel));
        res.put("genderAge", genderAge(members));
        res.put("region", topN(groupCount(members, Member::getCity), 8));
        res.put("funnel", funnel(pairs));
        res.put("satisfaction", satisfaction(agents, pairs, feedbackRepo.findAll()));
        res.put("hotTags", hotTags(members));
        res.put("activityFeed", feed());
        return res;
    }

    /** 顶部指标块 */
    private List<Map<String, Object>> statTiles(List<Member> members, List<Pair> pairs,
                                                List<Activity> activities, List<FinanceOrder> orders,
                                                List<Agent> agents) {
        LocalDate today = LocalDate.now();
        long monthNew = members.stream().filter(m -> m.getCreatedAt() != null
                && m.getCreatedAt().getYear() == today.getYear()
                && m.getCreatedAt().getMonthValue() == today.getMonthValue()).count();
        long success = pairs.stream().filter(p -> "已成功".equals(p.getStatus())).count();
        BigDecimal revenue = Money.sum(orders.stream().filter(o -> !"退款".equals(o.getType()))
                .toList(), FinanceOrder::getAmount);

        return List.of(
                tile("会员总数", members.size(), "位", "+" + monthNew + " 本月"),
                tile("在服红娘", agents.stream().filter(a -> "在职".equals(a.getStatus())).count(), "人", "覆盖全部城市"),
                tile("累计牵线", pairs.size(), "对", "成功 " + success + " 对"),
                tile("牵手成功率", pairs.isEmpty() ? 0 : Math.round(success * 1000.0 / pairs.size()) / 10.0, "%", "环比稳定"),
                tile("活动场次", activities.size(), "场", "报名 "
                        + activities.stream().mapToInt(a -> a.getSigned() == null ? 0 : a.getSigned()).sum() + " 人次"),
                tile("累计营收", Math.round(Money.toDouble(revenue) / 1000) / 10.0, "万元", "订单 " + orders.size() + " 笔"));
    }

    /** 近 12 月牵线 / 成功趋势 */
    private Map<String, Object> matchTrend(List<Pair> pairs) {
        List<String> months = new ArrayList<>();
        List<Long> created = new ArrayList<>();
        List<Long> success = new ArrayList<>();
        LocalDate today = LocalDate.now();
        for (int i = 11; i >= 0; i--) {
            LocalDate d = today.minusMonths(i);
            months.add(d.getMonthValue() + "月");
            created.add(pairs.stream().filter(p -> sameMonth(p.getCreatedAt(), d)).count());
            success.add(pairs.stream().filter(p -> "已成功".equals(p.getStatus()) && sameMonth(p.getUpdatedAt(), d)).count());
        }
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("months", months);
        m.put("created", created);
        m.put("success", success);
        return m;
    }

    /** 红娘业绩排行 */
    private List<Map<String, Object>> agentRank(List<Agent> agents) {
        return agents.stream()
                .sorted((a, b) -> Double.compare(
                        b.getMonthRevenue() == null ? 0 : b.getMonthRevenue(),
                        a.getMonthRevenue() == null ? 0 : a.getMonthRevenue()))
                .limit(10)
                .map(a -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("name", a.getName());
                    m.put("level", a.getLevel());
                    m.put("value", a.getMonthRevenue() == null ? 0 : a.getMonthRevenue());
                    m.put("match", a.getMonthMatch() == null ? 0 : a.getMonthMatch());
                    m.put("successRate", a.getSuccessRate() == null ? 0 : a.getSuccessRate());
                    return m;
                }).collect(Collectors.toList());
    }

    /** 性别 × 年龄段分布 */
    private Map<String, Object> genderAge(List<Member> members) {
        String[] buckets = {"20-25", "26-30", "31-35", "36-40", "40+"};
        List<Long> male = new ArrayList<>();
        List<Long> female = new ArrayList<>();
        for (String b : buckets) {
            male.add(members.stream().filter(m -> "男".equals(m.getGender()) && inBucket(m.getAge(), b)).count());
            female.add(members.stream().filter(m -> "女".equals(m.getGender()) && inBucket(m.getAge(), b)).count());
        }
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("buckets", Arrays.asList(buckets));
        m.put("male", male);
        m.put("female", female);
        return m;
    }

    /** 牵线漏斗 */
    private List<Map<String, Object>> funnel(List<Pair> pairs) {
        long total = pairs.size();
        long feedback = pairs.stream().filter(p -> !"牵线中".equals(p.getStatus())).count();
        long met = pairs.stream().filter(p -> "已约见".equals(p.getStatus())
                || "双方同意".equals(p.getStatus()) || "已成功".equals(p.getStatus())).count();
        long agree = pairs.stream().filter(p -> "双方同意".equals(p.getStatus()) || "已成功".equals(p.getStatus())).count();
        long success = pairs.stream().filter(p -> "已成功".equals(p.getStatus())).count();
        return List.of(kv("发起牵线", total), kv("双方反馈", feedback), kv("安排见面", met),
                kv("双方同意", agree), kv("牵手成功", success));
    }

    /** 满意度仪表：红娘评分 + 牵线成功率 + 真实反馈均值 + 低分计数（驱动满意度闭环） */
    private Map<String, Object> satisfaction(List<Agent> agents, List<Pair> pairs, List<PairFeedback> fbList) {
        double rating = agents.stream().filter(a -> a.getRating() != null)
                .mapToDouble(Agent::getRating).average().orElse(4.6);
        long success = pairs.stream().filter(p -> "已成功".equals(p.getStatus())).count();
        double rate = pairs.isEmpty() ? 0 : success * 100.0 / pairs.size();
        double score = Math.min(100, rating / 5 * 70 + rate * 0.3);

        double fbAvg = fbList.stream().filter(f -> f.getSatisfaction() != null)
                .mapToInt(PairFeedback::getSatisfaction).average().orElse(0);
        long lowCnt = fbList.stream().filter(f -> f.getSatisfaction() != null
                && f.getSatisfaction() <= SatisfactionService.LOW_SAT).count();

        Map<String, Object> m = new LinkedHashMap<>();
        m.put("score", Math.round(score * 10) / 10.0);
        m.put("rating", Math.round(rating * 10) / 10.0);
        m.put("successRate", Math.round(rate * 10) / 10.0);
        m.put("feedbackAvg", Math.round(fbAvg * 10) / 10.0);
        m.put("lowSatisfactionCount", lowCnt);
        return m;
    }

    /** 热门标签词云（来自会员 tags 字段） */
    private List<Map<String, Object>> hotTags(List<Member> members) {
        Map<String, Long> count = new HashMap<>();
        for (Member m : members) {
            if (m.getTags() == null || m.getTags().isBlank()) continue;
            for (String t : m.getTags().split("[,，、]")) {
                String v = t.trim();
                if (!v.isEmpty()) count.merge(v, 1L, Long::sum);
            }
        }
        return count.entrySet().stream()
                .sorted((a, b) -> Long.compare(b.getValue(), a.getValue()))
                .limit(24)
                .map(e -> kv(e.getKey(), e.getValue()))
                .collect(Collectors.toList());
    }

    /** 实时动态流（取最近操作日志） */
    private List<Map<String, Object>> feed() {
        return logRepo.findTop100ByOrderByCreatedAtDesc().stream()
                .limit(20)
                .map(l -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("time", MessageController.ago(l.getCreatedAt()));
                    m.put("text", (l.getUserName() == null ? "系统" : l.getUserName())
                            + " " + l.getAction() + (l.getDetail() == null ? "" : "：" + l.getDetail()));
                    m.put("action", l.getAction());
                    return m;
                }).collect(Collectors.toList());
    }

    // ---------- 工具 ----------

    private static boolean sameMonth(LocalDateTime t, LocalDate d) {
        return t != null && t.getYear() == d.getYear() && t.getMonthValue() == d.getMonthValue();
    }

    private static boolean inBucket(Integer age, String bucket) {
        if (age == null) return false;
        return switch (bucket) {
            case "20-25" -> age >= 20 && age <= 25;
            case "26-30" -> age >= 26 && age <= 30;
            case "31-35" -> age >= 31 && age <= 35;
            case "36-40" -> age >= 36 && age <= 40;
            default -> age > 40;
        };
    }

    private static <T> List<Map<String, Object>> groupCount(List<T> list, java.util.function.Function<T, String> key) {
        Map<String, Long> g = list.stream()
                .map(key).filter(Objects::nonNull)
                .collect(Collectors.groupingBy(k -> k, Collectors.counting()));
        return g.entrySet().stream()
                .sorted((a, b) -> Long.compare(b.getValue(), a.getValue()))
                .map(e -> kv(e.getKey(), e.getValue()))
                .collect(Collectors.toList());
    }

    private static List<Map<String, Object>> topN(List<Map<String, Object>> list, int n) {
        return list.stream().limit(n).collect(Collectors.toList());
    }

    private static Map<String, Object> kv(String name, Object value) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("name", name);
        m.put("value", value);
        return m;
    }

    private static Map<String, Object> tile(String label, Object value, String unit, String sub) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("label", label);
        m.put("value", value);
        m.put("unit", unit);
        m.put("sub", sub);
        return m;
    }
}
