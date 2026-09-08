package com.queqiao.server.service;

import com.queqiao.server.common.BizException;
import com.queqiao.server.common.Money;
import com.queqiao.server.common.Ids;
import com.queqiao.server.common.OrderStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.queqiao.server.entity.ChannelLedger;
import com.queqiao.server.entity.FinanceOrder;
import com.queqiao.server.repo.ChannelLedgerRepository;
import com.queqiao.server.repo.FinanceOrderRepository;
import com.queqiao.server.service.CommissionService;
import com.queqiao.server.service.CouponService;
import com.queqiao.server.service.CityService;
import com.queqiao.server.service.EscrowService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 财务写操作服务：所有涉及「收款 / 支付 / 退款」的变更都在此以事务执行，
 * 并对状态流转做状态机守卫 + 幂等保护（重复退款/支付不会产生多笔记录）。
 * 每次支付 / 退款同步写入渠道侧流水（t_channel_ledger），供对账使用。
 */
@Service
public class FinanceService {

    private static final Logger log = LoggerFactory.getLogger(FinanceService.class);

    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final FinanceOrderRepository orderRepo;
    private final ChannelLedgerRepository ledgerRepo;
    private final LogService logService;
    private final CommissionService commissionService;
    private final ReferralService referralService;
    private final CouponService couponService;
    private final CityService cityService;
    private final RenewalService renewalService;
    private final EscrowService escrowService;

    public FinanceService(FinanceOrderRepository orderRepo, ChannelLedgerRepository ledgerRepo,
                          LogService logService, CommissionService commissionService,
                          ReferralService referralService, CouponService couponService,
                          CityService cityService, RenewalService renewalService,
                          EscrowService escrowService) {
        this.orderRepo = orderRepo;
        this.ledgerRepo = ledgerRepo;
        this.logService = logService;
        this.commissionService = commissionService;
        this.referralService = referralService;
        this.couponService = couponService;
        this.cityService = cityService;
        this.renewalService = renewalService;
        this.escrowService = escrowService;
    }

    /**
     * 支付成功：待支付 / 处理中 → 已完成（幂等：已完成直接返回）。
     * 同步写入渠道侧流水（PAID）。
     */
    @Transactional
    public FinanceOrder pay(String id, String channel, String tradeNo) {
        FinanceOrder o = orderRepo.findById(id).orElseThrow(() -> BizException.notFound("订单"));
        renewalService.closeReminder(o.getId()); // 1-G: 任一支付/确认尝试都关闭对应续费提醒（幂等）
        OrderStatus cur = OrderStatus.parse(o.getStatus());
        if (cur == OrderStatus.已完成) return o; // 幂等
        if (cur == OrderStatus.已退款) {
            throw new BizException("已退款订单不可支付");
        }
        if (cur != null && !cur.canTransitTo(OrderStatus.已完成)) {
            throw new BizException("订单状态「" + o.getStatus() + "」不可支付");
        }
        o.setStatus("已完成");
        o.setPaidAt(LocalDateTime.now());
        if (channel != null) o.setChannel(channel);
        if (tradeNo != null) o.setTradeNo(tradeNo);
        o.setUpdatedAt(LocalDateTime.now());
        orderRepo.save(o);
        ledgerRepo.save(channelRow(o, "PAID", tradeNo));
        logService.record("订单支付", id, o.getOrderNo() + " ¥" + o.getAmount() + " via " + o.getChannel());
        // 支付完成 → 触发分佣计提（续费 / 新购且已有成功牵线）
        commissionService.settleFromOrder(o);
        // 支付完成 → 触发转介绍奖励结算（若该会员由他人转介绍而来，幂等）
        try {
            referralService.onOrderPaid(o);
        } catch (Exception e) {
            log.warn("[referral] 转介绍奖励结算失败 order={} : {}", o.getId(), e.getMessage());
        }
        // 支付成功 → 核销所用优惠券（幂等）
        try {
            couponService.redeem(o);
        } catch (Exception e) {
            log.warn("[coupon] 优惠券核销失败 order={} : {}", o.getId(), e.getMessage());
        }
        // 支付成功 → 社群档位订阅开通真实会员资格（仅「社群订阅」订单，幂等）
        try {
            cityService.onOrderPaid(o);
        } catch (Exception e) {
            log.warn("[city] 社群档位开通失败 order={} : {}", o.getId(), e.getMessage());
        }
        // 支付成功 → 自动建托管单（套餐购买 → 资金托管，实现交易闭环）；异常不阻断主流程
        try {
            escrowService.autoHold(o);
        } catch (Exception e) {
            log.warn("[escrow] 自动托管失败 order={} : {}", o.getId(), e.getMessage());
        }
        return o;
    }

    /** 确认收款（线下 / 手动）：处理中 → 已完成，复用 pay 的幂等语义 */
    @Transactional
    public FinanceOrder confirm(String id) {
        return pay(id, null, null);
    }

    /**
     * 退款：已完成 → 已退款，同时生成一笔负数退款单。
     * 幂等：若原单已退款，直接返回原单（不重复生成负数记录）。
     * 同步写渠道侧退款流水。
     */
    @Transactional
    public FinanceOrder refund(String id, Double amount, String reason) {
        FinanceOrder src = orderRepo.findById(id).orElseThrow(() -> BizException.notFound("订单"));
        if ("已退款".equals(src.getStatus())) {
            return orderRepo.findByOrderId(id).orElse(src); // 幂等：返回已生成的退款单（同一 id）
        }
        OrderStatus cur = OrderStatus.parse(src.getStatus());
        if (cur != null && !cur.canTransitTo(OrderStatus.已退款)) {
            throw new BizException("订单状态「" + src.getStatus() + "」不可退款");
        }
        BigDecimal amt = Money.abs(amount != null ? Money.of(amount) : src.getAmount());
        // 退款金额上限：不能超过原订单实付金额（P0 防超额/重复退款）
        if (src.getAmount() != null && amt.compareTo(src.getAmount()) > 0) {
            throw new BizException("退款金额不能超过原订单金额 ¥" + Money.toDouble(src.getAmount()));
        }

        FinanceOrder r = new FinanceOrder();
        r.setId(Ids.biz("FO"));
        r.setOrderId(src.getId());
        r.setOrderNo("RF" + System.currentTimeMillis());
        r.setMemberId(src.getMemberId());
        r.setMember(src.getMember());
        r.setType("退款");
        r.setAmount(amt.negate());
        r.setStatus("已完成");
        r.setAgent(src.getAgent());
        r.setChannel(src.getChannel());
        r.setProductId(src.getProductId());
        r.setProductName(src.getProductName());
        r.setOrderType("退款");
        r.setRefundReason(reason);
        r.setTradeNo(src.getTradeNo());
        r.setRemark(reason == null ? "原单退款 " + src.getOrderNo() : reason + "（原单 " + src.getOrderNo() + "）");
        r.setCreatedAt(LocalDateTime.now());
        r.setTime(r.getCreatedAt().format(TS));
        orderRepo.save(r);

        src.setStatus("已退款");
        src.setRefundReason(reason);
        src.setUpdatedAt(LocalDateTime.now());
        orderRepo.save(src);

        // 渠道侧退款流水（始终记录，表示该订单在支付渠道侧的冲正）
        ChannelLedger cr = new ChannelLedger();
        cr.setId(Ids.biz("CL"));
        cr.setOrderId(id);
        cr.setOrderNo(src.getOrderNo());
        cr.setTradeNo(src.getTradeNo());
        cr.setChannel(src.getChannel());
        cr.setAmount(amt.negate());
        cr.setStatus("REFUND");
        cr.setPaidAt(LocalDateTime.now());
        cr.setCreatedAt(LocalDateTime.now());
        ledgerRepo.save(cr);

        logService.record("订单退款", id, src.getOrderNo() + " 退 ¥" + amt);
        return r;
    }

    private ChannelLedger channelRow(FinanceOrder o, String status, String tradeNo) {
        ChannelLedger l = new ChannelLedger();
        l.setId(Ids.biz("CL"));
        l.setOrderId(o.getId());
        l.setOrderNo(o.getOrderNo());
        l.setTradeNo(tradeNo != null ? tradeNo : o.getTradeNo());
        l.setChannel(o.getChannel());
        l.setAmount(o.getAmount());
        l.setStatus(status);
        l.setPaidAt(o.getPaidAt());
        l.setCreatedAt(LocalDateTime.now());
        return l;
    }
}
