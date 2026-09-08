package com.queqiao.server.service;

import com.queqiao.server.common.BizException;
import com.queqiao.server.common.Ids;
import com.queqiao.server.common.Money;
import com.queqiao.server.entity.FinanceOrder;
import com.queqiao.server.entity.Member;
import com.queqiao.server.entity.Referral;
import com.queqiao.server.repo.MemberRepository;
import com.queqiao.server.repo.ReferralRepository;
import com.queqiao.server.service.CouponService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

/**
 * 转介绍激励（#111 5.3.1）。
 *
 * 生命周期：已登记 → 已转化（被推荐人首次付费）→ 已发奖。
 * 触发点：{@link FinanceService#pay} 支付完成后调用 {@link #onOrderPaid}，
 * 事件驱动地完成「转化 + 发奖」，无需人工干预。
 *
 * 幂等保证：
 *  1. 同一 (referrerId + refereePhone) 只允许登记一条；
 *  2. 已转化的记录不会被后续订单二次改写金额；
 *  3. 已发奖的记录再次发奖直接返回，奖励金额不累加。
 */
@Service
public class ReferralService {

    public static final String REGISTERED = "已登记";
    public static final String CONVERTED = "已转化";
    public static final String REWARDED = "已发奖";
    public static final String INVALID = "已失效";
    public static final String COUPON = "优惠券";

    private final ReferralRepository repo;
    private final MemberRepository members;
    private final SettingService settings;
    private final LogService logs;
    private final CouponService couponService;
    private final PointsService pointsService;

    public ReferralService(ReferralRepository repo, MemberRepository members,
                           SettingService settings, LogService logs, CouponService couponService,
                           PointsService pointsService) {
        this.repo = repo;
        this.members = members;
        this.settings = settings;
        this.logs = logs;
        this.couponService = couponService;
        this.pointsService = pointsService;
    }

    /** 登记推荐关系；同一推荐人对同一手机号只登记一次（返回既有记录） */
    @Transactional
    public Referral register(String referrerId, String referrerType, String refereeName,
                             String refereePhone, String refereeLeadId, String remark) {
        if (referrerId == null || referrerId.isBlank()) throw new BizException("缺少推荐人");
        if (refereePhone == null || refereePhone.isBlank()) throw new BizException("缺少被推荐人手机号");

        Optional<Referral> exist = repo.findByReferrerIdAndRefereePhone(referrerId, refereePhone);
        if (exist.isPresent()) return exist.get();

        String referrerName = resolveName(referrerId);
        Referral r = new Referral();
        r.setId(Ids.biz("RF"));
        r.setReferrerId(referrerId);
        r.setReferrerType(referrerType == null || referrerType.isBlank() ? "会员" : referrerType);
        r.setReferrerName(referrerName);
        r.setRefereeName(refereeName);
        r.setRefereePhone(refereePhone);
        r.setRefereeLeadId(refereeLeadId);
        r.setStatus(REGISTERED);
        r.setRewardAmount(BigDecimal.ZERO);
        r.setRemark(remark);
        r.setCreatedAt(LocalDateTime.now());
        r.setUpdatedAt(LocalDateTime.now());
        repo.save(r);
        logs.record("登记转介绍", r.getId(), referrerName + " 推荐 " + refereeName);
        return r;
    }

    /** 会员专属推荐码：不存在则生成并落库（幂等）。2-B 裂变分享入口。 */
    @Transactional
    public String ensureCode(String memberId) {
        Member m = members.findById(memberId).orElseThrow(() -> BizException.notFound("会员"));
        if (m.getReferralCode() != null && !m.getReferralCode().isBlank()) return m.getReferralCode();
        String code = genCode();
        m.setReferralCode(code);
        m.setUpdatedAt(LocalDateTime.now());
        members.save(m);
        logs.record("生成推荐码", memberId, code);
        return code;
    }

    /**
     * C 端裂变：好友凭会员专属码登记为被推荐人，奖励默认走「积分」（接 1-A 积分）。
     * 幂等：同一推荐人对同一手机号只登记一条。
     */
    @Transactional
    public Referral registerByCode(String code, String refereeName, String refereePhone) {
        if (code == null || code.isBlank()) throw new BizException("缺少推荐码");
        if (refereePhone == null || refereePhone.isBlank()) throw new BizException("缺少被推荐人手机号");
        Optional<Member> referrer = members.findByReferralCode(code);
        if (referrer.isEmpty()) throw new BizException("推荐码无效或已失效");
        Referral r = register(referrer.get().getId(), "会员", refereeName, refereePhone, null,
                "C端裂变-专属码 " + code);
        // 裂变默认奖励形式：积分（接 1-A 积分体系）
        if (r.getRewardType() == null || r.getRewardType().isBlank()) {
            r.setRewardType("积分");
            r.setUpdatedAt(LocalDateTime.now());
            repo.save(r);
        }
        return r;
    }

    /** 生成 6 位无歧义字符推荐码（QQ 前缀），碰撞重试，兜底时间戳。 */
    private String genCode() {
        for (int i = 0; i < 20; i++) {
            String c = "QQ" + randCode(6);
            if (members.findByReferralCode(c).isEmpty()) return c;
        }
        return "QQ" + (System.currentTimeMillis() % 1000000);
    }

    private static String randCode(int n) {
        final String chars = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < n; i++) sb.append(chars.charAt((int) (Math.random() * chars.length())));
        return sb.toString();
    }

    /**
     * 订单支付完成事件：若下单会员是某条推荐记录的被推荐人，
     * 则自动置为「已转化」并按配置比例发奖。整个过程幂等。
     */
    @Transactional
    public void onOrderPaid(FinanceOrder order) {
        if (order == null || order.getMemberId() == null) return;
        Member m = members.findById(order.getMemberId()).orElse(null);
        if (m == null) return;

        List<Referral> hits = new ArrayList<>(repo.findByRefereeMemberId(m.getId()));
        if (m.getPhone() != null && !m.getPhone().isBlank()) {
            for (Referral r : repo.findByRefereePhone(m.getPhone())) {
                if (hits.stream().noneMatch(x -> x.getId().equals(r.getId()))) hits.add(r);
            }
        }
        for (Referral r : hits) {
            // 已发奖 → 幂等跳过；已失效 → 不处理
            if (REWARDED.equals(r.getStatus()) || INVALID.equals(r.getStatus())) continue;
            r.setRefereeMemberId(m.getId());
            if (!CONVERTED.equals(r.getStatus())) {
                r.setStatus(CONVERTED);
                r.setConvertedAt(LocalDateTime.now());
                r.setOrderId(order.getId());
                r.setOrderAmount(order.getAmount());
                logs.record("转介绍转化", r.getId(), r.getReferrerName() + " → " + r.getRefereeName());
            }
            r.setUpdatedAt(LocalDateTime.now());
            repo.save(r);
            grant(r, "系统自动");
        }
    }

    /** 手动标记转化（主管） */
    @Transactional
    public Referral convert(String id, String memberId, Double orderAmount, String operator) {
        Referral r = repo.findById(id).orElseThrow(() -> BizException.notFound("转介绍记录"));
        if (REWARDED.equals(r.getStatus())) return r;
        r.setStatus(CONVERTED);
        if (memberId != null && !memberId.isBlank()) r.setRefereeMemberId(memberId);
        if (orderAmount != null) r.setOrderAmount(Money.of(orderAmount));
        r.setConvertedAt(LocalDateTime.now());
        r.setOperator(operator);
        r.setUpdatedAt(LocalDateTime.now());
        repo.save(r);
        logs.record("转介绍转化", id, r.getReferrerName() + " → " + r.getRefereeName());
        return r;
    }

    /** 发放奖励（管理员）；幂等：已发奖直接返回 */
    @Transactional
    public Referral reward(String id, String rewardType, Double amount, String operator) {
        Referral r = repo.findById(id).orElseThrow(() -> BizException.notFound("转介绍记录"));
        if (REWARDED.equals(r.getStatus())) return r;
        if (REGISTERED.equals(r.getStatus())) {
            throw new BizException("该推荐尚未转化，不能发奖");
        }
        if (rewardType != null && !rewardType.isBlank()) r.setRewardType(rewardType);
        if (amount != null) r.setRewardAmount(Money.of(amount));
        grant(r, operator);
        return r;
    }

    /** 内部发奖：按配置比例计算，封顶保护；已发奖不重复 */
    private void grant(Referral r, String operator) {
        if (REWARDED.equals(r.getStatus())) return;
        // 2-B 裂变：奖励形式为「积分」→ 走 1-A 积分体系，按推荐记录幂等发放
        if ("积分".equals(r.getRewardType())) {
            r.setStatus(REWARDED);
            r.setRewardedAt(LocalDateTime.now());
            r.setOperator(operator);
            r.setUpdatedAt(LocalDateTime.now());
            repo.save(r);
            logs.record("转介绍发奖", r.getId(), r.getReferrerName() + " 获得积分奖励");
            try {
                pointsService.earnReferral(r.getReferrerId(), r.getId(), operator, "推荐好友转化奖励");
            } catch (Exception e) {
                logs.record("转介绍积分发放失败", r.getId(), e.getMessage());
            }
            return;
        }
        BigDecimal amt = Money.nz(r.getRewardAmount());
        if (!Money.gtZero(amt)) {
            double rate = settings.getInt("referral.rewardRate", 10);
            double cap = settings.getInt("referral.rewardCap", 500);
            BigDecimal base = Money.nz(r.getOrderAmount());
            BigDecimal pct = Money.percent(base, rate);
            amt = Money.gtZero(pct) && pct.compareTo(Money.of(cap)) < 0 ? pct : Money.of(cap);
        }
        r.setRewardAmount(amt);
        if (r.getRewardType() == null || r.getRewardType().isBlank()) r.setRewardType("服务折扣");
        r.setStatus(REWARDED);
        r.setRewardedAt(LocalDateTime.now());
        r.setOperator(operator);
        r.setUpdatedAt(LocalDateTime.now());
        repo.save(r);
        logs.record("转介绍发奖", r.getId(),
                r.getReferrerName() + " 获得 " + r.getRewardType() + " ¥" + r.getRewardAmount());
        // 奖励形式为优惠券：向被推荐人（已转化会员）实际发放一张抵现券（幂等按来源）
        if (COUPON.equals(r.getRewardType()) && r.getRefereeMemberId() != null) {
            couponService.issue(r.getRefereeMemberId(), r.getRefereeName(),
                    Money.toDouble(amt), r.getId(), "REFERRAL");
        }
    }

    /** 作废 */
    @Transactional
    public Referral invalidate(String id, String operator) {
        Referral r = repo.findById(id).orElseThrow(() -> BizException.notFound("转介绍记录"));
        if (REWARDED.equals(r.getStatus())) throw new BizException("已发奖记录不可作废");
        r.setStatus(INVALID);
        r.setOperator(operator);
        r.setUpdatedAt(LocalDateTime.now());
        return repo.save(r);
    }

    public List<Referral> list(String status, String referrerId) {
        List<Referral> all = (status == null || status.isBlank())
                ? repo.findAll()
                : repo.findByStatusOrderByCreatedAtDesc(status);
        return all.stream()
                .filter(r -> referrerId == null || referrerId.isBlank() || referrerId.equals(r.getReferrerId()))
                .sorted(Comparator.comparing(Referral::getCreatedAt,
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();
    }

    public List<Referral> mine(String referrerId) {
        if (referrerId == null) return List.of();
        return repo.findByReferrerIdOrderByCreatedAtDesc(referrerId);
    }

    public Referral detail(String id) {
        return repo.findById(id).orElseThrow(() -> BizException.notFound("转介绍记录"));
    }

    /** 汇总：登记/转化/发奖笔数、奖励总额、转化率 */
    public Map<String, Object> summary() {
        List<Referral> all = repo.findAll();
        long registered = all.stream().filter(r -> REGISTERED.equals(r.getStatus())).count();
        long converted = all.stream().filter(r -> CONVERTED.equals(r.getStatus())).count();
        long rewarded = all.stream().filter(r -> REWARDED.equals(r.getStatus())).count();
        BigDecimal rewardTotal = Money.sum(all,
                r -> REWARDED.equals(r.getStatus()) ? r.getRewardAmount() : null);
        BigDecimal orderTotal = Money.sum(all, Referral::getOrderAmount);
        long convertedOrRewarded = converted + rewarded;

        Map<String, Object> res = new LinkedHashMap<>();
        res.put("total", all.size());
        res.put("registeredCount", registered);
        res.put("convertedCount", converted);
        res.put("rewardedCount", rewarded);
        res.put("rewardTotal", rewardTotal);
        res.put("orderTotal", orderTotal);
        res.put("convertRate", all.isEmpty() ? 0.0
                : Math.round(convertedOrRewarded * 1000.0 / all.size()) / 10.0);
        res.put("roi", Money.gtZero(rewardTotal)
                ? Money.toDouble(Money.sub(orderTotal, rewardTotal)) / Money.toDouble(rewardTotal) * 100 : 0.0);
        // 推荐人排行 TOP5
        Map<String, Object[]> byRef = new LinkedHashMap<>();
        for (Referral r : all) {
            String k = r.getReferrerName() == null ? r.getReferrerId() : r.getReferrerName();
            if (k == null) continue;
            Object[] v = byRef.computeIfAbsent(k, x -> new Object[]{0L, BigDecimal.ZERO});
            v[0] = (Long) v[0] + 1;
            v[1] = Money.add((BigDecimal) v[1], r.getRewardAmount());
        }
        List<Map<String, Object>> top = byRef.entrySet().stream()
                .sorted((a, b) -> Long.compare((Long) b.getValue()[0], (Long) a.getValue()[0]))
                .limit(5)
                .map(e -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("name", e.getKey());
                    m.put("count", e.getValue()[0]);
                    m.put("reward", e.getValue()[1]);
                    return m;
                }).toList();
        res.put("topReferrers", top);
        return res;
    }

    private String resolveName(String memberId) {
        return members.findById(memberId).map(Member::getName).orElse(memberId);
    }

    private static double round(double v) {
        return Math.round(v * 100.0) / 100.0;
    }
}
