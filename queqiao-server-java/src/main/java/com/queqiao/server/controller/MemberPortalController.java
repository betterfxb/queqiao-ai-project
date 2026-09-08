package com.queqiao.server.controller;

import com.queqiao.server.common.BizException;
import com.queqiao.server.common.Ids;
import com.queqiao.server.entity.Activity;
import com.queqiao.server.entity.ActivitySignup;
import com.queqiao.server.entity.Member;
import com.queqiao.server.entity.Pair;
import com.queqiao.server.entity.VerifyTask;
import com.queqiao.server.repo.ActivityRepository;
import com.queqiao.server.repo.ActivitySignupRepository;
import com.queqiao.server.repo.MemberRepository;
import com.queqiao.server.repo.PairRepository;
import com.queqiao.server.repo.VerifyTaskRepository;
import com.queqiao.server.security.RequestContext;
import com.queqiao.server.service.MemberPushService;
import com.queqiao.server.service.PointsService;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

/**
 * 会员端（MEMBER 角色）门户接口：本人资料、推给我的牵线、可报名活动、我的认证状态。
 * 所有接口均按当前登录会员的 memberId 收敛数据范围，会员仅能访问自己。
 */
@RestController
@RequestMapping("/api/member")
public class MemberPortalController {

    private final MemberRepository members;
    private final PairRepository pairs;
    private final ActivityRepository activities;
    private final ActivitySignupRepository signups;
    private final VerifyTaskRepository verifyTasks;
    private final MemberPushService push;
    private final PointsService points;

    public MemberPortalController(MemberRepository members, PairRepository pairs,
                                  ActivityRepository activities, ActivitySignupRepository signups,
                                  VerifyTaskRepository verifyTasks, MemberPushService push,
                                  PointsService points) {
        this.members = members;
        this.pairs = pairs;
        this.activities = activities;
        this.signups = signups;
        this.verifyTasks = verifyTasks;
        this.push = push;
        this.points = points;
    }

    private String me() {
        // 会员专属：未登录→401，已登录但非会员（ADMIN/AGENT）→403
        RequestContext.requireMember();
        return RequestContext.currentMemberId();
    }

    /** 本人资料（会员看自己，敏感字段原文可见） */
    @GetMapping("/me")
    public Member meProfile() {
        return members.findById(me()).orElseThrow(() -> BizException.notFound("会员"));
    }

    /** 推给我的牵线（非终态），附带对方画像摘要与我的表态 */
    @GetMapping("/pairs")
    public List<Map<String, Object>> myPairs() {
        String me = me();
        Set<String> open = Set.of("待确认", "牵线中", "进行中", "双方同意", "已约见");
        List<Pair> list = pairs.findByMaleIdOrFemaleId(me, me).stream()
                .filter(p -> open.contains(p.getStatus())).toList();
        List<Map<String, Object>> res = new ArrayList<>();
        for (Pair p : list) {
            boolean iAmMale = me.equals(p.getMaleId());
            Member other = members.findById(iAmMale ? p.getFemaleId() : p.getMaleId()).orElse(null);
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("pair", p);
            if (other != null) {
                Map<String, Object> o = new LinkedHashMap<>();
                o.put("id", other.getId());
                o.put("name", other.getName());
                o.put("gender", other.getGender());
                o.put("age", other.getAge());
                o.put("city", other.getCity());
                o.put("education", other.getEducation());
                o.put("job", other.getJob());
                o.put("avatarBg", other.getAvatarBg());
                o.put("tags", other.getTags());
                o.put("bio", other.getBio());
                m.put("other", o);
            }
            m.put("iAmMale", iAmMale);
            m.put("myResponseKind", iAmMale ? p.getMaleResponseKind() : p.getFemaleResponseKind());
            res.add(m);
        }
        return res;
    }

    /** 会员对牵线表态：接受 / 暂不（拒绝），并通知对方 */
    @PostMapping("/pairs/{id}/respond")
    @Transactional
    public Map<String, Object> respond(@PathVariable String id, @RequestBody Map<String, String> body) {
        String me = me();
        Pair p = pairs.findById(id).orElseThrow(() -> BizException.notFound("牵线"));
        boolean iAmMale = me.equals(p.getMaleId());
        boolean iAmFemale = me.equals(p.getFemaleId());
        if (!iAmMale && !iAmFemale) throw BizException.forbidden("无权操作该牵线");
        String kind = "reject".equals(body.getOrDefault("kind", "success")) ? "reject" : "success";
        String note = body.get("note");
        if (iAmMale) { p.setMaleResponse(note); p.setMaleResponseKind(kind); }
        else { p.setFemaleResponse(note); p.setFemaleResponseKind(kind); }
        if ("success".equals(p.getMaleResponseKind()) && "success".equals(p.getFemaleResponseKind())) {
            p.setStatus("双方同意");
            p.setStage("安排见面");
        }
        pairs.save(p);
        String otherId = iAmMale ? p.getFemaleId() : p.getMaleId();
        if (otherId != null) {
            push.push(otherId, Map.of("type", "pair-response", "pairId", id, "kind", kind,
                    "text", "对方" + ("success".equals(kind) ? "已接受牵线" : "暂未接受")));
        }
        return Map.of("ok", true, "pair", p);
    }

    /** 可报名活动列表（含我是否已报名） */
    @GetMapping("/activities")
    public List<Map<String, Object>> myActivities() {
        String me = me();
        LocalDate today = LocalDate.now();
        Set<String> closed = Set.of("已结束", "已取消", "已关闭");
        List<Activity> list = activities.findByOrderByDateDesc().stream()
                .filter(a -> a.getDate() != null && !a.getDate().isBefore(today))
                .filter(a -> a.getStatus() == null || !closed.contains(a.getStatus()))
                .toList();
        List<Map<String, Object>> res = new ArrayList<>();
        for (Activity a : list) {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("activity", a);
            m.put("enrolled", signups.existsByActivityIdAndMemberId(a.getId(), me));
            res.add(m);
        }
        return res;
    }

    /** 报名活动（唯一约束避免重复） */
    @PostMapping("/activities/{id}/enroll")
    @Transactional
    public Map<String, Object> enroll(@PathVariable String id) {
        String me = me();
        Activity a = activities.findById(id).orElseThrow(() -> BizException.notFound("活动"));
        if (signups.existsByActivityIdAndMemberId(id, me)) {
            return Map.of("ok", true, "already", true);
        }
        Member m = members.findById(me).orElseThrow(() -> BizException.notFound("会员"));
        ActivitySignup s = new ActivitySignup();
        s.setActivityId(id);
        s.setMemberId(me);
        s.setMemberName(m.getName());
        s.setGender(m.getGender());
        s.setStatus("已报名");
        s.setPaid(false);
        s.setSignedAt(LocalDateTime.now());
        signups.save(s);
        a.setSigned((a.getSigned() == null ? 0 : a.getSigned()) + 1);
        activities.save(a);
        return Map.of("ok", true);
    }

    /** 我的认证状态总览 + 历史 */
    @GetMapping("/verify")
    public Map<String, Object> myVerify() {
        String me = me();
        Member m = members.findById(me).orElseThrow(() -> BizException.notFound("会员"));
        Map<String, Object> res = new LinkedHashMap<>();
        res.put("verified", m.getVerified());
        res.put("verifyRealName", m.getVerifyRealName());
        res.put("verifyEducation", m.getVerifyEducation());
        res.put("verifyAsset", m.getVerifyAsset());
        res.put("verifyRealPerson", m.getVerifyRealPerson());
        res.put("history", verifyTasks.findByMemberIdOrderByCreatedAtDesc(me));
        return res;
    }

    /** 我的积分（累计 / 等级 / 近 20 条流水，含推荐成功奖励）。2-B 裂变奖励明细数据源。 */
    @GetMapping("/points")
    public Map<String, Object> myPoints() {
        return points.of(me());
    }
}
