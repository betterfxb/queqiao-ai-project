package com.queqiao.server.controller;

import com.queqiao.server.ai.AiClient;
import com.queqiao.server.common.BizException;
import com.queqiao.server.common.Ids;
import com.queqiao.server.entity.FollowUp;
import com.queqiao.server.entity.Member;
import com.queqiao.server.repo.FollowUpRepository;
import com.queqiao.server.repo.MemberRepository;
import com.queqiao.server.security.RequestContext;
import com.queqiao.server.service.LogService;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

/**
 * 会员跟进时间线（B2B 服务过程留痕）。
 * 红娘对本人负责会员记录每一次沟通；系统据此生成跟进摘要与下一步建议（AI，可离线降级）。
 */
@RestController
@RequestMapping("/api")
public class FollowUpController {

    private final FollowUpRepository follows;
    private final MemberRepository members;
    private final AiClient ai;
    private final LogService logs;

    public FollowUpController(FollowUpRepository follows, MemberRepository members, AiClient ai, LogService logs) {
        this.follows = follows;
        this.members = members;
        this.ai = ai;
        this.logs = logs;
    }

    /** 跟进时间线（按时间倒序） */
    @GetMapping("/members/{memberId}/followups")
    public List<Map<String, Object>> list(@PathVariable String memberId) {
        Member m = members.findById(memberId).orElseThrow(() -> BizException.notFound("会员"));
        assertOwned(m);
        List<Map<String, Object>> out = new ArrayList<>();
        for (FollowUp f : follows.findByMemberIdOrderByCreatedAtDesc(memberId)) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", f.getId());
            row.put("type", f.getType());
            row.put("content", f.getContent());
            row.put("outcome", f.getOutcome());
            row.put("agent", f.getAgent());
            row.put("nextFollowUp", f.getNextFollowUp() == null ? null : f.getNextFollowUp().toString());
            row.put("createdAt", f.getCreatedAt());
            row.put("timeAgo", ago(f.getCreatedAt()));
            out.add(row);
        }
        return out;
    }

    /** 新增跟进记录 */
    @PostMapping("/members/{memberId}/followups")
    public Map<String, Object> create(@PathVariable String memberId, @RequestBody Map<String, String> body) {
        Member m = members.findById(memberId).orElseThrow(() -> BizException.notFound("会员"));
        assertOwned(m);
        String content = nz(body.get("content"));
        if (content.isBlank()) throw new BizException("请填写跟进内容");
        if (content.length() > 1000) throw new BizException("跟进内容不能超过 1000 字");

        FollowUp f = new FollowUp();
        f.setId(Ids.biz("FU"));
        f.setMemberId(memberId);
        f.setAgent(RequestContext.get() == null ? m.getAgent() : RequestContext.agentName());
        f.setType(nz(body.getOrDefault("type", "电话")));
        f.setContent(content);
        f.setOutcome(nz(body.getOrDefault("outcome", "待定")));
        String next = nz(body.get("nextFollowUp"));
        if (!next.isBlank()) {
            try {
                f.setNextFollowUp(LocalDate.parse(next));
            } catch (Exception e) {
                throw new BizException("下次跟进日期格式不正确（应为 yyyy-MM-dd）");
            }
        }
        f.setCreatedAt(LocalDateTime.now());
        FollowUp saved = follows.save(f);

        // 回流到会员档案：最近跟进日期（供流失预警）
        m.setLastFollowUp(LocalDate.now());
        members.save(m);

        logs.record("记录跟进", memberId, m.getName() + " · " + f.getType() + "：" + f.getOutcome());

        Map<String, Object> res = new LinkedHashMap<>();
        res.put("id", saved.getId());
        res.put("memberId", memberId);
        res.put("type", saved.getType());
        res.put("content", saved.getContent());
        res.put("outcome", saved.getOutcome());
        res.put("nextFollowUp", saved.getNextFollowUp() == null ? null : saved.getNextFollowUp().toString());
        res.put("createdAt", saved.getCreatedAt());
        return res;
    }

    /** 跟进摘要（AI）：汇总时间线 → 意向判断 + 下一步建议；AI 不可用时本地规则兜底 */
    @GetMapping("/members/{memberId}/followups/summary")
    public Map<String, Object> summary(@PathVariable String memberId) {
        Member m = members.findById(memberId).orElseThrow(() -> BizException.notFound("会员"));
        assertOwned(m);
        List<FollowUp> list = follows.findByMemberIdOrderByCreatedAtAsc(memberId);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("memberId", m.getId());
        payload.put("name", m.getName());
        payload.put("gender", m.getGender());
        payload.put("age", m.getAge());
        payload.put("level", m.getLevel());
        payload.put("lastFollowUp", m.getLastFollowUp() == null ? null : m.getLastFollowUp().toString());
        List<Map<String, Object>> items = new ArrayList<>();
        for (FollowUp f : list) {
            Map<String, Object> it = new LinkedHashMap<>();
            it.put("type", f.getType());
            it.put("content", f.getContent());
            it.put("outcome", f.getOutcome());
            it.put("nextFollowUp", f.getNextFollowUp() == null ? null : f.getNextFollowUp().toString());
            it.put("createdAt", f.getCreatedAt() == null ? null : f.getCreatedAt().toString());
            items.add(it);
        }
        payload.put("follows", items);

        Map<String, Object> r = ai.followupSummary(payload);
        if (r != null) {
            r.put("source", "ai");
            return r;
        }
        return localSummary(m, list);
    }

    /** 本地兜底：按最近意向 + 跟进密度给一句建议 */
    private Map<String, Object> localSummary(Member m, List<FollowUp> list) {
        String latestOutcome = list.isEmpty() ? "无" : nz(list.get(list.size() - 1).getOutcome());
        String typeText = list.isEmpty() ? "无" : nz(list.get(list.size() - 1).getType());
        String next = list.isEmpty() ? null : list.get(list.size() - 1).getNextFollowUp().toString();

        String intention = switch (latestOutcome) {
            case "成交意向" -> "高意向";
            case "无意向" -> "低意向";
            default -> "待定";
        };
        String suggestion;
        if (list.isEmpty()) {
            suggestion = "尚无跟进记录，建议尽快电话回访一次，确认择偶诉求";
        } else if ("成交意向".equals(latestOutcome)) {
            suggestion = "客户意向明确，建议 3 天内约见并推进匹配方案，锁定服务档位";
        } else if ("无意向".equals(latestOutcome)) {
            suggestion = "客户当前无意向，建议暂缓高频打扰，改为每月 1 次轻触达（节日关怀/新资料）";
        } else {
            suggestion = "下次跟进建议安排在 " + (next == null ? "3 天内" : next)
                    + "，通过 " + typeText + " 沟通确认择偶要求并推进匹配";
        }

        Map<String, Object> res = new LinkedHashMap<>();
        res.put("source", "local-fallback");
        res.put("summary", "共跟进 " + list.size() + " 次，最近一次（" + typeText + "）意向：" + latestOutcome);
        res.put("count", list.size());
        res.put("latestType", typeText);
        res.put("latestOutcome", latestOutcome);
        res.put("intention", intention);
        res.put("nextFollowUp", next);
        res.put("suggestion", suggestion);
        res.put("reminders", List.of(
                "本会员当前意向为「" + intention + "」，跟进节奏可据此动态调整",
                "每次跟进请补充沟通要点，便于 AI 持续生成更精准的下一步建议"));
        return res;
    }

    /** 数据范围：红娘只能操作本人负责会员的跟进 */
    private void assertOwned(Member m) {
        if (!RequestContext.isAgent()) return;
        String me = RequestContext.agentName();
        if (me != null && m.getAgent() != null && !me.equals(m.getAgent())) {
            throw BizException.forbidden("无权访问该会员");
        }
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }

    static String ago(LocalDateTime t) {
        if (t == null) return "";
        long min = java.time.Duration.between(t, LocalDateTime.now()).toMinutes();
        if (min < 1) return "刚刚";
        if (min < 60) return min + "分钟前";
        long h = min / 60;
        if (h < 24) return h + "小时前";
        long d = h / 24;
        return d < 30 ? d + "天前" : (d / 30) + "个月前";
    }
}
