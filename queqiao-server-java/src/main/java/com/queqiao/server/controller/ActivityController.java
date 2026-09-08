package com.queqiao.server.controller;

import com.queqiao.server.common.BizException;
import com.queqiao.server.common.Ids;
import com.queqiao.server.common.Money;
import com.queqiao.server.entity.Activity;
import com.queqiao.server.entity.ActivityCheckin;
import com.queqiao.server.entity.ActivityMatch;
import com.queqiao.server.entity.Member;
import com.queqiao.server.entity.ActivitySignup;
import com.queqiao.server.entity.Pair;
import com.queqiao.server.repo.ActivityCheckinRepository;
import com.queqiao.server.repo.ActivityMatchRepository;
import com.queqiao.server.repo.ActivityRepository;
import com.queqiao.server.repo.ActivitySignupRepository;
import com.queqiao.server.repo.MemberRepository;
import com.queqiao.server.repo.PairRepository;
import com.queqiao.server.security.RequestContext;
import com.queqiao.server.service.LogService;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 活动运营模块。
 * 前端契约：GET /api/activities 直接返回 Activity[]（tags 为字符串数组、date 为 yyyy.MM.dd）。
 */
@RestController
@RequestMapping("/api/activities")
public class ActivityController {

    private static final DateTimeFormatter DOT = DateTimeFormatter.ofPattern("yyyy.MM.dd");
    private static final String[] WEEKDAYS = {"周一", "周二", "周三", "周四", "周五", "周六", "周日"};
    /** 合法活动状态白名单（P1 枚举校验） */
    private static final Set<String> STATUSES = Set.of("草稿", "报名中", "进行中", "已满员", "已结束", "已取消");

    private final ActivityRepository activityRepo;
    private final ActivitySignupRepository signupRepo;
    private final MemberRepository memberRepo;
    private final LogService logService;
    private final ActivityCheckinRepository checkinRepo;
    private final ActivityMatchRepository matchRepo;
    private final PairRepository pairRepo;

    public ActivityController(ActivityRepository activityRepo, ActivitySignupRepository signupRepo,
                              MemberRepository memberRepo, LogService logService,
                              ActivityCheckinRepository checkinRepo, ActivityMatchRepository matchRepo,
                              PairRepository pairRepo) {
        this.activityRepo = activityRepo;
        this.signupRepo = signupRepo;
        this.memberRepo = memberRepo;
        this.logService = logService;
        this.checkinRepo = checkinRepo;
        this.matchRepo = matchRepo;
        this.pairRepo = pairRepo;
    }

    /** 活动列表（可按状态 / 关键词过滤），返回前端 Activity[] */
    @GetMapping
    public List<Map<String, Object>> list(@RequestParam(required = false) String status,
                                          @RequestParam(required = false) String keyword,
                                          @RequestParam(required = false) String agent) {
        return activityRepo.findByOrderByDateDesc().stream()
                .filter(a -> !has(status) || status.equals(a.getStatus()))
                .filter(a -> !has(agent) || agent.equals(a.getAgent()))
                .filter(a -> !has(keyword)
                        || (a.getTitle() != null && a.getTitle().contains(keyword))
                        || (a.getPlace() != null && a.getPlace().contains(keyword)))
                .map(ActivityController::toVo)
                .collect(Collectors.toList());
    }

    /** 活动统计卡片（P0-10：计数与求和全部下沉 DB，不再整表加载） */
    @GetMapping("/stats")
    public Map<String, Object> stats() {
        LocalDate now = LocalDate.now();
        LocalDate monthStart = now.withDayOfMonth(1);
        LocalDate monthEnd = monthStart.plusMonths(1);
        List<Object[]> rows = activityRepo.statAggregateRow();
        Object[] agg = rows.isEmpty() ? new Object[]{0, BigDecimal.ZERO, 0} : rows.get(0);
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("total", activityRepo.count());
        m.put("ongoing", activityRepo.countByStatusIn(List.of("报名中", "进行中")));
        m.put("monthCount", activityRepo.countByDateGreaterThanEqualAndDateLessThan(monthStart, monthEnd));
        m.put("signed", agg[0] == null ? 0 : ((Number) agg[0]).intValue());
        m.put("revenue", Money.toDouble(agg[1] == null ? BigDecimal.ZERO : new BigDecimal(agg[1].toString())));
        m.put("expectedPairs", agg[2] == null ? 0 : ((Number) agg[2]).intValue());
        return m;
    }

    /** 活动详情（含报名名单） */
    @GetMapping("/{id}")
    public Map<String, Object> detail(@PathVariable String id) {
        Activity a = activityRepo.findById(id).orElseThrow(() -> BizException.notFound("活动不存在：" + id));
        Map<String, Object> vo = toVo(a);
        vo.put("description", a.getDescription());
        vo.put("signups", signupRepo.findByActivityId(id));
        return vo;
    }

    /** 新建活动 */
    @PostMapping
    public Map<String, Object> create(@RequestBody Map<String, Object> body) {
        Activity a = new Activity();
        a.setId(Ids.biz("EV"));
        apply(a, body);
        a.setSigned(0);
        a.setFemale(0);
        a.setMale(0);
        a.setRevenue(BigDecimal.ZERO);
        if (!has(a.getStatus())) a.setStatus("草稿");
        a.setCreatedAt(LocalDateTime.now());
        a.setUpdatedAt(LocalDateTime.now());
        activityRepo.save(a);
        logService.record("新建活动", a.getId(), a.getTitle());
        return toVo(a);
    }

    /** 编辑活动 */
    @PutMapping("/{id}")
    @Transactional
    public Map<String, Object> update(@PathVariable String id, @RequestBody Map<String, Object> body) {
        Activity a = activityRepo.findById(id).orElseThrow(() -> BizException.notFound("活动不存在：" + id));
        assertOwned(a);
        apply(a, body);
        a.setUpdatedAt(LocalDateTime.now());
        activityRepo.save(a);
        logService.record("编辑活动", id, a.getTitle());
        return toVo(a);
    }

    /** 变更状态：报名中 / 进行中 / 已满员 / 已结束 / 草稿 */
    @PostMapping("/{id}/status")
    @Transactional
    public Map<String, Object> changeStatus(@PathVariable String id, @RequestBody Map<String, String> body) {
        Activity a = activityRepo.findById(id).orElseThrow(() -> BizException.notFound("活动不存在：" + id));
        assertOwned(a);
        String status = body.get("status");
        if (!has(status)) throw new IllegalArgumentException("status 不能为空");
        if (!STATUSES.contains(status)) {
            throw new BizException("非法的活动状态：" + status + "，可选：" + String.join("/", STATUSES));
        }
        a.setStatus(status);
        a.setUpdatedAt(LocalDateTime.now());
        activityRepo.save(a);
        logService.record("活动状态变更", id, a.getTitle() + " → " + status);
        return toVo(a);
    }

    @DeleteMapping("/{id}")
    @Transactional
    public Map<String, Object> delete(@PathVariable String id) {
        Activity a = activityRepo.findById(id).orElseThrow(() -> BizException.notFound("活动不存在：" + id));
        assertOwned(a);
        activityRepo.deleteById(id);
        logService.record("删除活动", id, a.getTitle());
        return Map.of("ok", true);
    }

    /** 报名名单 */
    @GetMapping("/{id}/signups")
    public List<ActivitySignup> signups(@PathVariable String id) {
        return signupRepo.findByActivityId(id);
    }

    /* ===================== 1-E 现场签到 + 互选 ===================== */

    /** 现场签到（幂等：同活动+会员不重复） */
    @PostMapping("/{id}/checkin")
    @Transactional
    public Map<String, Object> checkin(@PathVariable String id, @RequestBody Map<String, String> body) {
        Activity a = activityRepo.findById(id).orElseThrow(() -> BizException.notFound("活动不存在：" + id));
        String memberId = body.get("memberId");
        if (!has(memberId)) throw new IllegalArgumentException("memberId 不能为空");
        Member m = memberRepo.findById(memberId).orElseThrow(() -> BizException.notFound("会员不存在：" + memberId));
        if (checkinRepo.existsByActivityIdAndMemberId(id, memberId)) {
            return Map.of("ok", true, "dup", true, "msg", "该会员已签到");
        }
        ActivityCheckin c = new ActivityCheckin();
        c.setId(Ids.biz("AC"));
        c.setActivityId(id);
        c.setMemberId(memberId);
        c.setMemberName(m.getName());
        c.setScannedBy(RequestContext.get() != null ? RequestContext.get().userName() : "system");
        c.setAt(LocalDateTime.now());
        checkinRepo.save(c);
        logService.record("活动签到", id, m.getName() + " 签到 " + a.getTitle());
        return Map.of("ok", true, "checkin", c);
    }

    /** 签到名单 */
    @GetMapping("/{id}/checkins")
    public List<ActivityCheckin> checkins(@PathVariable String id) {
        return checkinRepo.findByActivityIdOrderByAtDesc(id);
    }

    /** 现场互选：a 选 b；双向互选成功自动建牵线候选，进入 match 流程 */
    @PostMapping("/{id}/mutual")
    @Transactional
    public Map<String, Object> mutual(@PathVariable String id, @RequestBody Map<String, String> body) {
        Activity a = activityRepo.findById(id).orElseThrow(() -> BizException.notFound("活动不存在：" + id));
        String aId = body.get("a");
        String bId = body.get("b");
        if (!has(aId) || !has(bId) || aId.equals(bId)) throw new BizException("请选择两位不同的会员");
        Member ma = memberRepo.findById(aId).orElseThrow(() -> BizException.notFound("会员不存在：" + aId));
        Member mb = memberRepo.findById(bId).orElseThrow(() -> BizException.notFound("会员不存在：" + bId));
        if (matchRepo.findByActivityIdAndAIdAndBId(id, aId, bId).isPresent()) {
            return Map.of("ok", true, "dup", true, "msg", "已记录该互选");
        }
        boolean mutual = matchRepo.findByActivityIdAndAIdAndBId(id, bId, aId).isPresent();
        ActivityMatch m = new ActivityMatch();
        m.setId(Ids.biz("AM"));
        m.setActivityId(id);
        m.setAId(aId); m.setAName(ma.getName());
        m.setBId(bId); m.setBName(mb.getName());
        m.setMutual(mutual);
        m.setCreatedAt(LocalDateTime.now());
        matchRepo.save(m);

        Map<String, Object> res = new LinkedHashMap<>();
        res.put("ok", true);
        res.put("mutual", mutual);
        if (mutual) {
            // 双向互选成功：标记反向记录并建牵线候选
            ActivityMatch rev = matchRepo.findByActivityIdAndAIdAndBId(id, bId, aId).orElse(null);
            if (rev != null) { rev.setMutual(true); matchRepo.save(rev); }
            Pair p = new Pair();
            p.setId(Ids.biz("P"));
            if ("男".equals(ma.getGender())) {
                p.setMaleId(aId); p.setMaleName(ma.getName());
                p.setFemaleId(bId); p.setFemaleName(mb.getName());
            } else {
                p.setMaleId(bId); p.setMaleName(mb.getName());
                p.setFemaleId(aId); p.setFemaleName(ma.getName());
            }
            p.setStatus("待确认");
            p.setStage("初步推荐");
            p.setAgent(a.getAgent());
            p.setCreatedAt(LocalDateTime.now());
            p.setUpdatedAt(LocalDateTime.now());
            Pair saved = pairRepo.save(p);
            logService.record("现场互选成缘", id, ma.getName() + " × " + mb.getName());
            res.put("pairId", saved.getId());
        }
        return res;
    }

    /** 互选名单 */
    @GetMapping("/{id}/matches")
    public List<ActivityMatch> matches(@PathVariable String id) {
        return matchRepo.findByActivityIdOrderByCreatedAtDesc(id);
    }

    /**
     * 为会员报名。
     *
     * <p>P0-7 幂等与并发：整个「查重 → 满员判断 → 落库 → 重算」在同一事务内完成，
     * 并由 t_activity_signup(activity_id, member_id) 唯一约束兜底；并发重复提交时
     * 唯一键冲突会被捕获并转成幂等结果，不会产生重复报名或人数虚高。</p>
     */
    @PostMapping("/{id}/signup")
    @Transactional
    public Map<String, Object> signup(@PathVariable String id, @RequestBody Map<String, Object> body) {
        Activity a = activityRepo.findById(id).orElseThrow(() -> BizException.notFound("活动不存在：" + id));
        String memberId = str(body.get("memberId"));
        if (!has(memberId)) throw new IllegalArgumentException("memberId 不能为空");
        Member m = memberRepo.findById(memberId).orElseThrow(() -> BizException.notFound("会员不存在：" + memberId));
        // IDOR：红娘只能给自己负责的会员报名
        if (RequestContext.isAgent() && m.getAgent() != null
                && !RequestContext.agentName().equals(m.getAgent())) {
            logService.record("越权访问拦截", memberId, "尝试为 " + m.getAgent() + " 的会员报名活动 " + id);
            throw BizException.forbidden("无权为该会员报名");
        }
        if (signupRepo.existsByActivityIdAndMemberId(id, memberId)) {
            throw new BizException("该会员已报名此活动");
        }
        // 满员判断以库内真实报名数为准，避免依赖可能滞后的冗余字段
        long signedNow = signupRepo.countByActivityId(id);
        if (nz(a.getCapacity()) > 0 && signedNow >= nz(a.getCapacity())) {
            throw new BizException("活动已满员");
        }

        ActivitySignup s = new ActivitySignup();
        s.setActivityId(id);
        s.setMemberId(memberId);
        s.setMemberName(m.getName());
        s.setGender(m.getGender());
        s.setStatus("已报名");
        s.setPaid(Boolean.TRUE.equals(body.get("paid")));
        s.setAmount(Money.toDouble(a.getPrice()));
        s.setPhone(m.getPhone());
        s.setSignedAt(LocalDateTime.now());
        try {
            signupRepo.saveAndFlush(s);
        } catch (DataIntegrityViolationException e) {
            throw new BizException("该会员已报名此活动");
        }

        recount(a);
        logService.record("活动报名", id, m.getName() + " 报名 " + a.getTitle());
        return Map.of("ok", true, "signup", s, "activity", toVo(a));
    }

    /** 取消报名 */
    @DeleteMapping("/{id}/signup/{signupId}")
    @Transactional
    public Map<String, Object> cancelSignup(@PathVariable String id, @PathVariable Long signupId) {
        Activity a = activityRepo.findById(id).orElseThrow(() -> BizException.notFound("活动不存在：" + id));
        ActivitySignup s = signupRepo.findById(signupId)
                .orElseThrow(() -> BizException.notFound("报名记录不存在：" + signupId));
        // IDOR：报名记录必须属于该活动，且红娘只能操作自己会员的报名
        if (!id.equals(s.getActivityId())) throw BizException.forbidden("报名记录与活动不匹配");
        if (RequestContext.isAgent() && s.getMemberId() != null) {
            String owner = memberRepo.findById(s.getMemberId()).map(Member::getAgent).orElse(null);
            if (owner != null && !RequestContext.agentName().equals(owner)) {
                logService.record("越权访问拦截", String.valueOf(signupId), "尝试取消 " + owner + " 的会员报名");
                throw BizException.forbidden("无权取消该报名");
            }
        }
        signupRepo.deleteById(signupId);
        recount(a);
        logService.record("取消报名", id, "报名记录 " + signupId);
        return Map.of("ok", true, "activity", toVo(a));
    }

    /** 活动归属校验：红娘只能改自己负责的活动（活动无归属人时不限制） */
    private void assertOwned(Activity a) {
        if (!RequestContext.isAgent()) return;
        String me = RequestContext.agentName();
        if (me != null && a.getAgent() != null && !me.equals(a.getAgent())) {
            logService.record("越权访问拦截", a.getId(), "尝试操作 " + a.getAgent() + " 的活动");
            throw BizException.forbidden("无权操作该活动");
        }
    }

    /** 报名人数 / 性别 / 营收重算 */
    private void recount(Activity a) {
        List<ActivitySignup> list = signupRepo.findByActivityId(a.getId());
        a.setSigned(list.size());
        a.setMale((int) list.stream().filter(x -> "男".equals(x.getGender())).count());
        a.setFemale((int) list.stream().filter(x -> "女".equals(x.getGender())).count());
        a.setRevenue(Money.of(list.stream().mapToDouble(x -> x.getAmount() == null ? 0 : x.getAmount()).sum()));
        if (nz(a.getCapacity()) > 0 && a.getSigned() >= a.getCapacity() && "报名中".equals(a.getStatus())) {
            a.setStatus("已满员");
        }
        a.setUpdatedAt(LocalDateTime.now());
        activityRepo.save(a);
    }

    @SuppressWarnings("unchecked")
    private void apply(Activity a, Map<String, Object> b) {
        if (b.containsKey("title")) a.setTitle(str(b.get("title")));
        if (b.containsKey("date")) a.setDate(parseDate(str(b.get("date"))));
        if (b.containsKey("time")) a.setTime(str(b.get("time")));
        if (b.containsKey("place")) a.setPlace(str(b.get("place")));
        if (b.containsKey("status")) a.setStatus(str(b.get("status")));
        if (b.containsKey("capacity")) a.setCapacity(num(b.get("capacity")).intValue());
        if (b.containsKey("price")) a.setPrice(Money.of(b.get("price")));
        if (b.containsKey("expectedPairs")) a.setExpectedPairs(num(b.get("expectedPairs")).intValue());
        if (b.containsKey("agent")) a.setAgent(str(b.get("agent")));
        if (b.containsKey("hero")) a.setHero(Boolean.TRUE.equals(b.get("hero")));
        if (b.containsKey("description")) a.setDescription(str(b.get("description")));
        if (b.containsKey("tags")) {
            Object t = b.get("tags");
            if (t instanceof List) a.setTags(String.join(",", ((List<Object>) t).stream().map(String::valueOf).toList()));
            else a.setTags(str(t));
        }
        if (a.getDate() != null) a.setWeekday(WEEKDAYS[a.getDate().getDayOfWeek().getValue() - 1]);
    }

    /** 实体 → 前端 Activity（date 点分格式、tags 数组） */
    static Map<String, Object> toVo(Activity a) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", a.getId());
        m.put("title", a.getTitle());
        m.put("date", a.getDate() == null ? "" : a.getDate().format(DOT));
        m.put("weekday", a.getWeekday());
        m.put("time", a.getTime());
        m.put("place", a.getPlace());
        m.put("status", a.getStatus());
        m.put("tags", has(a.getTags()) ? Arrays.asList(a.getTags().split(",")) : List.of());
        m.put("signed", nz(a.getSigned()));
        m.put("capacity", nz(a.getCapacity()));
        m.put("female", nz(a.getFemale()));
        m.put("male", nz(a.getMale()));
        m.put("price", a.getPrice() == null ? 0 : a.getPrice());
        m.put("revenue", a.getRevenue() == null ? 0 : a.getRevenue());
        m.put("expectedPairs", nz(a.getExpectedPairs()));
        m.put("agent", a.getAgent());
        m.put("hero", Boolean.TRUE.equals(a.getHero()));
        return m;
    }

    private static LocalDate parseDate(String s) {
        if (!has(s)) return null;
        String v = s.trim().replace('.', '-').replace('/', '-');
        try {
            return LocalDate.parse(v);
        } catch (Exception e) {
            return null;
        }
    }

    private static boolean has(String s) { return s != null && !s.isBlank(); }
    private static int nz(Integer i) { return i == null ? 0 : i; }
    private static String str(Object o) { return o == null ? null : String.valueOf(o); }
    private static Number num(Object o) { return o instanceof Number n ? n : Double.parseDouble(String.valueOf(o)); }
}
