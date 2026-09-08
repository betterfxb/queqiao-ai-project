package com.queqiao.server.service;

import com.queqiao.server.common.BizException;
import com.queqiao.server.common.Ids;
import com.queqiao.server.entity.*;
import com.queqiao.server.repo.*;
import com.queqiao.server.security.RequestContext;
import com.queqiao.server.service.LogService;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

/**
 * 满意度闭环（3-D 子模块二）。
 *
 * <p>「bigscreen 低分自动建跟进 + 红娘改进建议」：会员提交低分（≤阈值）牵线反馈时，
 * 系统自动为该会员建一条跟进（含红娘改进建议）并生成回访日程，指派其红娘，形成真实闭环；
 * 另提供 {@code scanLowSatisfaction} 供 smart-ops 自动化批量补扫（幂等）。</p>
 */
@Service
public class SatisfactionService {

    /** 低分阈值：满意度 ≤ 2（满分 5）视为低分，触发闭环 */
    public static final int LOW_SAT = 2;

    private final FollowUpRepository follows;
    private final PairRepository pairs;
    private final PairFeedbackRepository feedbacks;
    private final MemberRepository members;
    private final CalendarEventRepository events;
    private final LogService logs;

    public SatisfactionService(FollowUpRepository follows, PairRepository pairs,
                               PairFeedbackRepository feedbacks, MemberRepository members,
                               CalendarEventRepository events, LogService logs) {
        this.follows = follows;
        this.pairs = pairs;
        this.feedbacks = feedbacks;
        this.members = members;
        this.events = events;
        this.logs = logs;
    }

    /**
     * 反馈落库后调用：低分则自动建跟进 + 红娘改进建议 + 回访日程。
     * 天然幂等——每次反馈只触发一次，且 dedupKey 防重复。
     */
    public boolean onLowScore(PairFeedback fb, Pair p) {
        if (fb.getSatisfaction() == null || fb.getSatisfaction() > LOW_SAT) return false;
        String memberId;
        String memberName;
        if ("female".equals(fb.getSide())) {
            memberId = p.getFemaleId();
            memberName = p.getFemaleName();
        } else {
            memberId = p.getMaleId();
            memberName = p.getMaleName();
        }
        createClosedLoop(fb, p, memberId, memberName, p.getAgent());
        return true;
    }

    /**
     * smart-ops 批量补扫：对最近 days 天内低分反馈自动建跟进（幂等，已处理跳过）。
     */
    public Map<String, Object> scanLowSatisfaction(int days) {
        RequestContext.requireManager();
        LocalDateTime since = LocalDateTime.now().minusDays(days);
        List<PairFeedback> low = feedbacks.findBySatisfactionLessThanEqualAndCreatedAtAfterOrderByCreatedAtDesc(LOW_SAT, since);
        int created = 0, skipped = 0;
        for (PairFeedback fb : low) {
            Pair p = pairs.findById(fb.getPairId()).orElse(null);
            if (p == null) {
                skipped++;
                continue;
            }
            String memberId = "female".equals(fb.getSide()) ? p.getFemaleId() : p.getMaleId();
            String dedup = "lowsat_followup:" + fb.getId();
            if (follows.existsByDedupKey(dedup)) {
                skipped++;
                continue;
            }
            String memberName = "female".equals(fb.getSide()) ? p.getFemaleName() : p.getMaleName();
            createClosedLoop(fb, p, memberId, memberName, p.getAgent());
            created++;
        }
        logs.record("满意度闭环扫描", "scan", "扫描近 " + days + " 天低分反馈 " + low.size() + " 条，新建跟进 " + created + "，跳过 " + skipped);
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("ok", true);
        r.put("scanned", low.size());
        r.put("created", created);
        r.put("skipped", skipped);
        return r;
    }

    private void createClosedLoop(PairFeedback fb, Pair p, String memberId, String memberName, String agent) {
        String dedup = "lowsat_followup:" + fb.getId();
        if (follows.existsByDedupKey(dedup)) return; // 幂等

        String suggestion = matchmakerSuggestion(fb, p);
        FollowUp f = new FollowUp();
        f.setId(Ids.biz("FU"));
        f.setMemberId(memberId);
        f.setAgent(agent);
        f.setType("系统");
        f.setContent(suggestion);
        f.setOutcome("待定");
        f.setNextFollowUp(LocalDate.now().plusDays(2)); // 建议 48h 内回访
        f.setDedupKey(dedup);
        f.setCreatedAt(LocalDateTime.now());
        follows.save(f);

        // 回访日程（指派红娘），写入日历便于跟进
        String evtKey = dedup + ":evt";
        if (!events.existsByDedupKey(evtKey)) {
            CalendarEvent e = new CalendarEvent();
            e.setId(Ids.biz("EV"));
            e.setTitle("低分回访 · " + (memberName == null ? "" : memberName));
            e.setType("低分回访");
            e.setDate(LocalDate.now().plusDays(2));
            e.setStartTime("10:00");
            e.setEndTime("10:15");
            e.setPlace("线上");
            e.setMemberNames(memberName);
            e.setAgent(agent);
            e.setStatus("待开始");
            e.setRemark("满意度闭环：自动建跟进，红娘改进建议见跟进记录");
            e.setDedupKey(evtKey);
            e.setCreatedAt(LocalDateTime.now());
            e.setUpdatedAt(LocalDateTime.now());
            events.save(e);
        }

        logs.record("满意度闭环", memberId, "低分反馈（" + fb.getSatisfaction() + "/5）自动建跟进并指派红娘 " + agent);
    }

    /** 红娘改进建议：基于卡点 + 自由备注 + 满意度，给出可落地改进方向 */
    public String matchmakerSuggestion(PairFeedback fb, Pair p) {
        StringBuilder sb = new StringBuilder();
        sb.append("【红娘改进建议】牵线 ").append(p.getMaleName() == null ? "" : p.getMaleName())
                .append(" × ").append(p.getFemaleName() == null ? "" : p.getFemaleName())
                .append(" 收到低分反馈（满意度 ").append(fb.getSatisfaction()).append("/5）\n");
        List<String> blks = fb.blockerList();
        if (!blks.isEmpty()) {
            sb.append("主要卡点：").append(String.join("、", blks))
                    .append("。建议针对性调整推荐画像与沟通节奏，避免重复踩坑。\n");
        }
        String note = fb.getNote();
        if (note != null && !note.isBlank()) {
            sb.append("会员原话：").append(note).append("\n");
        }
        sb.append("处置建议：48 小时内电话/微信回访复盘匹配契合度，必要时重新评估推荐方向或更换候选。");
        return sb.toString();
    }
}
