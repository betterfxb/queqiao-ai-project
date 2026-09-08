package com.queqiao.server.service;

import com.queqiao.server.common.BizException;
import com.queqiao.server.common.Ids;
import com.queqiao.server.entity.Member;
import com.queqiao.server.entity.MemberPoints;
import com.queqiao.server.entity.PointLog;
import com.queqiao.server.repo.MemberPointsRepository;
import com.queqiao.server.repo.MemberRepository;
import com.queqiao.server.repo.PointLogRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;

/**
 * 会员积分体系（1-A）。
 *
 * 积分类型与分值（可随业务调整）：
 *  - 完善资料 PROFILE_COMPLETE +20（一次性，资料从"不完整"变"完整"时发放）
 *  - 约见达成 MEETING +10（每次约见，按牵线 ID 幂等）
 *  - 牵线成功 MATCH_SUCCESS +100（每次成功，按牵线 ID 幂等，双方各计）
 *  - 完成测评 QUIZ_COMPLETE +10（一次性，完成情感性格测评，复用 1-B）
 *
 * 设计要点：
 *  - 累计与等级去范式化存储在 MemberPoints，读取 / 排行零聚合；
 *  - 发放全部经 PointLog 留痕，且按 (memberId, type, refId) 幂等，避免重复发放；
 *  - 越权 / 会员不存在由调用方（控制器）先行拦截，本服务仅做积分域内的幂等与累加。
 */
@Service
public class PointsService {

    /** 积分类型 → 规则（分值 + 文案 + 是否一次性） */
    public enum Rule {
        PROFILE_COMPLETE("完善资料", 20, true),
        MEETING("约见达成", 10, false),
        MATCH_SUCCESS("牵线成功", 100, false),
        QUIZ_COMPLETE("完成测评", 10, true);

        final String label;
        final int points;
        final boolean once;

        Rule(String label, int points, boolean once) {
            this.label = label;
            this.points = points;
            this.once = once;
        }

        public static Rule of(String t) {
            if (t == null) return null;
            try {
                return valueOf(t);
            } catch (Exception e) {
                return null;
            }
        }
    }

    /** 等级阈值（升序）：达到阈值即授予该等级 */
    private static final int[] LEVEL_THRESHOLD = {0, 100, 300, 700, 1500};
    private static final String[] LEVEL_NAME = {"见习红娘", "红娘助手", "资深红娘", "金牌红娘", "首席红娘"};

    private final MemberPointsRepository pointsRepo;
    private final PointLogRepository logRepo;
    private final MemberRepository members;
    private final LogService logs;
    private final SettingService settings;

    public PointsService(MemberPointsRepository pointsRepo, PointLogRepository logRepo,
                         MemberRepository members, LogService logs, SettingService settings) {
        this.pointsRepo = pointsRepo;
        this.logRepo = logRepo;
        this.members = members;
        this.logs = logs;
        this.settings = settings;
    }

    /** 发放积分（幂等）。返回 null 表示被幂等拦截或未发放。 */
    @Transactional
    public PointLog earn(String memberId, String type, String refId, String operator, String remark) {
        Rule rule = Rule.of(type);
        if (rule == null) throw new BizException("未知积分类型：" + type);
        Member m = members.findById(memberId).orElse(null);
        if (m == null) return null;

        // 幂等去重键：一次性类型按会员去重；事件型按 (会员, 业务单据) 去重
        String dedupeRef = rule.once ? memberId : refId;
        if (dedupeRef != null && logRepo.existsByMemberIdAndTypeAndRefId(memberId, type, dedupeRef)) {
            return null;
        }

        PointLog log = new PointLog();
        log.setId(Ids.biz("PL"));
        log.setMemberId(memberId);
        log.setMemberName(m.getName());
        log.setType(type);
        log.setTypeLabel(rule.label);
        log.setDelta(rule.points);
        log.setRefId(refId);
        log.setOperator(operator == null ? logs.currentUserName() : operator);
        log.setRemark(remark);
        log.setCreatedAt(LocalDateTime.now());
        logRepo.save(log);

        MemberPoints mp = pointsRepo.findByMemberId(memberId).orElse(null);
        if (mp == null) {
            mp = new MemberPoints();
            mp.setId(Ids.biz("MP"));
            mp.setMemberId(memberId);
            mp.setTotal(0);
        }
        mp.setMemberName(m.getName());
        mp.setTotal(mp.getTotal() + rule.points);
        mp.setLevel(levelOf(mp.getTotal()));
        mp.setUpdatedAt(LocalDateTime.now());
        pointsRepo.save(mp);

        logs.record("会员积分", memberId, m.getName() + "「" + rule.label + "」+" + rule.points);
        return log;
    }

    /**
     * 推荐裂变积分（2-B 全民红娘）：类型固定 REFERRAL_SUCCESS，
     * 分值取自配置 referral.pointReward（默认 50），按 (memberId, 类型, refId) 幂等，避免重复发放。
     */
    @Transactional
    public PointLog earnReferral(String memberId, String refId, String operator, String remark) {
        Member m = members.findById(memberId).orElse(null);
        if (m == null) return null;
        if (logRepo.existsByMemberIdAndTypeAndRefId(memberId, "REFERRAL_SUCCESS", refId)) return null;
        int pts = settings.getInt("referral.pointReward", 50);
        PointLog log = new PointLog();
        log.setId(Ids.biz("PL"));
        log.setMemberId(memberId);
        log.setMemberName(m.getName());
        log.setType("REFERRAL_SUCCESS");
        log.setTypeLabel("推荐成功");
        log.setDelta(pts);
        log.setRefId(refId);
        log.setOperator(operator == null ? logs.currentUserName() : operator);
        log.setRemark(remark);
        log.setCreatedAt(LocalDateTime.now());
        logRepo.save(log);

        MemberPoints mp = pointsRepo.findByMemberId(memberId).orElse(null);
        if (mp == null) {
            mp = new MemberPoints();
            mp.setId(Ids.biz("MP"));
            mp.setMemberId(memberId);
            mp.setTotal(0);
        }
        mp.setMemberName(m.getName());
        mp.setTotal(mp.getTotal() + pts);
        mp.setLevel(levelOf(mp.getTotal()));
        mp.setUpdatedAt(LocalDateTime.now());
        pointsRepo.save(mp);

        logs.record("会员积分", memberId, m.getName() + "「推荐成功」+" + pts);
        return log;
    }

    /** 查询会员积分（无记录返回零值默认），含最近 20 条流水 */
    public Map<String, Object> of(String memberId) {
        MemberPoints mp = pointsRepo.findByMemberId(memberId).orElse(null);
        Map<String, Object> res = new LinkedHashMap<>();
        if (mp == null) {
            res.put("memberId", memberId);
            res.put("total", 0);
            res.put("level", levelOf(0));
            res.put("updatedAt", null);
        } else {
            res.put("memberId", mp.getMemberId());
            res.put("total", mp.getTotal());
            res.put("level", mp.getLevel());
            res.put("updatedAt", mp.getUpdatedAt());
        }
        res.put("logs", logRepo.findByMemberIdOrderByCreatedAtDesc(memberId).stream()
                .limit(20).map(this::toMap).toList());
        return res;
    }

    /** 积分排行榜（按累计积分降序，取前 limit） */
    public List<Map<String, Object>> leaderboard(int limit) {
        List<MemberPoints> all = pointsRepo.findAll();
        all.sort((a, b) -> Integer.compare(b.getTotal(), a.getTotal()));
        return all.stream().limit(limit <= 0 ? 20 : limit).map(mp -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("memberId", mp.getMemberId());
            m.put("memberName", mp.getMemberName());
            m.put("total", mp.getTotal());
            m.put("level", mp.getLevel());
            return m;
        }).toList();
    }

    /** 积分规则（前端展示用） */
    public List<Map<String, Object>> rules() {
        List<Map<String, Object>> out = new ArrayList<>();
        for (Rule r : Rule.values()) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("type", r.name());
            m.put("label", r.label);
            m.put("points", r.points);
            m.put("once", r.once);
            out.add(m);
        }
        return out;
    }

    private static String levelOf(int total) {
        String result = LEVEL_NAME[0];
        for (int i = 0; i < LEVEL_THRESHOLD.length; i++) {
            if (total >= LEVEL_THRESHOLD[i]) result = LEVEL_NAME[i];
        }
        return result;
    }

    private Map<String, Object> toMap(PointLog l) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", l.getId());
        m.put("memberId", l.getMemberId());
        m.put("memberName", l.getMemberName());
        m.put("type", l.getType());
        m.put("typeLabel", l.getTypeLabel());
        m.put("delta", l.getDelta());
        m.put("refId", l.getRefId());
        m.put("operator", l.getOperator());
        m.put("remark", l.getRemark());
        m.put("createdAt", l.getCreatedAt());
        return m;
    }
}
