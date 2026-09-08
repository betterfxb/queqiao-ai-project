package com.queqiao.server.controller;

import com.queqiao.server.common.BizException;
import com.queqiao.server.common.Ids;
import com.queqiao.server.entity.*;
import com.queqiao.server.repo.*;
import com.queqiao.server.security.RequestContext;
import com.queqiao.server.service.LogService;
import com.queqiao.server.service.SatisfactionService;
import com.queqiao.server.service.SettingService;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 营销自动化：按持久化规则自动生成消息 / 日程，形成真实业务闭环。
 * - 规则存库（AutomationRule），重启不丢、可启停可编辑；
 * - 执行幂等（dedupKey 防重复造数据）；
 * - 阈值来自系统设置（业务设置·流失/续费提醒天数），可配置；
 * - 读取需主管及以上，启停/执行需管理员。
 */
@RestController
@RequestMapping("/api/automation")
public class AutomationController {

    private final MemberRepository members;
    private final PairRepository pairs;
    private final CalendarEventRepository events;
    private final ConversationRepository conversations;
    private final MessageItemRepository messages;
    private final ActivityRepository activities;
    private final AutomationRuleRepository rulesRepo;
    private final SettingService settings;
    private final LogService logs;
    private final SatisfactionService satisfaction;

    private static final Set<String> TERMINAL = Set.of("已脱单", "已流失", "已终止", "已暂停");

    public AutomationController(MemberRepository members, PairRepository pairs,
                                CalendarEventRepository events, ConversationRepository conversations,
                                MessageItemRepository messages, ActivityRepository activities,
                                AutomationRuleRepository rulesRepo, SettingService settings, LogService logs,
                                SatisfactionService satisfaction) {
        this.members = members;
        this.pairs = pairs;
        this.events = events;
        this.conversations = conversations;
        this.messages = messages;
        this.activities = activities;
        this.rulesRepo = rulesRepo;
        this.settings = settings;
        this.logs = logs;
        this.satisfaction = satisfaction;
    }

    /** 规则清单（主管及以上可读） */
    @GetMapping("/rules")
    public List<Map<String, Object>> rules() {
        RequestContext.requireManager();
        return rulesRepo.findAll().stream().map(this::toMap).collect(Collectors.toList());
    }

    /** 启停规则（管理员） */
    @PostMapping("/rules/{id}/toggle")
    public Map<String, Object> toggle(@PathVariable String id, @RequestBody Map<String, Boolean> body) {
        RequestContext.requireAdmin();
        AutomationRule r = rulesRepo.findById(id).orElseThrow(() -> BizException.notFound("自动化规则"));
        boolean on = body.getOrDefault("enabled", !r.isEnabled());
        r.setEnabled(on);
        rulesRepo.save(r);
        return Map.of("id", id, "enabled", on);
    }

    /** 执行规则（真实写库：消息 + 日程；幂等防重复） */
    @PostMapping("/run")
    public Map<String, Object> run(@RequestBody Map<String, String> body) {
        RequestContext.requireAdmin();
        String id = body.get("ruleId");
        AutomationRule rule = rulesRepo.findById(id).orElse(null);
        if (rule == null) return Map.of("ok", false, "reason", "规则不存在");
        if (!rule.isEnabled()) return Map.of("ok", false, "reason", "规则未启用");

        LocalDate today = LocalDate.now();
        int created = 0, skipped = 0;
        List<String> detail = new ArrayList<>();

        if ("renewal_due".equals(id)) {
            int renewalDays = settings.getInt("biz.renewalDays", 15);
            for (Member m : members.findAll()) {
                if (TERMINAL.contains(nz(m.getStatus()))) continue;
                LocalDate exp = m.getExpire() != null
                        ? m.getExpire()
                        : (m.getCreatedAt() != null ? m.getCreatedAt().toLocalDate().plusYears(1) : today.plusDays(30));
                long left = ChronoUnit.DAYS.between(today, exp);
                if (left >= 0 && left <= renewalDays) {
                    if (genRenewal(m, exp, left)) created += 2; else skipped++;
                }
            }
        } else if ("churn_followup".equals(id)) {
            int churnDays = settings.getInt("biz.churnDays", 14);
            for (Member m : members.findAll()) {
                if (TERMINAL.contains(nz(m.getStatus()))) continue;
                LocalDate lf = m.getLastFollowUp() != null
                        ? m.getLastFollowUp()
                        : (m.getUpdatedAt() != null ? m.getUpdatedAt().toLocalDate() : today);
                long days = ChronoUnit.DAYS.between(lf, today);
                if (days >= churnDays || "差评".equals(m.getFeedback())) {
                    if (genFollowup(m, days)) created += 2; else skipped++;
                }
            }
        } else if ("new_match_push".equals(id)) {
            for (Pair p : pairs.findAll()) {
                if (!"待确认".equals(p.getStatus())) continue;
                if (p.getCreatedAt() != null && p.getCreatedAt().isAfter(today.atStartOfDay().minusDays(7))) {
                    if (pushToAgent(p)) created += 2; else skipped++;
                }
            }
        } else if ("activity_notify".equals(id)) {
            for (Activity a : activities.findByDateBetween(today, today.plusDays(1))) {
                if ("已结束".equals(a.getStatus())) continue;
                if (genActivityNotify(a)) created += 1; else skipped++;
            }
            if (detail.isEmpty()) detail.add("活动提醒：近 1 天无进行中活动，跳过");
        }

        rule.setLastRunAt(today.toString());
        rule.setLastCreated(created);
        rulesRepo.save(rule);
        logs.record("营销自动化执行", id, "新建动作 " + created + " 项，跳过重复 " + skipped + " 项");
        return Map.of("ok", true, "ruleId", id, "created", created, "skipped", skipped, "detail", detail);
    }

    /**
     * 1-F 预警→动作自动编排：针对高流失风险会员，按规则自动生成跟进（会话 + 日程）并指派其红娘。
     * 运营仅复核，幂等（同会员同日只生成一次，避免重复打扰）。
     */
    @PostMapping("/apply")
    public Map<String, Object> apply(@RequestBody Map<String, String> body) {
        RequestContext.requireManager();
        String memberId = body.get("memberId");
        if (memberId == null || memberId.isBlank()) throw new IllegalArgumentException("memberId 不能为空");
        Member m = members.findById(memberId).orElseThrow(() -> BizException.notFound("会员不存在：" + memberId));
        String key = "auto_followup:" + memberId + ":" + LocalDate.now();
        if (events.existsByDedupKey(key)) {
            return Map.of("ok", true, "dup", true, "msg", "今日已为该会员生成自动跟进", "agent", nz(m.getAgent()));
        }
        String content = "【预测流失·自动跟进】" + m.getName() + " 14 天内流失风险偏高，请尽快电话/微信沟通，调整推荐策略。";
        createConversation(m, "预测流失自动跟进", content, key);
        createEvent(m, "预测流失自动跟进", LocalDate.now().plusDays(1), content, key);
        logs.record("预警自动编排", memberId, "生成预测流失跟进并指派红娘 " + nz(m.getAgent()));
        return Map.of("ok", true, "memberId", memberId, "agent", nz(m.getAgent()), "type", "预测流失自动跟进", "dedupKey", key);
    }

    /**
     * 3-D 满意度闭环·接入 smart-ops 自动化：扫描近 N 天低分反馈，自动建跟进 + 红娘改进建议（幂等）。
     */
    @PostMapping("/satisfaction-scan")
    public Map<String, Object> satisfactionScan(@RequestBody(required = false) Map<String, Object> body) {
        int days = body == null ? 30 : asInt(body.get("days"), 30);
        if (days <= 0 || days > 365) days = 30;
        return satisfaction.scanLowSatisfaction(days);
    }

    private boolean genRenewal(Member m, LocalDate exp, long left) {
        String key = "renewal_due:" + m.getId() + ":" + exp;
        if (events.existsByDedupKey(key)) return false;
        String content = "尊敬的 " + m.getName() + "，您的会员服务将于 " + exp + " 到期（剩 " + left
                + " 天），请及时续费以免影响牵线进度。";
        createConversation(m, "会员续费提醒", content, key);
        createEvent(m, "会员续费提醒", exp, content, key);
        return true;
    }

    private boolean genFollowup(Member m, long days) {
        String key = "churn_followup:" + m.getId() + ":" + LocalDate.now();
        if (events.existsByDedupKey(key)) return false;
        String content = "【流失预警】" + m.getName() + " 已 " + days + " 天未跟进，请尽快电话或微信沟通，调整推荐策略。";
        createConversation(m, "流失跟进任务", content, key);
        createEvent(m, "流失跟进任务", LocalDate.now().plusDays(1), content, key);
        return true;
    }

    private boolean pushToAgent(Pair p) {
        String key = "new_match_push:" + p.getId() + ":" + LocalDate.now();
        if (events.existsByDedupKey(key)) return false;
        String content = "【新匹配待推进】" + p.getMaleName() + " × " + p.getFemaleName()
                + " 已建立牵线，请尽快安排首次沟通。";
        Conversation c = new Conversation();
        c.setId(Ids.biz("C"));
        c.setName("牵线推进：" + p.getMaleName() + "×" + p.getFemaleName());
        c.setMemberId(p.getId());
        c.setKind("system");
        c.setAgent(p.getAgent());
        c.setPreview(content);
        c.setTimeAgo("刚刚");
        c.setUnread(1);
        c.setOnline(false);
        c.setAvatarBg("linear-gradient(135deg,#F4C6D0,#A8667D)");
        c.setDedupKey(key);
        c.setLastAt(LocalDateTime.now());
        c.setCreatedAt(LocalDateTime.now());
        conversations.save(c);
        MessageItem msg = new MessageItem();
        msg.setConversationId(c.getId());
        msg.setSender("system");
        msg.setContent(content);
        msg.setType("text");
        msg.setReadFlag(false);
        msg.setCreatedAt(LocalDateTime.now());
        messages.save(msg);
        // 同时写入日程便于红娘跟进
        createEventRaw("牵线推进", LocalDate.now().plusDays(1), p.getAgent(), content, key + ":evt", p.getMaleName() + "×" + p.getFemaleName());
        return true;
    }

    private boolean genActivityNotify(Activity a) {
        String key = "activity_notify:" + a.getId() + ":" + a.getDate();
        if (events.existsByDedupKey(key)) return false;
        String content = "【活动开场提醒】" + a.getTitle() + " 将于 " + a.getDate() + " " + nz(a.getTime())
                + " 开始，请提前准备物料与签到。";
        createEventRaw("活动开场提醒", a.getDate(), a.getAgent(), content, key, a.getTitle());
        return true;
    }

    private void createConversation(Member m, String title, String content, String key) {
        Conversation c = new Conversation();
        c.setId(Ids.biz("C"));
        c.setName(m.getName());
        c.setMemberId(m.getId());
        c.setKind("member");
        c.setAgent(m.getAgent());
        c.setPreview(content);
        c.setTimeAgo("刚刚");
        c.setUnread(1);
        c.setOnline(false);
        c.setAvatarBg(m.getAvatarBg());
        c.setDedupKey(key);
        c.setLastAt(LocalDateTime.now());
        c.setCreatedAt(LocalDateTime.now());
        conversations.save(c);
        MessageItem msg = new MessageItem();
        msg.setConversationId(c.getId());
        msg.setSender("system");
        msg.setContent(content);
        msg.setType("text");
        msg.setReadFlag(false);
        msg.setCreatedAt(LocalDateTime.now());
        messages.save(msg);
    }

    private void createEvent(Member m, String title, LocalDate date, String remark, String key) {
        createEventRaw(title + " · " + m.getName(), date, m.getAgent(), remark, key, m.getName());
    }

    private void createEventRaw(String title, LocalDate date, String agent, String remark, String key, String memberNames) {
        CalendarEvent e = new CalendarEvent();
        e.setId(Ids.biz("EV"));
        e.setTitle(title);
        e.setType(title);
        e.setDate(date);
        e.setStartTime("10:00");
        e.setEndTime("10:15");
        e.setPlace("线上");
        e.setMemberNames(memberNames);
        e.setAgent(agent);
        e.setStatus("待开始");
        e.setRemark(remark);
        e.setDedupKey(key);
        e.setCreatedAt(LocalDateTime.now());
        e.setUpdatedAt(LocalDateTime.now());
        events.save(e);
    }

    private Map<String, Object> toMap(AutomationRule r) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", r.getId());
        m.put("name", r.getName());
        m.put("desc", r.getDesc());
        m.put("scope", r.getScope());
        m.put("enabled", r.isEnabled());
        m.put("lastRun", r.getLastRunAt());
        m.put("lastCreated", r.getLastCreated());
        m.put("cron", r.getCron());
        return m;
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }

    private static int asInt(Object v, int def) {
        if (v == null) return def;
        if (v instanceof Number n) return n.intValue();
        try {
            return Integer.parseInt(String.valueOf(v).trim());
        } catch (Exception e) {
            return def;
        }
    }
}
