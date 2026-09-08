package com.queqiao.server.controller;

import com.queqiao.server.ai.AiClient;
import com.queqiao.server.common.BizException;
import com.queqiao.server.entity.AiFeedback;
import com.queqiao.server.entity.Member;
import com.queqiao.server.entity.Pair;
import com.queqiao.server.repo.MemberRepository;
import com.queqiao.server.repo.PairRepository;
import com.queqiao.server.security.RequestContext;
import com.queqiao.server.service.AiFeedbackService;
import com.queqiao.server.service.LogService;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * AI 增效能力（B 类）。
 *
 * <ul>
 *   <li>GET  /api/ai/coach/profile/{memberId} 会员画像教练（AI 不可用时本地降级）</li>
 *   <li>GET  /api/ai/coach/next-step/{pairId} 约会教练：下一步约会建议 + 话题/穿搭话术库（AI 不可用时本地降级）</li>
 *   <li>POST /api/ai/moderate                 内容风控预检</li>
 *   <li>POST /api/ai/feedback                 建议采纳/否决反馈（落库 + 在线学习）</li>
 *   <li>GET  /api/ai/feedback/stats           权重与采纳率</li>
 *   <li>POST /api/ai/feedback/replay          手动全量回灌（ADMIN）</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/ai")
public class AiController {

    private final AiClient ai;
    private final MemberRepository members;
    private final PairRepository pairs;
    private final AiFeedbackService feedbacks;
    private final LogService logs;

    public AiController(AiClient ai, MemberRepository members, PairRepository pairs,
                        AiFeedbackService feedbacks, LogService logs) {
        this.ai = ai;
        this.members = members;
        this.pairs = pairs;
        this.feedbacks = feedbacks;
        this.logs = logs;
    }

    @GetMapping("/health")
    public Map<String, Object> health() {
        boolean ok = ai.health();
        return Map.of("ai", ok ? "up" : "down", "degraded", !ok);
    }

    // ---------------------------------------------------------------- 画像教练

    /** 会员画像教练：完整度诊断 + 按收益排序的补全清单 + 沟通脚本 */
    @GetMapping("/coach/profile/{memberId}")
    public Map<String, Object> coachProfile(@PathVariable String memberId) {
        Member m = members.findById(memberId).orElseThrow(() -> BizException.notFound("会员"));
        assertOwned(m);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("id", m.getId());
        payload.put("name", nz(m.getName()));
        payload.put("gender", nz(m.getGender()));
        payload.put("age", m.getAge());
        payload.put("city", nz(m.getCity()));
        payload.put("education", nz(m.getEducation()));
        payload.put("job", nz(m.getJob()));
        payload.put("income", nz(m.getIncome()));
        payload.put("height", m.getHeight());
        payload.put("marriage", nz(m.getMarriage()));
        payload.put("house", nz(m.getHouse()));
        payload.put("car", nz(m.getCar()));
        payload.put("hometown", nz(m.getHometown()));
        payload.put("tags", splitTags(m.getTags()));
        payload.put("bio", nz(m.getBio()));
        payload.put("requirement", nz(m.getRequirement()));
        payload.put("verified", Boolean.TRUE.equals(m.getVerified()));
        payload.put("photoCount", Boolean.TRUE.equals(m.getVerified()) ? 1 : 0);
        payload.put("matchCount", m.getMatchCount() == null ? 0 : m.getMatchCount());
        payload.put("successRate", m.getSuccessRate() == null ? 0 : m.getSuccessRate());

        Map<String, Object> res = ai.coachProfile(payload);
        if (res == null) {
            res = localCoach(payload);
            res.put("source", "local-fallback");
        } else {
            res.put("source", "ai");
        }
        res.put("memberId", m.getId());
        res.put("memberName", m.getName());
        return res;
    }

    // ---------------------------------------------------------------- 约会教练

    /** 约会教练：基于牵线进展给出下一步约会建议 + 话题/穿搭话术库（AI 不可用时本地降级） */
    @GetMapping("/coach/next-step/{pairId}")
    public Map<String, Object> coachNextStep(@PathVariable String pairId) {
        Pair p = pairs.findById(pairId).orElseThrow(() -> BizException.notFound("配对"));
        assertOwnedPair(p);

        // 规范阶段：依据 pair 的 stage/status 推导
        String stage = normCoachStage(p.getStage(), p.getStatus());

        // 双方兴趣标签 -> 话题库定制
        List<String> interests = new ArrayList<>();
        Member m = members.findById(p.getMaleId()).orElse(null);
        Member f = members.findById(p.getFemaleId()).orElse(null);
        if (m != null) interests.addAll(splitTags(m.getTags()));
        if (f != null) interests.addAll(splitTags(f.getTags()));

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("pairId", p.getId());
        payload.put("stage", stage);
        payload.put("meetCount", 0);
        payload.put("lastContactDays", 0);
        payload.put("interests", interests);
        payload.put("recentTopics", List.of());
        payload.put("gender", "");
        payload.put("season", "");

        Map<String, Object> res = ai.coachNextStep(payload);
        if (res == null) {
            res = localNextStep(stage, interests);
            res.put("source", "local-fallback");
        } else {
            res.put("source", "ai");
        }
        res.put("pairId", p.getId());
        res.put("stage", stage);
        res.put("stageSource", p.getStage());
        res.put("statusSource", p.getStatus());
        return res;
    }

    // ---------------------------------------------------------------- 内容风控

    /** 内容风控预检：前端发送前调用，可拿到 action/sanitized 做二次确认 */
    @PostMapping("/moderate")
    public Map<String, Object> moderate(@RequestBody Map<String, String> body) {
        String text = body.getOrDefault("text", "");
        if (text.isBlank()) throw new BizException("请输入待检测内容");
        Map<String, Object> res = ai.moderate(Map.of(
                "text", text,
                "scene", body.getOrDefault("scene", "message"),
                "role", RequestContext.get() == null ? "AGENT" : nz(RequestContext.get().role())));
        if (res == null) {
            res = localModerate(text);
            res.put("source", "local-fallback");
        } else {
            res.put("source", "ai");
        }
        return res;
    }

    // ---------------------------------------------------------------- 反馈学习

    /** 上报一条 AI 建议反馈（采纳/否决），落库并在线调权 */
    @PostMapping("/feedback")
    public Map<String, Object> feedback(@RequestBody Map<String, Object> body) {
        String action = String.valueOf(body.getOrDefault("action", "accept"));
        if (!"accept".equals(action) && !"reject".equals(action)) {
            throw new BizException("action 仅支持 accept / reject");
        }
        AiFeedback fb = new AiFeedback();
        fb.setScene(String.valueOf(body.getOrDefault("scene", "match")));
        fb.setItemId(str(body.get("itemId")));
        fb.setAction(action);
        fb.setDimensions(joinDims(body.get("dimensions")));
        fb.setReason(str(body.get("reason")));
        var ctx = RequestContext.get();
        fb.setOperator(ctx == null ? "系统" : ctx.userName());
        fb.setOperatorId(ctx == null ? null : ctx.userId());

        Map<String, Object> res = feedbacks.record(fb);
        logs.record("AI建议反馈-" + ("accept".equals(action) ? "采纳" : "否决"),
                nz(fb.getItemId()), nz(fb.getScene()) + " " + nz(fb.getDimensions()));
        return res;
    }

    /** 当前维度权重、采纳率与本地事实汇总 */
    @GetMapping("/feedback/stats")
    public Map<String, Object> feedbackStats() {
        Map<String, Object> res = new LinkedHashMap<>();
        Map<String, Object> remote = ai.feedbackStats();
        res.put("local", feedbacks.localSummary());
        res.put("model", remote);
        res.put("degraded", remote == null);
        return res;
    }

    /** 手动全量回灌（AI 服务重启后自愈的兜底入口） */
    @PostMapping("/feedback/replay")
    public Map<String, Object> replay() {
        RequestContext.requireAdmin();
        Map<String, Object> r = feedbacks.replayAll();
        logs.record("AI反馈全量回灌", "-", String.valueOf(r.get("replayed")));
        return r;
    }

    // ---------------------------------------------------------------- 本地降级

    /** AI 不可用时的画像完整度兜底（与 AI 侧字段权重保持一致的简化版） */
    static Map<String, Object> localCoach(Map<String, Object> p) {
        Object[][] fields = {
                {"age", "年龄", 8}, {"city", "常住城市", 8}, {"education", "学历", 7},
                {"job", "职业", 7}, {"income", "收入区间", 6}, {"height", "身高", 5},
                {"marriage", "婚况", 6}, {"house", "房产", 4}, {"car", "车辆", 3},
                {"hometown", "籍贯", 3}, {"tags", "兴趣标签", 10}, {"bio", "自我介绍", 12},
                {"requirement", "择偶要求", 10}, {"photoCount", "生活照", 8}, {"verified", "实名认证", 3},
        };
        int total = 0, got = 0;
        List<Map<String, Object>> missing = new ArrayList<>();
        for (Object[] f : fields) {
            String key = (String) f[0];
            int w = (Integer) f[2];
            total += w;
            if (filled(p.get(key), key)) {
                got += w;
            } else {
                missing.add(Map.of("field", key, "label", f[1], "weight", w,
                        "action", "补充" + f[1], "impact", "影响匹配曝光",
                        "priority", w >= 8 ? "高" : (w >= 5 ? "中" : "低")));
            }
        }
        missing.sort((a, b) -> (Integer) b.get("weight") - (Integer) a.get("weight"));
        int score = Math.round(got * 100f / total);
        String level = score >= 90 ? "优秀" : score >= 75 ? "良好" : score >= 55 ? "待完善" : "不合格";
        Map<String, Object> res = new LinkedHashMap<>();
        res.put("score", score);
        res.put("level", level);
        res.put("verdict", "AI 服务暂不可用，以下为本地规则评估结果");
        res.put("completedWeight", got);
        res.put("totalWeight", total);
        res.put("missing", missing);
        res.put("quickWins", missing.stream().limit(3).toList());
        res.put("strengths", List.of());
        res.put("risks", List.of("AI 服务不可用，暂未做质量风险分析"));
        res.put("script", "您好，为了让推荐更精准，还有部分资料需要补充，方便现在完善一下吗？");
        return res;
    }

    private static boolean filled(Object v, String key) {
        if (v == null) return false;
        if (v instanceof Boolean b) return b;
        if (v instanceof Number n) return n.intValue() > 0;
        if (v instanceof Collection<?> c) return c.size() >= 3;
        String s = String.valueOf(v).trim();
        if (s.isEmpty()) return false;
        if ("bio".equals(key)) return s.length() >= 80;
        if ("requirement".equals(key)) return s.length() >= 15;
        return true;
    }

    /** AI 不可用时的最小内容风控（只保留高危关键词与手机号，宁可误报不可漏放） */
    static Map<String, Object> localModerate(String text) {
        String[] high = {"转账", "汇款", "刷单", "返利", "USDT", "博彩", "约炮", "代孕", "验证码"};
        List<Map<String, Object>> hits = new ArrayList<>();
        for (String w : high) {
            if (text.contains(w)) {
                hits.add(Map.of("category", "fraud", "label", "高危词命中", "severity", 3, "keyword", w));
            }
        }
        String sanitized = text.replaceAll("(?<!\\d)(1[3-9]\\d)\\d{4}(\\d{4})(?!\\d)", "$1****$2");
        boolean pii = !sanitized.equals(text);
        if (pii) {
            hits.add(Map.of("category", "pii", "label", "联系方式外泄", "severity", 2, "keyword", "手机号"));
        }
        String action = hits.stream().anyMatch(h -> (Integer) h.get("severity") >= 3) ? "block"
                : (hits.isEmpty() ? "pass" : "warn");
        Map<String, Object> res = new LinkedHashMap<>();
        res.put("action", action);
        res.put("riskLevel", "block".equals(action) ? "高" : ("warn".equals(action) ? "中" : "无"));
        res.put("riskScore", hits.size() * 30);
        res.put("hits", hits);
        res.put("pii", pii ? List.of(Map.of("type", "phone", "label", "手机号")) : List.of());
        res.put("sanitized", sanitized);
        res.put("advice", List.of(hits.isEmpty() ? "内容合规，可以发送" : "AI 服务不可用，已按最小规则集拦截，请人工复核"));
        return res;
    }

    // ---------------------------------------------------------------- 工具

    /** 牵线进展 -> 规范教练阶段 */
    private static String normCoachStage(String stage, String status) {
        if ("已成功".equals(status)) return "committed";
        if ("已约见".equals(status)) return "met_once";
        if ("双方同意".equals(status) || "安排见面".equals(stage)) return "contacted";
        return "matched";
    }

    private void assertOwnedPair(Pair p) {
        if (RequestContext.isAgent()) {
            String me = RequestContext.agentName();
            if (me != null && !me.equals(p.getAgent())) {
                throw BizException.forbidden("无权访问该牵线");
            }
        }
    }

    /** AI 不可用时的约会教练兜底（与 AI 侧阶段建议保持一致的简化版） */
    static Map<String, Object> localNextStep(String stage, List<String> interests) {
        Map<String, Object> advice = stageAdvice(stage);
        List<String> topics = new ArrayList<>();
        for (String it : interests.stream().limit(3).toList()) {
            topics.add("聊聊你们都喜欢的「" + it + "」");
        }
        if (topics.isEmpty()) {
            topics.add("各自的城市生活");
            topics.add("最近一次旅行");
            topics.add("喜欢的食物口味");
        }
        String label = switch (stage) {
            case "matched" -> "刚牵线";
            case "contacted" -> "已建立联系";
            case "chatting" -> "线上沟通中";
            case "met_once" -> "已首次见面";
            case "dating" -> "稳定约会中";
            case "deepening" -> "关系升温中";
            default -> "已确定关系";
        };
        Map<String, Object> res = new LinkedHashMap<>();
        res.put("stage", stage);
        res.put("stageLabel", label);
        res.put("nextStep", advice.get("next"));
        res.put("actionWindow", advice.get("window"));
        res.put("topics", topics);
        res.put("outfit", List.of("干净得体，注意个人卫生与细节"));
        res.put("openers", advice.get("openers"));
        res.put("deepenQuestions", advice.get("deepen"));
        res.put("tips", advice.get("tips"));
        res.put("warnings", List.of("AI 服务暂不可用，以下为本地规则建议"));
        return res;
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> stageAdvice(String stage) {
        return switch (stage) {
            case "matched" -> Map.of(
                    "window", "3 天内",
                    "next", "先互发一条轻松的自我介绍，本周内约一次 15 分钟线上语音破冰，先别急着线下见面。",
                    "openers", List.of("你好呀，我是红娘牵线的，终于联系上啦~", "看到你资料里也喜欢XX，感觉有点缘分"),
                    "deepen", List.of("你理想中的周末一般怎么过？", "最近工作节奏忙吗，平时怎么放松？"),
                    "tips", List.of("破冰以轻松话题为主", "先建立舒适感，再谈见面"));
            case "contacted" -> Map.of(
                    "window", "7 天内",
                    "next", "趁热打铁把首次见面定下来，约在人流适中的公共场所。",
                    "openers", List.of("这周末有空吗，要不要去喝杯咖啡聊聊？", "我发现一家很安静的书店咖啡，感觉你会喜欢"),
                    "deepen", List.of("你第一次约会一般喜欢什么形式？", "有什么一直想吃但没去成的店吗？"),
                    "tips", List.of("给出具体时间地点比「改天约」更容易成行", "首次见面控制在 1~1.5 小时"));
            case "chatting" -> Map.of(
                    "window", "本周内",
                    "next", "锁定首次见面的时间与地点，提前发好定位，让对方有安全感。",
                    "openers", List.of("那我们就周六下午两点老地方见？", "我把定位发你，到了给我发个消息"),
                    "deepen", List.of("见面你一般喜欢安静还是热闹？", "有没有什么你特别在意的相处细节？"),
                    "tips", List.of("把模糊的好感变成具体的约会", "主动确认细节显得靠谱"));
            case "met_once" -> Map.of(
                    "window", "10 天内",
                    "next", "安排第二次约会，比首次更互动一些：手工、运动、市集或小型展览。",
                    "openers", List.of("上次聊得挺开心的，要不要一起去做个陶艺？", "这周末有个市集好像不错，一起去逛逛？"),
                    "deepen", List.of("你觉得我们相处起来舒服吗？", "你期待的恋爱节奏大概是怎样的？"),
                    "tips", List.of("第二次约会重在共同体验", "尊重对方身体边界与节奏"));
            case "dating" -> Map.of(
                    "window", "每 1~2 周",
                    "next", "关系进入稳定期，安排半日以上深度相处：近郊一日游、一起做饭、看展览。",
                    "openers", List.of("这周要不要来我家一起做顿饭？", "天气好了，我们去近郊转转吧"),
                    "deepen", List.of("你理想的生活状态是什么样的？", "我们在哪些事情上特别聊得来？"),
                    "tips", List.of("稳定期要制造只属于两人的回忆", "适时聊价值观但别变成面试"));
            case "deepening" -> Map.of(
                    "window", "近期",
                    "next", "关系已升温，选放松场合坦诚聊聊彼此对未来的规划与期待。",
                    "openers", List.of("我挺享受和你在一起的状态，想听听你对我们的期待", "我们认真聊聊以后吧，你是怎么想的？"),
                    "deepen", List.of("如果我们继续走下去，你最在意的是什么？", "哪些事是你绝对不能接受的？"),
                    "tips", List.of("升温期要确认关系而非一直暧昧", "坦诚但温和给对方空间"));
            default -> Map.of(
                    "window", "持续",
                    "next", "关系已确定，重心转向共同经营：建立日常沟通仪式、规划见家长。",
                    "openers", List.of("下个月带你去见我家人吧？", "我们一起攒个旅行基金怎么样？"),
                    "deepen", List.of("我们怎么分工家务比较舒服？", "对财务和大事的决策方式想怎么定？"),
                    "tips", List.of("确定关系后日常经营比惊喜更重要", "尽早对齐家庭与金钱观"));
        };
    }

    private void assertOwned(Member m) {
        if (RequestContext.isAgent()) {
            String me = RequestContext.agentName();
            if (me != null && !me.equals(m.getAgent())) {
                throw BizException.forbidden("无权访问该会员");
            }
        }
    }

    @SuppressWarnings("unchecked")
    private static String joinDims(Object v) {
        if (v == null) return "";
        if (v instanceof Collection<?> c) {
            return String.join(",", c.stream().map(String::valueOf).toList());
        }
        return String.valueOf(v);
    }

    private static List<String> splitTags(String tags) {
        if (tags == null || tags.isBlank()) return List.of();
        return Arrays.stream(tags.split("[,，、\\s]+")).filter(s -> !s.isBlank()).toList();
    }

    private static String str(Object o) {
        return o == null ? "" : String.valueOf(o);
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }
}
