package com.queqiao.server.service;

import com.queqiao.server.common.BizException;
import com.queqiao.server.common.Ids;
import com.queqiao.server.common.Money;
import com.queqiao.server.entity.Coupon;
import com.queqiao.server.entity.FinanceOrder;
import com.queqiao.server.repo.CouponRepository;
import com.queqiao.server.service.LogService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * 优惠券服务：发放（幂等）、可用额度计算、支付成功核销（幂等）、钱包查询。
 * 与转介绍奖励解耦——转介绍发放优惠券只调用 {@link #issue}，不耦合业务状态。
 */
@Service
public class CouponService {

    public static final String UNUSED = "未使用";
    public static final String USED = "已使用";
    public static final String EXPIRED = "已过期";
    public static final String TYPE_CASH = "抵现";
    public static final int DEFAULT_VALID_DAYS = 90;

    private final CouponRepository repo;
    private final LogService logService;

    public CouponService(CouponRepository repo, LogService logService) {
        this.repo = repo;
        this.logService = logService;
    }

    /** 发放：幂等——同一来源(sourceId+sourceType)已发过且未过期则直接返回原券 */
    @Transactional
    public Coupon issue(String memberId, String memberName, double amount, String sourceId, String sourceType) {
        if (memberId == null || memberId.isBlank()) return null;
        if (sourceId != null && sourceType != null) {
            Optional<Coupon> existing = repo.findBySourceIdAndSourceType(sourceId, sourceType).stream()
                    .filter(c -> !EXPIRED.equals(c.getStatus()))
                    .findFirst();
            if (existing.isPresent()) return existing.get();
        }
        Coupon c = new Coupon();
        c.setId(Ids.biz("CP"));
        c.setCode(genCode());
        c.setMemberId(memberId);
        c.setMemberName(memberName);
        c.setAmount(Money.of(amount));
        c.setType(TYPE_CASH);
        c.setStatus(UNUSED);
        c.setSourceId(sourceId);
        c.setSourceType(sourceType);
        c.setExpireAt(LocalDateTime.now().plusDays(DEFAULT_VALID_DAYS));
        c.setCreatedAt(LocalDateTime.now());
        c.setUpdatedAt(LocalDateTime.now());
        repo.save(c);
        logService.record("发放优惠券", c.getId(),
                (memberName == null ? memberId : memberName) + " 抵现 ¥" + c.getAmount() + " 来源" + sourceType);
        return c;
    }

    /** 计算可用抵扣额（校验归属/状态/有效期），不可用时返回 0 */
    public double discountFor(String couponId, String memberId, double orderAmount) {
        if (couponId == null || couponId.isBlank()) return 0;
        Coupon c = repo.findById(couponId).orElse(null);
        if (c == null) return 0;
        if (!UNUSED.equals(c.getStatus())) return 0;
        if (c.getExpireAt() != null && c.getExpireAt().isBefore(LocalDateTime.now())) return 0;
        if (memberId != null && c.getMemberId() != null && !memberId.equals(c.getMemberId())) return 0;
        BigDecimal amt = Money.nz(c.getAmount());
        return Math.min(Money.toDouble(amt), orderAmount);
    }

    /** 核销：支付成功后标记已使用并绑定订单（幂等：已核销到同订单直接跳过） */
    @Transactional
    public void redeem(FinanceOrder order) {
        if (order.getCouponId() == null) return;
        Coupon c = repo.findById(order.getCouponId()).orElse(null);
        if (c == null) return;
        if (USED.equals(c.getStatus()) && order.getId().equals(c.getOrderId())) return; // 幂等
        if (!UNUSED.equals(c.getStatus())) return;
        c.setStatus(USED);
        c.setOrderId(order.getId());
        c.setUsedAt(LocalDateTime.now());
        c.setUpdatedAt(LocalDateTime.now());
        repo.save(c);
        BigDecimal disc = Money.nz(order.getCouponAmount());
        logService.record("核销优惠券", c.getId(), "订单" + order.getOrderNo() + " 抵现 ¥" + disc);
    }

    /** 优惠券钱包：按会员 / 状态查询 */
    public List<Coupon> wallet(String memberId, String status) {
        List<Coupon> list = (memberId == null || memberId.isBlank())
                ? repo.findAll()
                : repo.findByMemberIdOrderByCreatedAtDesc(memberId);
        if (status != null && !status.isBlank()) {
            list = list.stream().filter(c -> status.equals(c.getStatus())).toList();
        }
        return list;
    }

    /** 管理员手动发放 */
    @Transactional
    public Coupon manualIssue(String memberId, String memberName, double amount, int days) {
        if (memberId == null || memberId.isBlank()) throw new BizException("请指定会员");
        if (amount <= 0) throw new BizException("抵扣金额需大于 0");
        return issue(memberId, memberName, amount, null, "MANUAL");
    }

    private String genCode() {
        return "QQ" + UUID.randomUUID().toString().replace("-", "").substring(0, 12).toUpperCase();
    }

    private static double round(double v) {
        return Math.round(v * 100) / 100.0;
    }
}
