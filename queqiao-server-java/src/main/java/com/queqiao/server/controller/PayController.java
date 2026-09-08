package com.queqiao.server.controller;

import com.queqiao.server.common.BizException;
import com.queqiao.server.common.Ids;
import com.queqiao.server.common.Money;
import com.queqiao.server.entity.FinanceOrder;
import com.queqiao.server.entity.PayReconcile;
import com.queqiao.server.entity.ServiceProduct;
import com.queqiao.server.pay.PayGateway;
import com.queqiao.server.repo.CommissionRepository;
import com.queqiao.server.repo.FinanceOrderRepository;
import com.queqiao.server.repo.ReferralRepository;
import com.queqiao.server.repo.ServiceProductRepository;
import com.queqiao.server.security.RequestContext;
import com.queqiao.server.service.CommissionService;
import com.queqiao.server.service.CouponService;
import com.queqiao.server.service.FinanceService;
import com.queqiao.server.service.LogService;
import com.queqiao.server.service.ReconcileService;
import com.queqiao.server.service.ReferralService;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 支付闭环：/api/pay
 *  - config   已配置渠道及可用性
 *  - create   发起支付（创建待支付订单 + 预支付参数）
 *  - simulate 模拟渠道成功回调（线下模式的「确认收款」，演示 notify→更新状态）
 *  - notify   异步回调（公开，验签后更新订单状态）
 *  - reconcile/reconcile/run 对账查询与手动触发
 */
@RestController
@RequestMapping("/api/pay")
public class PayController {

    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final FinanceOrderRepository orderRepo;
    private final FinanceService financeService;
    private final ReconcileService reconcileService;
    private final ServiceProductRepository products;
    private final LogService logService;
    private final List<PayGateway> gateways;
    private final CommissionRepository commissionRepo;
    private final ReferralRepository referralRepo;
    private final CommissionService commissionService;
    private final ReferralService referralService;
    private final CouponService couponService;

    public PayController(FinanceOrderRepository orderRepo, FinanceService financeService,
                         ReconcileService reconcileService, ServiceProductRepository products,
                         LogService logService, List<PayGateway> gateways,
                         CommissionRepository commissionRepo,
                         ReferralRepository referralRepo, CommissionService commissionService,
                         ReferralService referralService, CouponService couponService) {
        this.orderRepo = orderRepo;
        this.financeService = financeService;
        this.reconcileService = reconcileService;
        this.products = products;
        this.logService = logService;
        this.gateways = gateways;
        this.commissionRepo = commissionRepo;
        this.referralRepo = referralRepo;
        this.commissionService = commissionService;
        this.referralService = referralService;
        this.couponService = couponService;
    }

    private PayGateway gateway(String channel) {
        return gateways.stream().filter(g -> g.channel().equals(channel)).findFirst()
                .orElseThrow(() -> new BizException("不支持的支付渠道：" + channel));
    }

    /** 已配置的支付渠道及可用性（前端据此展示可选渠道） */
    @GetMapping("/config")
    public Map<String, Object> config() {
        RequestContext.requireManager();
        List<Map<String, Object>> ch = gateways.stream().map(g -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("channel", g.channel());
            m.put("available", g.available());
            return m;
        }).collect(Collectors.toList());
        Map<String, Object> res = new LinkedHashMap<>();
        res.put("channels", ch);
        return res;
    }

    /** 发起支付：创建待支付订单 + 生成预支付参数 */
    @PostMapping("/create")
    public Map<String, Object> create(@RequestBody Map<String, Object> body) {
        RequestContext.requireManager();
        String channel = str(body.get("channel"));
        if (!has(channel)) throw new BizException("请选择支付渠道");
        PayGateway gw = gateway(channel);
        if (!gw.available()) {
            throw new BizException(channel + " 渠道未配置（密钥走环境变量，请设置后重试）");
        }

        String member = str(body.get("member"));
        if (!has(member)) throw new BizException("请选择会员");
        String memberId = str(body.get("memberId"));
        String productId = str(body.get("productId"));
        String orderType = str(body.get("orderType"));
        if (!has(orderType)) orderType = "新购";

        BigDecimal amount;
        String productName = null;
        if (has(productId)) {
            ServiceProduct p = products.findById(productId).orElseThrow(() -> BizException.notFound("服务产品"));
            productName = p.getName();
            amount = body.get("amount") != null ? Money.of(body.get("amount")) : Money.nz(p.getPrice());
        } else {
            if (body.get("amount") == null) throw new BizException("未关联产品时需填写金额");
            amount = Money.of(body.get("amount"));
        }

        FinanceOrder o = new FinanceOrder();
        o.setId(Ids.biz("FO"));
        o.setOrderNo("QQ" + System.currentTimeMillis());
        o.setMemberId(memberId);
        o.setMember(member);
        o.setType("收款");
        // 优惠券抵扣（若有）：校验归属/状态/有效期后按金额折减，订单金额同步更新
        String couponId = str(body.get("couponId"));
        if (couponId != null && !couponId.isBlank()) {
            double disc = couponService.discountFor(couponId, memberId, Money.toDouble(amount));
            if (disc > 0) {
                amount = Money.sub(amount, Money.of(disc));
                o.setCouponId(couponId);
                o.setCouponAmount(Money.of(disc));
            }
        }
        o.setAmount(Money.nz(amount));
        o.setStatus("待支付");
        o.setChannel(channel);
        o.setProductId(productId);
        o.setProductName(productName);
        o.setOrderType(orderType);
        o.setAgent(str(body.get("agent")));
        o.setRemark(str(body.get("remark")));
        o.setCreatedAt(LocalDateTime.now());
        o.setUpdatedAt(LocalDateTime.now());
        o.setTime(o.getCreatedAt().format(TS));
        orderRepo.save(o);

        Map<String, Object> res = new LinkedHashMap<>();
        res.put("order", o);
        res.put("prepay", gw.prepay(o));
        return res;
    }

    /** 模拟渠道成功回调（线下模式：标记收款）。演示 notify → 状态机 → 渠道流水 全链路。 */
    @PostMapping("/simulate/{id}")
    public FinanceOrder simulate(@PathVariable String id) {
        RequestContext.requireManager();
        FinanceOrder o = orderRepo.findById(id).orElseThrow(() -> BizException.notFound("订单"));
        String ch = o.getChannel() != null ? o.getChannel() : "线下";
        return financeService.pay(id, ch, "OFFLINE-" + o.getOrderNo() + "-" + System.currentTimeMillis());
    }

    /** 异步回调（公开入口，渠道服务器调用）。验签通过后更新订单状态。 */
    @PostMapping("/notify")
    public Map<String, Object> notify(@RequestBody Map<String, Object> body) {
        String channel = str(body.get("channel"));
        String orderNo = str(body.get("orderNo"));
        String tradeNo = str(body.get("tradeNo"));
        String sign = str(body.get("sign"));
        if (!has(orderNo)) throw new BizException("缺少订单号");

        PayGateway gw = gateway(channel);
        Map<String, String> params = new LinkedHashMap<>();
        body.forEach((k, v) -> params.put(k, v == null ? "" : String.valueOf(v)));
        if (!gw.verifyNotify(params, sign)) {
            throw new BizException("签名校验失败");
        }
        FinanceOrder o = orderRepo.findByOrderNo(orderNo).orElseThrow(() -> BizException.notFound("订单：" + orderNo));
        financeService.pay(o.getId(), channel, tradeNo);
        return Map.of("code", "SUCCESS");
    }

    /** 对账差异查询（最近 N 天） */
    @GetMapping("/reconcile")
    public Map<String, Object> reconcile(@RequestParam(required = false, defaultValue = "30") int days) {
        RequestContext.requireManager();
        List<PayReconcile> list = reconcileService.recent(days);
        Map<String, Object> res = new LinkedHashMap<>();
        res.put("list", list);
        res.put("diffCount", list.size());
        return res;
    }

    /** 手动触发某日对账（管理员） */
    @PostMapping("/reconcile/run")
    public ReconcileService.ReconcileResult run(@RequestParam(required = false) String date) {
        RequestContext.requireAdmin();
        LocalDate d = date == null ? LocalDate.now().minusDays(1) : LocalDate.parse(date);
        return reconcileService.reconcile(d);
    }

    /**
     * 管理员修复：为历史终态订单补齐缺失的渠道流水 / 分佣 / 转介绍奖励（幂等）。
     * 适用场景：旧数据经 FinanceController.create 直写终态而未走状态机，导致对账缺失或分佣漏计。
     * 每次调用安全可重复——已存在的不重复创建。
     * <p>流水回填统一委托 {@link ReconcileService#backfillLedgers()}，覆盖「已完成」+「已退款」，
     * 与对账稽核口径一致；分佣 / 转介绍仅针对「已完成」正向订单补齐。
     */
    @PostMapping("/reconcile/repair")
    public Map<String, Object> repair() {
        RequestContext.requireAdmin();
        int ledgersCreated = reconcileService.backfillLedgers();
        int scanned = 0, commissionsBackfilled = 0, referralsBackfilled = 0;
        for (FinanceOrder o : orderRepo.findAll()) {
            if (!"已完成".equals(o.getStatus())) continue;
            scanned++;
            // 补齐分佣（幂等：已计提的不重复）
            int cmBefore = commissionRepo.findByOrderId(o.getId()).size();
            commissionService.settleFromOrder(o);
            if (commissionRepo.findByOrderId(o.getId()).size() > cmBefore) commissionsBackfilled++;
            // 补齐转介绍奖励（幂等：已发奖的不重复）
            int rfBefore = referralRepo.findByOrderId(o.getId()).size();
            referralService.onOrderPaid(o);
            if (referralRepo.findByOrderId(o.getId()).size() > rfBefore) referralsBackfilled++;
        }
        logService.record("对账修复", "repair",
                "扫描" + scanned + " 补流水" + ledgersCreated + " 补分佣" + commissionsBackfilled + " 补转介绍" + referralsBackfilled);
        Map<String, Object> res = new LinkedHashMap<>();
        res.put("scanned", scanned);
        res.put("ledgersCreated", ledgersCreated);
        res.put("commissionsBackfilled", commissionsBackfilled);
        res.put("referralsBackfilled", referralsBackfilled);
        return res;
    }

    private static boolean has(String s) { return s != null && !s.isBlank(); }
    private static String str(Object o) { return o == null ? null : String.valueOf(o); }
    private static Number num(Object o) { return o instanceof Number n ? n : Double.parseDouble(String.valueOf(o)); }
    private static double round(double v) { return Math.round(v * 100) / 100.0; }
}
