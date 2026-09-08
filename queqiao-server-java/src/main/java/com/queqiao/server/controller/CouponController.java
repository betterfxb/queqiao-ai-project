package com.queqiao.server.controller;

import com.queqiao.server.common.BizException;
import com.queqiao.server.entity.Coupon;
import com.queqiao.server.security.RequestContext;
import com.queqiao.server.service.CouponService;
import com.queqiao.server.service.LogService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 优惠券：钱包查询（主管）、管理员手动发放。
 * 转介绍自动发券由 {@link com.queqiao.server.service.ReferralService} 在发奖时触发。
 */
@RestController
@RequestMapping("/api/coupons")
public class CouponController {

    private final CouponService couponService;
    private final LogService logService;

    public CouponController(CouponService couponService, LogService logService) {
        this.couponService = couponService;
        this.logService = logService;
    }

    /** 优惠券钱包：按会员 / 状态查询（主管+） */
    @GetMapping
    public List<Coupon> list(@RequestParam(required = false) String memberId,
                             @RequestParam(required = false) String status) {
        RequestContext.requireManager();
        return couponService.wallet(memberId, status);
    }

    /** 管理员手动发放抵现券 */
    @PostMapping("/issue")
    public Coupon issue(@RequestBody Map<String, Object> body) {
        RequestContext.requireAdmin();
        String memberId = str(body.get("memberId"));
        String memberName = str(body.get("memberName"));
        Double amount = body.get("amount") != null ? num(body.get("amount")).doubleValue() : 0;
        int days = body.get("days") != null ? num(body.get("days")).intValue() : CouponService.DEFAULT_VALID_DAYS;
        return couponService.manualIssue(memberId, memberName, amount, days);
    }

    private static String str(Object o) {
        return o == null ? null : String.valueOf(o);
    }

    private static Number num(Object o) {
        return o instanceof Number n ? n : Double.parseDouble(String.valueOf(o));
    }
}
