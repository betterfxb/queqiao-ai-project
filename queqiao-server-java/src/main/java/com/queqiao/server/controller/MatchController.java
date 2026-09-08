package com.queqiao.server.controller;

import com.queqiao.server.ai.AiClient;
import com.queqiao.server.common.BizException;
import com.queqiao.server.common.Ids;
import com.queqiao.server.common.PageResult;
import com.queqiao.server.common.PairStatus;
import com.queqiao.server.common.Specs;
import com.queqiao.server.entity.CalendarEvent;
import com.queqiao.server.entity.Member;
import com.queqiao.server.entity.Pair;
import com.queqiao.server.entity.PairFeedback;
import com.queqiao.server.repo.PairFeedbackRepository;
import com.queqiao.server.repo.BlacklistRepository;
import com.queqiao.server.repo.CalendarEventRepository;
import com.queqiao.server.repo.MemberRepository;
import com.queqiao.server.repo.PairRepository;
import com.queqiao.server.security.RequestContext;
import com.queqiao.server.service.CommissionService;
import com.queqiao.server.service.LogService;
import com.queqiao.server.service.MatchService;
import com.queqiao.server.service.MemberPushService;
import com.queqiao.server.service.PointsService;
import com.queqiao.server.service.SatisfactionService;
import com.queqiao.server.service.StoryService;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.transaction.annotation.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

/** 智能牵线：推荐 / 配对 / 双方反馈 / 约见安排 */
@RestController
@RequestMapping("/api")
public class MatchController {

    private static final Logger log = LoggerFactory.getLogger(MatchController.class);

    private final PairRepository pairs;
    private final MemberRepository members;
    private final CalendarEventRepository events;
    private final MatchService matchService;
    private final AiClient ai;
    private final LogService logs;
    private final CommissionService commissionService;
    private final BlacklistRepository blacklists;
    private final StoryService storyService;
    private final PointsService points;
    private final MemberPushService memberPush;
    private final PairFeedbackRepository feedbacks;
    private final SatisfactionService satisfaction;

    public MatchController(PairRepository pairs, MemberRepository members, CalendarEventRepository events,
                           MatchService matchService, AiClient ai, LogService logs, CommissionService commissionService,
                           BlacklistRepository blacklists, StoryService storyService,                            PointsService points,
                           MemberPushService memberPush,
                           PairFeedbackRepository feedbacks, SatisfactionService satisfaction) {
        this.storyService = storyService;
        this.pairs = pairs;
        this.members = members;
        this.events = events;
        this.matchService = matchService;
        this.ai = ai;
        this.logs = logs;
        this.commissionService = commissionService;
        this.blacklists = blacklists;
        this.points = points;
        this.memberPush = memberPush;
        this.feedbacks = feedbacks;
        this.satisfaction = satisfaction;
    }

    /** 配对列表（红娘自动收敛为本人牵线的配对） */
    @GetMapping("/pairs")
    public PageResult<Pair> list(@RequestParam(required = false) String keyword,
                                 @RequestParam(required = false) String status,
                                 @RequestParam(required = false) String stage,
                                 @RequestParam(required = false) String agent,
                                 @RequestParam(defaultValue = "1") int page,
                                 @RequestParam(defaultValue = "20") int size) {
        String me = currentAgent();
        String scope = me != null ? me : agent;
        var spec = Specs.<Pair>of()
                .keyword(keyword, "maleName", "femaleName", "id")
                .eq("status", status).eq("stage", stage).eq("agent", scope).build();
        return PageResult.of(pairs.findAll(spec,
                PageRequest.of(Math.max(0, page - 1), size, Sort.by(Sort.Direction.DESC, "updatedAt"))));
    }

    /** 配对详情（含双方完整档案，供牵线对比视图使用） */
    @GetMapping("/pairs/{id}")
    public Map<String, Object> detail(@PathVariable String id) {
        Pair p = pairs.findById(id).orElseThrow(() -> BizException.notFound("配对"));
        assertOwned(p);
        Map<String, Object> res = new LinkedHashMap<>();
        res.put("pair", p);
        res.put("male", members.findById(nz(p.getMaleId())).orElse(null));
        res.put("female", members.findById(nz(p.getFemaleId())).orElse(null));
        res.put("events", events.findAll().stream()
                .filter(e -> id.equals(e.getPairId())).toList());
        return res;
    }

    /** 智能推荐：给定会员，返回按匹配度排序的候选人 */
    @GetMapping("/match/recommendations")
    public Map<String, Object> recommendations(@RequestParam String memberId,
                                               @RequestParam(defaultValue = "6") int limit) {
        Member target = members.findById(memberId).orElseThrow(() -> BizException.notFound("会员"));
        var list = matchService.recommend(memberId, limit);
        Map<String, Object> res = new LinkedHashMap<>();
        res.put("target", target);
        res.put("list", list);
        res.put("total", list.size());
        return res;
    }

    /** 两人匹配度详情（用于对比视图的雷达/评分） */
    @GetMapping("/match/score")
    public Map<String, Object> score(@RequestParam String maleId, @RequestParam String femaleId) {
        Member a = members.findById(maleId).orElseThrow(() -> BizException.notFound("男方会员"));
        Member b = members.findById(femaleId).orElseThrow(() -> BizException.notFound("女方会员"));
        var s = matchService.score(a, b);
        Map<String, Object> res = new LinkedHashMap<>();
        res.put("score", s.score());
        res.put("reasons", s.reasons());
        res.put("dimensions", s.dims());
        res.put("male", a);
        res.put("female", b);
        return res;
    }

    /** 建立牵线 */
    @PostMapping("/pairs")
    @Transactional
    public Pair create(@RequestBody Map<String, String> body) {
        String maleId = body.get("maleId");
        String femaleId = body.get("femaleId");
        if (maleId == null || femaleId == null) throw new BizException("请选择男女双方会员");
        Member a = members.findById(maleId).orElseThrow(() -> BizException.notFound("男方会员"));
        Member b = members.findById(femaleId).orElseThrow(() -> BizException.notFound("女方会员"));
        assertOwned(a);
        assertOwned(b);
        if (currentAgent() != null && !currentAgent().equals(a.getAgent())) {
            throw BizException.forbidden("只能为本人负责的会员牵线");
        }
        // 风控：黑名单拦截（任一方在生效黑名单中则禁止牵线）
        if (blacklists.findByMemberIdAndStatus(a.getId(), "生效").isPresent()
                || blacklists.findByMemberIdAndStatus(b.getId(), "生效").isPresent()) {
            throw BizException.forbidden("该会员已被列入黑名单，无法为其牵线");
        }

        Pair p = new Pair();
        p.setId(Ids.biz("P"));
        p.setMaleId(a.getId());
        p.setMaleName(a.getName());
        p.setFemaleId(b.getId());
        p.setFemaleName(b.getName());
        var s = matchService.score(a, b);
        p.setScore(s.score());
        p.setReason(String.join("；", s.reasons()));
        p.setStatus("待确认");
        p.setStage("初步推荐");
        String me = currentAgent();
        p.setAgent(me != null ? me : body.getOrDefault("agent", logs.currentUserName()));
        p.setNote(body.get("note"));
        p.setCreatedAt(LocalDateTime.now());
        p.setUpdatedAt(LocalDateTime.now());

        a.setStatus("牵线中");
        b.setStatus("牵线中");
        a.setMatchCount(nz(a.getMatchCount()) + 1);
        b.setMatchCount(nz(b.getMatchCount()) + 1);
        members.save(a);
        members.save(b);

        logs.record("建立牵线", p.getId(), a.getName() + " × " + b.getName());
        Pair saved = pairs.save(p);
        // 推送牵线邀请给双方会员（会员端 WS /user/queue/member）
        memberPush.push(a.getId(), Map.of("type", "pair-invite", "pairId", saved.getId(),
                "name", b.getName(), "gender", b.getGender(), "age", b.getAge(), "city", b.getCity()));
        memberPush.push(b.getId(), Map.of("type", "pair-invite", "pairId", saved.getId(),
                "name", a.getName(), "gender", a.getGender(), "age", a.getAge(), "city", a.getCity()));
        return saved;
    }

    /** 更新牵线状态 / 阶段 */
    @PutMapping("/pairs/{id}")
    @Transactional
    public Pair update(@PathVariable String id, @RequestBody Map<String, String> body) {
        Pair p = pairs.findById(id).orElseThrow(() -> BizException.notFound("配对"));
        assertOwned(p);
        boolean justSucceeded = false;
        if (body.get("status") != null) {
            PairStatus from = PairStatus.parse(p.getStatus());
            PairStatus to = PairStatus.parse(body.get("status"));
            if (from != null && to != null && !from.canTransitTo(to)) {
                throw new BizException("牵线状态不可从「" + p.getStatus() + "」变更为「" + body.get("status") + "」");
            }
            p.setStatus(body.get("status"));
            // 牵线成功 → 触发红娘分佣计提（为该牵线双方已支付订单按配置比例计提）
            if ("已成功".equals(body.get("status"))) {
                commissionService.settleFromPair(p);
                justSucceeded = true;
            }
        }
        if (body.get("stage") != null) p.setStage(body.get("stage"));
        if (body.get("note") != null) p.setNote(body.get("note"));
        if (body.get("agent") != null) p.setAgent(body.get("agent"));
        p.setUpdatedAt(LocalDateTime.now());
        logs.record("更新牵线", id, p.getMaleName() + " × " + p.getFemaleName());
        Pair saved = pairs.save(p);
        // 牵线成功 → 旁路异步沉淀脱敏成功案例草稿（幂等，失败不影响主流程）
        if (justSucceeded) {
            storyService.draftFromPairAsync(saved.getId(), RequestContext.agentName());
            // 牵线成功 → 双方各计一次高额积分（按牵线 ID 幂等）
            points.earn(saved.getMaleId(), "MATCH_SUCCESS", saved.getId(), logs.currentUserName(), "牵线成功奖励");
            points.earn(saved.getFemaleId(), "MATCH_SUCCESS", saved.getId(), logs.currentUserName(), "牵线成功奖励");
        }
        return saved;
    }

    /** 记录一方反馈（结构化：满意度 + 卡点 + 备注），兼容旧文本调用 */
    @PostMapping("/pairs/{id}/feedback")
    @Transactional
    public Pair feedback(@PathVariable String id, @RequestBody Map<String, Object> body) {
        Pair p = pairs.findById(id).orElseThrow(() -> BizException.notFound("配对"));
        assertOwned(p);
        String side = asStr(body.get("side"), "male");
        String text = asStr(body.get("text"), "");
        String kind = asStr(body.get("kind"), "success");
        Integer satisfaction = asInt(body.get("satisfaction"), null);
        List<String> blockers = asList(body.get("blockers"));

        if ("female".equals(side)) {
            p.setFemaleResponse(text);
            p.setFemaleResponseKind(kind);
        } else if ("male".equals(side)) {
            p.setMaleResponse(text);
            p.setMaleResponseKind(kind);
        }
        // 其他 side（如 both/agent）：仅沉淀结构化反馈，不改双方答复字段
        if ("success".equals(p.getMaleResponseKind()) && "success".equals(p.getFemaleResponseKind())) {
            PairStatus from = PairStatus.parse(p.getStatus());
            PairStatus to = PairStatus.双方同意;
            if (from != null && !from.canTransitTo(to)) {
                throw new BizException("当前牵线状态「" + p.getStatus() + "」不可回跳为双方同意");
            }
            p.setStatus("双方同意");
            p.setStage("安排见面");
        }
        p.setUpdatedAt(LocalDateTime.now());
        logs.record("记录牵线反馈", id, side + ": " + text);

        // 1-D 闭环：结构化反馈沉淀到独立表，并反哺会员偏好标签（影响后续匹配权重）
        PairFeedback fb = new PairFeedback();
        fb.setId(Ids.biz("PF"));
        fb.setPairId(p.getId());
        fb.setSide(side);
        fb.setSatisfaction(satisfaction);
        fb.setBlockerList(blockers);
        fb.setNote(text);
        fb.setCreatedAt(LocalDateTime.now());
        List<String> prefs = derivePreferences(satisfaction, kind, blockers);
        fb.setPreferenceTags(String.join(",", prefs));
        feedbacks.save(fb);
        if (!prefs.isEmpty()) {
            appendTags(p.getMaleId(), prefs);
            appendTags(p.getFemaleId(), prefs);
        }
        // 3-D 满意度闭环：低分反馈自动建跟进 + 红娘改进建议 + 回访日程
        this.satisfaction.onLowScore(fb, p);
        return pairs.save(p);
    }

    /** 安排约见（同时写入日程） */
    @PostMapping("/pairs/{id}/meeting")
    @Transactional
    public Map<String, Object> arrange(@PathVariable String id, @RequestBody Map<String, String> body) {
        Pair p = pairs.findById(id).orElseThrow(() -> BizException.notFound("配对"));
        assertOwned(p);
        CalendarEvent e = new CalendarEvent();
        e.setId(Ids.biz("EV"));
        e.setTitle(p.getMaleName() + " & " + p.getFemaleName() + " 见面安排");
        e.setType("见面安排");
        e.setDate(LocalDate.parse(body.getOrDefault("date", LocalDate.now().plusDays(3).toString())));
        e.setStartTime(body.getOrDefault("startTime", "15:00"));
        e.setEndTime(body.getOrDefault("endTime", "17:00"));
        e.setPlace(body.getOrDefault("place", "待定"));
        e.setPairId(p.getId());
        e.setMemberIds(p.getMaleId() + "," + p.getFemaleId());
        e.setMemberNames(p.getMaleName() + "," + p.getFemaleName());
        e.setAgent(p.getAgent());
        e.setStatus("待开始");
        e.setRemark(body.get("remark"));
        e.setCreatedAt(LocalDateTime.now());
        e.setUpdatedAt(LocalDateTime.now());
        events.save(e);

        p.setStatus("已约见");
        p.setStage("安排见面");
        p.setUpdatedAt(LocalDateTime.now());
        pairs.save(p);

        logs.record("安排约见", p.getId(), e.getDate() + " " + e.getStartTime() + " " + e.getPlace());
        // 约见达成 → 双方各记一次积分（按牵线 ID 幂等，避免重复约见重复计）
        points.earn(p.getMaleId(), "MEETING", p.getId(), logs.currentUserName(), "约见达成");
        points.earn(p.getFemaleId(), "MEETING", p.getId(), logs.currentUserName(), "约见达成");
        return Map.of("pair", p, "event", e);
    }

    /** 终止牵线 */
    @DeleteMapping("/pairs/{id}")
    public Map<String, Object> terminate(@PathVariable String id,
                                         @RequestParam(required = false) String reason) {
        Pair p = pairs.findById(id).orElseThrow(() -> BizException.notFound("配对"));
        assertOwned(p);
        p.setStatus("已终止");
        p.setNote(reason == null ? p.getNote() : reason);
        p.setUpdatedAt(LocalDateTime.now());
        pairs.save(p);
        logs.record("终止牵线", id, nz(reason));
        return Map.of("ok", true, "id", id);
    }

    /** AI 沟通话术建议 */
    @PostMapping("/match/reply-suggest")
    public Map<String, Object> replySuggest(@RequestBody Map<String, Object> body) {
        Map<String, Object> r = ai.replySuggest(body);
        if (r != null) return r;
        return Map.of("source", "local-fallback", "suggestions", List.of(
                "您好，我这边为您筛选了一位条件比较契合的会员，方便的话我把资料发您看看？",
                "两位在学历与生活城市上都比较接近，要不要先线上聊一聊？",
                "如果您对这位比较有意向，我可以帮忙安排一次轻松的咖啡会面。"));
    }

    /** 解释型匹配报告：契合点话术 + 建议沟通话题（让红娘能向客户解释「为什么推荐 TA」） */
    @GetMapping("/pairs/{id}/explain")
    public Map<String, Object> explain(@PathVariable String id) {
        Pair p = pairs.findById(id).orElseThrow(() -> BizException.notFound("配对"));
        assertOwned(p);
        Member a = members.findById(nz(p.getMaleId())).orElse(null);
        Member b = members.findById(nz(p.getFemaleId())).orElse(null);
        if (a == null || b == null) throw BizException.notFound("配对双方会员");

        var s = matchService.score(a, b);
        Map<String, Integer> dims = s.dims();

        List<String> fitPoints = new ArrayList<>();
        for (var e : dims.entrySet()) {
            if (e.getValue() >= 60) fitPoints.add(e.getKey() + "契合度高（" + e.getValue() + "%）");
        }
        if (fitPoints.isEmpty()) fitPoints.add("基础条件可进一步沟通确认");

        Set<String> common = tagSet(a.getTags());
        common.retainAll(tagSet(b.getTags()));
        List<String> topics = new ArrayList<>();
        if (!common.isEmpty()) topics.add("从共同兴趣切入：" + String.join("、", common));
        if (Objects.equals(a.getCity(), b.getCity())) topics.add("约在" + cityOf(a.getCity()) + "见面，降低距离成本");
        if (nz(a.getRequirement()).length() > 0) topics.add("围绕择偶要求展开：" + a.getRequirement());
        if (topics.isEmpty()) topics.add("从彼此的工作与日常生活切入破冰");

        Map<String, Object> res = new LinkedHashMap<>();
        res.put("pairId", id);
        res.put("score", s.score());
        res.put("dims", dims);
        res.put("fitPoints", fitPoints);
        res.put("topics", topics);
        res.put("summary", String.join("；", s.reasons()));
        // 3-A 语义契合维度（离线同源算法，AI 宕机仍可用）
        res.put("semanticKeywords", s.semanticKeywords());
        res.put("semanticFit", dims.getOrDefault("语义契合", 0));

        // 1-D：解释报告附历史反馈摘要（满意度均值 + 高频卡点），让反馈闭环可见
        long fbCount = feedbacks.countByPairId(id);
        Double fbAvg = feedbacks.avgSatisfaction(id);
        List<String> blkTop = new ArrayList<>();
        java.util.Map<String, Long> blkCount = new java.util.LinkedHashMap<>();
        for (PairFeedback f : feedbacks.findByPairIdOrderByCreatedAtDesc(id)) {
            for (String blk : f.blockerList()) blkCount.merge(blk, 1L, Long::sum);
        }
        blkCount.entrySet().stream()
                .sorted(java.util.Map.Entry.<String, Long>comparingByValue(java.util.Comparator.reverseOrder()))
                .limit(3).forEach(e -> blkTop.add(e.getKey()));
        java.util.Map<String, Object> fbSum = new java.util.LinkedHashMap<>();
        fbSum.put("count", fbCount);
        fbSum.put("satisfactionAvg", fbAvg == null ? 0 : Math.round(fbAvg * 10.0) / 10.0);
        fbSum.put("blockersTop", blkTop);
        res.put("feedbackSummary", fbSum);
        return res;
    }

    private static Set<String> tagSet(String tags) {
        if (tags == null || tags.isBlank()) return new HashSet<>();
        return new HashSet<>(Arrays.asList(tags.split("[,，、]")));
    }

    private static String cityOf(String city) {
        if (city == null) return "";
        int i = city.indexOf('·');
        return i > 0 ? city.substring(0, i) : city;
    }

    /** 统计（牵线页顶部卡片，红娘视角收敛为本人牵线） */
    @GetMapping("/pairs/stats")
    public Map<String, Object> stats() {
        List<Pair> mine = scopedPairs();
        Map<String, Object> m = new LinkedHashMap<>();
        long total = mine.size();
        m.put("total", total);
        m.put("ongoing", mine.stream().filter(p -> Set.of("双方同意", "进行中").contains(p.getStatus())).count());
        m.put("waiting", mine.stream().filter(p -> "待确认".equals(p.getStatus())).count());
        m.put("met", mine.stream().filter(p -> "已约见".equals(p.getStatus())).count());
        m.put("success", mine.stream().filter(p -> "已成功".equals(p.getStatus())).count());
        m.put("successRate", total == 0 ? 0
                : Math.round(mine.stream().filter(p -> "已成功".equals(p.getStatus())).count() * 100.0 / total));
        return m;
    }

    private static String currentAgent() {
        return RequestContext.isAgent() ? RequestContext.agentName() : null;
    }

    private List<Pair> scopedPairs() {
        String me = currentAgent();
        return me == null ? pairs.findAll() : pairs.findByAgent(me);
    }

    /** 越权访问拦截：红娘只能访问本人负责的牵线/会员 */
    private void assertOwned(Member m) {
        String me = currentAgent();
        if (me != null && m.getAgent() != null && !me.equals(m.getAgent())) {
            throw BizException.forbidden("无权访问该会员");
        }
    }

    private void assertOwned(Pair p) {
        String me = currentAgent();
        if (me != null && p.getAgent() != null && !me.equals(p.getAgent())) {
            throw BizException.forbidden("无权操作该牵线");
        }
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }

    private static int nz(Integer i) {
        return i == null ? 0 : i;
    }

    /* ---- 1-D 反馈结构化辅助 ---- */

    private static String asStr(Object o, String dflt) {
        return o == null ? dflt : String.valueOf(o);
    }

    private static Integer asInt(Object o, Integer dflt) {
        if (o == null) return dflt;
        if (o instanceof Number n) return n.intValue();
        try { return Integer.parseInt(String.valueOf(o).trim()); } catch (Exception e) { return dflt; }
    }

    @SuppressWarnings("unchecked")
    private static List<String> asList(Object o) {
        List<String> out = new ArrayList<>();
        if (o == null) return out;
        if (o instanceof List<?> l) {
            for (Object x : l) if (x != null && !String.valueOf(x).isBlank()) out.add(String.valueOf(x).trim());
        } else if (!String.valueOf(o).isBlank()) {
            for (String s : String.valueOf(o).split("[,，、]")) {
                String t = s.trim();
                if (!t.isEmpty()) out.add(t);
            }
        }
        return out;
    }

    /** 从满意度 + 卡点提炼可沉淀到会员的偏好标签（影响后续匹配权重） */
    private static List<String> derivePreferences(Integer satisfaction, String kind, List<String> blockers) {
        List<String> prefs = new ArrayList<>();
        if (satisfaction != null && satisfaction >= 4) prefs.add("反馈积极");
        if (blockers != null) {
            for (String b : blockers) {
                switch (b) {
                    case "异地顾虑" -> prefs.add("偏好同城");
                    case "节奏太快" -> prefs.add("偏好慢节奏");
                    case "年龄差距" -> prefs.add("看重年龄相仿");
                    case "经济差距" -> prefs.add("看重经济匹配");
                    case "学历差距" -> prefs.add("看重学历相仿");
                }
            }
        }
        return prefs.stream().distinct().toList();
    }

    /** 把偏好标签追加到会员 tags（去重，不覆盖既有契合标签） */
    private void appendTags(String memberId, List<String> prefs) {
        if (memberId == null || prefs.isEmpty()) return;
        members.findById(memberId).ifPresent(m -> {
            Set<String> set = new LinkedHashSet<>(tagSet(m.getTags()));
            set.addAll(prefs);
            m.setTags(String.join(",", set));
            members.save(m);
        });
    }
}
