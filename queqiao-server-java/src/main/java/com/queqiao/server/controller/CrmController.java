package com.queqiao.server.controller;

import com.queqiao.server.ai.AiClient;
import com.queqiao.server.common.BizException;
import com.queqiao.server.common.Ids;
import com.queqiao.server.entity.CrmFollow;
import com.queqiao.server.entity.CrmLead;
import com.queqiao.server.entity.Member;
import com.queqiao.server.repo.CrmFollowRepository;
import com.queqiao.server.repo.CrmLeadRepository;
import com.queqiao.server.repo.MemberRepository;
import com.queqiao.server.service.LogService;
import com.queqiao.server.service.ReferralService;
import com.queqiao.server.service.SettingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * CRM 线索看板。
 * 前端契约：GET /api/crm/board 返回 [{key,title,cards:CrmCard[]}]。
 * 阶段以中文 title 落库，key 仅用于前端列标识，读写双向兼容旧值。
 */
@RestController
@RequestMapping("/api/crm")
public class CrmController {

    private static final Logger log = LoggerFactory.getLogger(CrmController.class);

    /** 看板列定义：key → 标准中文阶段 */
    private static final LinkedHashMap<String, String> STAGES = new LinkedHashMap<>();
    /** 成交阶段（生产环境应在此硬关联会员） */
    private static final String DEAL_STAGE = "已成交";
    /** 旧值 / 别名 → 标准中文阶段 */
    private static final Map<String, String> ALIAS = new HashMap<>();

    static {
        STAGES.put("new", "新线索");
        STAGES.put("contact", "初次沟通");
        STAGES.put("analysis", "需求分析");
        STAGES.put("match", "匹配推荐");
        STAGES.put("meet", "约见安排");
        STAGES.put("deal", "已成交");

        ALIAS.put("已联系", "初次沟通");
        ALIAS.put("需求确认", "需求分析");
        ALIAS.put("方案推荐", "匹配推荐");
        ALIAS.put("成交", "已成交");
        ALIAS.put("约见", "约见安排");
        STAGES.forEach((k, v) -> { ALIAS.put(k, v); ALIAS.put(v, v); });
    }

    private final CrmLeadRepository leadRepo;
    private final CrmFollowRepository followRepo;
    private final LogService logService;
    private final AiClient ai;
    private final SettingService settings;
    private final ReferralService referralService;
    private final MemberRepository members;

    public CrmController(CrmLeadRepository leadRepo, CrmFollowRepository followRepo,
                         LogService logService, AiClient ai, SettingService settings,
                         ReferralService referralService, MemberRepository members) {
        this.leadRepo = leadRepo;
        this.followRepo = followRepo;
        this.logService = logService;
        this.ai = ai;
        this.settings = settings;
        this.referralService = referralService;
        this.members = members;
    }

    /** 看板数据（六列） */
    @GetMapping("/board")
    public List<Map<String, Object>> board(@RequestParam(required = false) String agent,
                                           @RequestParam(required = false) String keyword) {
        List<CrmLead> all = leadRepo.findAll().stream()
                .filter(l -> !has(agent) || agent.equals(l.getAgent()))
                .filter(l -> !has(keyword) || (l.getName() != null && l.getName().contains(keyword))
                        || (l.getPhone() != null && l.getPhone().contains(keyword)))
                .toList();

        List<Map<String, Object>> cols = new ArrayList<>();
        for (Map.Entry<String, String> e : STAGES.entrySet()) {
            List<Map<String, Object>> cards = all.stream()
                    .filter(l -> e.getValue().equals(norm(l.getStage())))
                    .sorted(Comparator.comparing(CrmLead::getUpdatedAt,
                            Comparator.nullsLast(Comparator.reverseOrder())))
                    .map(CrmController::toCard)
                    .collect(Collectors.toList());
            Map<String, Object> col = new LinkedHashMap<>();
            col.put("key", e.getKey());
            col.put("title", e.getValue());
            col.put("count", cards.size());
            col.put("cards", cards);
            cols.add(col);
        }
        return cols;
    }

    /** 漏斗统计 */
    @GetMapping("/stats")
    public Map<String, Object> stats() {
        List<CrmLead> all = leadRepo.findAll();
        Map<String, Long> byStage = new LinkedHashMap<>();
        STAGES.values().forEach(v -> byStage.put(v,
                all.stream().filter(l -> v.equals(norm(l.getStage()))).count()));
        long deal = byStage.getOrDefault("已成交", 0L);
        long risk = all.stream().filter(l -> "danger".equals(l.getFresh())).count();

        Map<String, Object> m = new LinkedHashMap<>();
        m.put("total", all.size());
        m.put("deal", deal);
        m.put("convRate", all.isEmpty() ? 0 : Math.round(deal * 1000.0 / all.size()) / 10.0);
        m.put("risk", risk);
        m.put("star", all.stream().filter(l -> Boolean.TRUE.equals(l.getStar())).count());
        m.put("byStage", byStage);
        return m;
    }

    /** 线索详情（含跟进记录） */
    @GetMapping("/leads/{id}")
    public Map<String, Object> detail(@PathVariable String id) {
        CrmLead l = leadRepo.findById(id).orElseThrow(() -> BizException.notFound("线索不存在：" + id));
        Map<String, Object> m = toCard(l);
        m.put("agent", l.getAgent());
        m.put("source", l.getSource());
        m.put("remark", l.getRemark());
        m.put("stage", norm(l.getStage()));
        m.put("createdAt", l.getCreatedAt());
        m.put("follows", followRepo.findByLeadIdOrderByCreatedAtDesc(id));
        return m;
    }

    /** 新建线索 */
    @PostMapping("/leads")
    public Map<String, Object> create(@RequestBody Map<String, Object> body) {
        CrmLead l = new CrmLead();
        l.setId(Ids.biz("L"));
        apply(l, body);
        if (!has(l.getStage())) l.setStage("新线索");
        l.setStayDays(0);
        l.setFresh("fresh");
        l.setFreshText("刚刚");
        if (!has(l.getNextAction())) l.setNextAction("待首次沟通");
        l.setCreatedAt(LocalDateTime.now());
        l.setUpdatedAt(LocalDateTime.now());
        leadRepo.save(l);
        logService.record("新建线索", l.getId(), l.getName());
        // 5.3.1 转介绍：线索带推荐人 → 自动登记一条转介绍记录（幂等，失败不阻断建线索）
        if (has(l.getReferrerMemberId()) && has(l.getPhone())) {
            try {
                referralService.register(l.getReferrerMemberId(), "会员", l.getName(),
                        l.getPhone(), l.getId(), "CRM 线索自动登记");
            } catch (Exception e) {
                log.warn("[referral] 线索自动登记转介绍失败 lead={} : {}", l.getId(), e.getMessage());
            }
        }
        return toCard(l);
    }

    @PutMapping("/leads/{id}")
    public Map<String, Object> update(@PathVariable String id, @RequestBody Map<String, Object> body) {
        CrmLead l = leadRepo.findById(id).orElseThrow(() -> BizException.notFound("线索不存在：" + id));
        apply(l, body);
        l.setUpdatedAt(LocalDateTime.now());
        leadRepo.save(l);
        logService.record("编辑线索", id, l.getName());
        return toCard(l);
    }

    /** 拖拽换列：{stage:'match'} 或 {stage:'匹配推荐'} 均可 */
    @PostMapping("/leads/{id}/stage")
    public Map<String, Object> moveStage(@PathVariable String id, @RequestBody Map<String, String> body) {
        CrmLead l = leadRepo.findById(id).orElseThrow(() -> BizException.notFound("线索不存在：" + id));
        String target = norm(body.get("stage"));
        if (target == null) throw new IllegalArgumentException("stage 非法：" + body.get("stage"));
        String from = norm(l.getStage());
        l.setStage(target);
        // 成交阶段：硬关联会员（按手机号匹配已有会员），保证 ROI 归因准确
        if (DEAL_STAGE.equals(target) && l.getMemberId() == null && has(l.getPhone())) {
            members.findByPhone(l.getPhone()).stream().findFirst().ifPresent(m -> {
                l.setMemberId(m.getId());
                l.setMemberName(m.getName());
            });
        }
        l.setStayDays(0);
        l.setFresh("fresh");
        l.setFreshText("刚刚");
        l.setUpdatedAt(LocalDateTime.now());
        leadRepo.save(l);

        CrmFollow f = new CrmFollow();
        f.setLeadId(id);
        f.setType("阶段变更");
        f.setContent(from + " → " + target);
        f.setOperator(logService.currentUserName());
        f.setCreatedAt(LocalDateTime.now());
        followRepo.save(f);

        logService.record("线索阶段变更", id, l.getName() + " " + from + " → " + target);
        return toCard(l);
    }

    /**
     * 线索转化为会员：硬关联 memberId（幂等）。
     * 优先级：指定 memberId → 按手机号匹配已有会员 → 由线索新建会员。
     * 转化后阶段置为「已成交」，供渠道 ROI 按会员精准归因。
     */
    @PostMapping("/leads/{id}/convert")
    public Map<String, Object> convert(@PathVariable String id,
                                       @RequestBody(required = false) Map<String, Object> body) {
        CrmLead l = leadRepo.findById(id).orElseThrow(() -> BizException.notFound("线索不存在：" + id));
        if (l.getMemberId() != null) {
            Map<String, Object> exist = toCard(l);
            exist.put("memberId", l.getMemberId());
            exist.put("memberName", l.getMemberName());
            exist.put("alreadyConverted", true);
            return exist;
        }
        Map<String, Object> b = body == null ? Map.of() : body;
        String reqMemberId = str(b.get("memberId"));
        Member m = null;
        if (has(reqMemberId)) m = members.findById(reqMemberId).orElse(null);
        if (m == null && has(l.getPhone())) {
            m = members.findByPhone(l.getPhone()).stream().findFirst().orElse(null);
        }
        if (m == null) {
            m = new Member();
            m.setId(Ids.biz("M"));
            m.setName(l.getName());
            m.setAge(l.getAge());
            m.setGender(l.getGender());
            m.setPhone(l.getPhone());
            m.setLevel(l.getLevel());
            m.setAgent(l.getAgent());
            m.setStatus("活跃");
            m.setCreatedAt(LocalDateTime.now());
            m.setUpdatedAt(LocalDateTime.now());
            members.save(m);
            logService.record("线索转化新建会员", m.getId(), l.getName());
        }
        l.setMemberId(m.getId());
        l.setMemberName(m.getName());
        l.setStage(DEAL_STAGE);
        l.setUpdatedAt(LocalDateTime.now());
        leadRepo.save(l);
        logService.record("线索转化", id, l.getName() + " → 会员" + m.getId());
        Map<String, Object> res = toCard(l);
        res.put("memberId", m.getId());
        res.put("memberName", m.getName());
        return res;
    }

    /** 标记 / 取消星标 */
    @PostMapping("/leads/{id}/star")
    public Map<String, Object> star(@PathVariable String id, @RequestBody(required = false) Map<String, Object> body) {
        CrmLead l = leadRepo.findById(id).orElseThrow(() -> BizException.notFound("线索不存在：" + id));
        boolean v = body != null && body.containsKey("star")
                ? Boolean.TRUE.equals(body.get("star")) : !Boolean.TRUE.equals(l.getStar());
        l.setStar(v);
        l.setUpdatedAt(LocalDateTime.now());
        leadRepo.save(l);
        return toCard(l);
    }

    @DeleteMapping("/leads/{id}")
    public Map<String, Object> delete(@PathVariable String id) {
        CrmLead l = leadRepo.findById(id).orElseThrow(() -> BizException.notFound("线索不存在：" + id));
        leadRepo.deleteById(id);
        logService.record("删除线索", id, l.getName());
        return Map.of("ok", true);
    }

    /** 跟进记录 */
    @GetMapping("/leads/{id}/follows")
    public List<CrmFollow> follows(@PathVariable String id) {
        return followRepo.findByLeadIdOrderByCreatedAtDesc(id);
    }

    /** 新增跟进 */
    @PostMapping("/leads/{id}/follows")
    public CrmFollow addFollow(@PathVariable String id, @RequestBody Map<String, String> body) {
        CrmLead l = leadRepo.findById(id).orElseThrow(() -> BizException.notFound("线索不存在：" + id));
        String content = body.get("content");
        if (!has(content)) throw new IllegalArgumentException("跟进内容不能为空");
        CrmFollow f = new CrmFollow();
        f.setLeadId(id);
        f.setContent(content);
        f.setType(body.getOrDefault("type", "电话"));
        f.setOperator(logService.currentUserName());
        f.setCreatedAt(LocalDateTime.now());
        followRepo.save(f);

        l.setFresh("fresh");
        l.setFreshText("刚刚");
        l.setStayDays(0);
        l.setUpdatedAt(LocalDateTime.now());
        leadRepo.save(l);

        logService.record("线索跟进", id, l.getName() + "：" + content);
        return f;
    }

    /** AI 下一步动作建议（AI 不可用时走本地规则） */
    @PostMapping("/leads/{id}/next-action")
    public Map<String, Object> nextAction(@PathVariable String id) {
        CrmLead l = leadRepo.findById(id).orElseThrow(() -> BizException.notFound("线索不存在：" + id));
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("name", l.getName());
        payload.put("stage", norm(l.getStage()));
        payload.put("level", l.getLevel());
        payload.put("stayDays", l.getStayDays());
        payload.put("fresh", l.getFresh());
        payload.put("follows", followRepo.findByLeadIdOrderByCreatedAtDesc(id).stream()
                .limit(5).map(CrmFollow::getContent).toList());

        Map<String, Object> res = ai.nextAction(payload);
        if (res != null && res.get("action") != null) {
            res.put("source", "ai");
            return res;
        }
        return localNextAction(l);
    }

    /** 本地规则兜底 */
    private Map<String, Object> localNextAction(CrmLead l) {
        String stage = norm(l.getStage());
        int stay = l.getStayDays() == null ? 0 : l.getStayDays();
        int stale = settings.getInt("biz.leadStaleDays", 7);
        String action;
        String reason;
        String priority = stay >= stale ? "高" : stay >= 3 ? "中" : "低";
        switch (stage == null ? "新线索" : stage) {
            case "新线索" -> { action = "24 小时内首次电话沟通，确认择偶基本诉求"; reason = "线索新鲜度最高，首触时效直接影响转化"; }
            case "初次沟通" -> { action = "整理需求清单并发送服务方案资料"; reason = "已建立联系，需快速推进到需求确认"; }
            case "需求分析" -> { action = "生成 3 位候选人匹配方案并预约讲解"; reason = "需求已明确，进入匹配推荐可提升成单概率"; }
            case "匹配推荐" -> { action = "跟进候选人反馈，锁定 1 位安排见面"; reason = "方案已送达，需推动到约见环节"; }
            case "约见安排" -> { action = "确认见面时间地点并做行前提醒"; reason = "见面履约率是成交前最后一道关口"; }
            default -> { action = "回访满意度并邀请转介绍"; reason = "已成交客户是低成本获客来源"; }
        }
        if (stay >= stale) {
            action = "【流失预警】" + action + "；建议叠加一次专属优惠触达";
            reason = "该线索已停留 " + stay + " 天，超过 " + stale + " 天流失风险显著上升";
        }
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("action", action);
        m.put("reason", reason);
        m.put("priority", priority);
        m.put("source", "local");
        return m;
    }

    private void apply(CrmLead l, Map<String, Object> b) {
        if (b.containsKey("name")) l.setName(str(b.get("name")));
        if (b.containsKey("age")) l.setAge(num(b.get("age")).intValue());
        if (b.containsKey("gender")) l.setGender(str(b.get("gender")));
        if (b.containsKey("level")) l.setLevel(str(b.get("level")));
        if (b.containsKey("phone")) l.setPhone(str(b.get("phone")));
        if (b.containsKey("stage")) l.setStage(norm(str(b.get("stage"))));
        if (b.containsKey("nextAction")) l.setNextAction(str(b.get("nextAction")));
        if (b.containsKey("agent")) l.setAgent(str(b.get("agent")));
        if (b.containsKey("source")) l.setSource(str(b.get("source")));
        if (b.containsKey("remark")) l.setRemark(str(b.get("remark")));
        if (b.containsKey("star")) l.setStar(Boolean.TRUE.equals(b.get("star")));
        // 5.3.5 渠道归因：线索侧打标（渠道 / 活动 / 单条获客成本）
        if (b.containsKey("channel")) l.setChannel(str(b.get("channel")));
        if (b.containsKey("campaignId")) l.setCampaignId(str(b.get("campaignId")));
        if (b.containsKey("cost")) l.setCost(num(b.get("cost")).doubleValue());
        // 5.3.1 转介绍：线索侧记录推荐人
        if (b.containsKey("referrerMemberId")) l.setReferrerMemberId(str(b.get("referrerMemberId")));
        if (b.containsKey("referrerName")) l.setReferrerName(str(b.get("referrerName")));
        // 5.3.5 边界：线索硬关联会员（通常由 convert / 阶段变更自动写入）
        if (b.containsKey("memberId")) l.setMemberId(str(b.get("memberId")));
        if (b.containsKey("memberName")) l.setMemberName(str(b.get("memberName")));
    }

    /** 实体 → 前端 CrmCard，停留天数与新鲜度实时重算 */
    static Map<String, Object> toCard(CrmLead l) {
        long days = l.getUpdatedAt() == null ? 0
                : Math.max(0, Duration.between(l.getUpdatedAt(), LocalDateTime.now()).toDays());
        String fresh = days <= 1 ? "fresh" : days <= 6 ? "warning" : "danger";
        String freshText = days == 0 ? "今天" : days == 1 ? "昨天" : days + "天前";

        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", l.getId());
        m.put("name", l.getName());
        m.put("age", l.getAge() == null ? 0 : l.getAge());
        m.put("gender", l.getGender());
        m.put("level", l.getLevel());
        m.put("phone", mask(l.getPhone()));
        m.put("stayDays", days);
        m.put("fresh", fresh);
        m.put("freshText", freshText);
        m.put("nextAction", l.getNextAction());
        m.put("star", Boolean.TRUE.equals(l.getStar()));
        m.put("memberId", l.getMemberId());
        m.put("memberName", l.getMemberName());
        return m;
    }

    private static String mask(String phone) {
        if (phone == null || phone.length() < 11) return phone;
        return phone.substring(0, 3) + "****" + phone.substring(7);
    }

    private static String norm(String stage) {
        if (stage == null) return null;
        return ALIAS.getOrDefault(stage.trim(), null);
    }

    private static boolean has(String s) { return s != null && !s.isBlank(); }
    private static String str(Object o) { return o == null ? null : String.valueOf(o); }
    private static Number num(Object o) { return o instanceof Number n ? n : Double.parseDouble(String.valueOf(o)); }
}
