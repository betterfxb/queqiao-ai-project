package com.queqiao.server.service;

import com.queqiao.server.common.BizException;
import com.queqiao.server.common.Ids;
import com.queqiao.server.entity.EscrowOrder;
import com.queqiao.server.entity.FinanceOrder;
import com.queqiao.server.entity.Member;
import com.queqiao.server.repo.EscrowRepository;
import com.queqiao.server.repo.MemberRepository;
import com.queqiao.server.service.LogService;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

/**
 * 预付资金存管服务（3-D）。
 *
 * <p>状态机：待托管 → 已托管 →（履约）放款中 → 已放款；争议/未履约 → 退款中 → 已退款。
 * 每次状态变更均写时间戳，且退款金额/原因留痕，实现「退费透明」。</p>
 */
@Service
public class EscrowService {

    public static final String S_PENDING = "待托管";
    public static final String S_HELD = "已托管";
    public static final String S_RELEASING = "放款中";
    public static final String S_RELEASED = "已放款";
    public static final String S_REFUNDED_ING = "退款中";
    public static final String S_REFUNDED = "已退款";

    private final EscrowRepository escrows;
    private final MemberRepository members;
    private final LogService logs;
    private final Map<String, EscrowChannel> channelMap = new LinkedHashMap<>();

    public EscrowService(EscrowRepository escrows, MemberRepository members, LogService logs,
                         List<EscrowChannel> channels) {
        this.escrows = escrows;
        this.members = members;
        this.logs = logs;
        for (EscrowChannel c : channels) channelMap.put(c.name(), c);
    }

    /** 发起托管：先建待托管单，再调通道确认 → 已托管并回填存管流水号 */
    public EscrowOrder hold(String memberId, String packageId, String packageName,
                            long amount, String currency, String channel) {
        if (amount <= 0) throw new BizException("托管金额必须大于 0");
        Member m = members.findById(memberId).orElseThrow(() -> BizException.notFound("会员不存在：" + memberId));
        EscrowChannel ch = channelMap.get(channel);
        if (ch == null) throw new BizException("未知存管通道：" + channel);

        EscrowOrder o = new EscrowOrder();
        o.setId(Ids.biz("ES"));
        o.setMemberId(memberId);
        o.setMemberName(m.getName());
        o.setPackageId(packageId);
        o.setPackageName(packageName);
        o.setAmount(amount);
        o.setCurrency(currency == null ? "rmb" : currency);
        o.setChannel(channel);
        o.setStatus(S_PENDING);
        o.setPaidAt(LocalDateTime.now());
        o.setCreatedAt(LocalDateTime.now());
        o.setUpdatedAt(LocalDateTime.now());
        escrows.save(o);

        // 调通道确认托管（离线桩返回仿真流水号；真实环境为网关凭证）
        String escrowNo = ch.hold(o.getId(), amount, o.getCurrency());
        o.setEscrowNo(escrowNo);
        o.setStatus(S_HELD);
        o.setHoldAt(LocalDateTime.now());
        o.setUpdatedAt(LocalDateTime.now());
        escrows.save(o);

        logs.record("资金存管", o.getId(), m.getName() + " 套餐[" + packageName + "] 托管成功，通道=" + channel + "，流水号=" + escrowNo);
        return o;
    }

    /**
     * 支付成功自动建托管单（已托管）：把会员已支付的套餐款项接入存管，
     * 实现「套餐购买 → 资金托管」真实闭环。幂等：同一笔 FinanceOrder 仅建一次托管。
     * 托管通道按支付渠道推导（线下 → 银行存管，其余 → 数字人民币存管）。
     */
    public EscrowOrder autoHold(FinanceOrder o) {
        if (o == null || o.getProductId() == null || o.getProductId().isBlank()) return null; // 仅套餐购买入存管
        if (escrows.existsByFinanceOrderId(o.getId())) {
            return escrows.findByFinanceOrderIdOrderByCreatedAtDesc(o.getId()).get(0);
        }
        long fen = o.getAmount() == null ? 0 : o.getAmount().multiply(BigDecimal.valueOf(100)).longValue();
        if (fen <= 0) return null;
        String escrowChannel = "线下".equals(o.getChannel()) ? "bank-escrow" : "digital-rmb";
        Member m = members.findById(o.getMemberId()).orElse(null);
        EscrowOrder e = new EscrowOrder();
        e.setId(Ids.biz("ES"));
        e.setMemberId(o.getMemberId());
        e.setMemberName(m != null ? m.getName() : o.getMember());
        e.setPackageId(o.getProductId());
        e.setPackageName(o.getProductName());
        e.setAmount(fen);
        e.setCurrency("rmb");
        e.setChannel(escrowChannel);
        e.setStatus(S_PENDING);
        e.setFinanceOrderId(o.getId());
        e.setPaidAt(LocalDateTime.now());
        e.setCreatedAt(LocalDateTime.now());
        e.setUpdatedAt(LocalDateTime.now());
        escrows.save(e);

        String escrowNo = channelMap.get(escrowChannel).hold(e.getId(), fen, "rmb");
        e.setEscrowNo(escrowNo);
        e.setStatus(S_HELD);
        e.setHoldAt(LocalDateTime.now());
        e.setUpdatedAt(LocalDateTime.now());
        escrows.save(e);
        logs.record("资金存管", e.getId(), "支付成功自动托管：" + (m != null ? m.getName() : o.getMember())
                + " 套餐[" + o.getProductName() + "] 通道=" + escrowChannel + " 关联订单=" + o.getOrderNo());
        return e;
    }

    /** 放款：服务履约完成，将存管资金释放给服务商 */
    public EscrowOrder release(String id) {
        EscrowOrder o = escrows.findById(id).orElseThrow(() -> BizException.notFound("存管订单不存在：" + id));
        if (!S_HELD.equals(o.getStatus()) && !S_RELEASING.equals(o.getStatus())) {
            throw new BizException("当前状态「" + o.getStatus() + "」不可放款（仅 已托管/放款中 可放款）");
        }
        o.setStatus(S_RELEASED);
        o.setReleasedAt(LocalDateTime.now());
        o.setUpdatedAt(LocalDateTime.now());
        escrows.save(o);
        logs.record("资金存管", id, "放款完成，资金已释放给服务商");
        return o;
    }

    /** 退款：退费透明——记录退款金额与原因，状态机校验后进入已退款 */
    public EscrowOrder refund(String id, Long amount, String reason) {
        EscrowOrder o = escrows.findById(id).orElseThrow(() -> BizException.notFound("存管订单不存在：" + id));
        if (!S_HELD.equals(o.getStatus()) && !S_RELEASED.equals(o.getStatus())) {
            throw new BizException("当前状态「" + o.getStatus() + "」不可退款（仅 已托管/已放款 可退款）");
        }
        long refund = amount == null ? (o.getAmount() == null ? 0 : o.getAmount()) : amount;
        if (refund <= 0) throw new BizException("退款金额必须大于 0");
        if (o.getAmount() != null && refund > o.getAmount()) throw new BizException("退款金额不能超过托管金额");
        o.setStatus(S_REFUNDED);
        o.setRefundAmount(refund);
        o.setRefundReason(reason == null ? "未说明" : reason);
        o.setRefundAt(LocalDateTime.now());
        o.setUpdatedAt(LocalDateTime.now());
        escrows.save(o);
        logs.record("资金存管", id, "退款完成，金额=" + refund + "分，原因=" + o.getRefundReason());
        return o;
    }

    public List<EscrowOrder> list(String memberId) {
        if (memberId != null && !memberId.isBlank()) {
            return escrows.findByMemberIdOrderByCreatedAtDesc(memberId);
        }
        return escrows.findAllByOrderByCreatedAtDesc();
    }

    /** 存管透明统计：在管/已放款/已退款笔数与金额，以及退款率 */
    public Map<String, Object> stats() {
        long held = escrows.countByStatus(S_HELD) + escrows.countByStatus(S_RELEASING);
        long released = escrows.countByStatus(S_RELEASED);
        long refunded = escrows.countByStatus(S_REFUNDED) + escrows.countByStatus(S_REFUNDED_ING);
        long total = escrows.count();
        long heldAmt = nz(escrows.sumAmountByStatus(S_HELD)) + nz(escrows.sumAmountByStatus(S_RELEASING));
        long releasedAmt = nz(escrows.sumAmountByStatus(S_RELEASED));
        long refundedAmt = nz(escrows.sumAmountByStatus(S_REFUNDED)) + nz(escrows.sumAmountByStatus(S_REFUNDED_ING));
        double refundRate = (held + released + refunded) == 0 ? 0
                : Math.round(refunded * 1000.0 / (held + released + refunded)) / 10.0;

        Map<String, Object> m = new LinkedHashMap<>();
        m.put("total", total);
        m.put("heldCount", held);
        m.put("releasedCount", released);
        m.put("refundedCount", refunded);
        m.put("heldAmount", heldAmt);
        m.put("releasedAmount", releasedAmt);
        m.put("refundedAmount", refundedAmt);
        m.put("refundRate", refundRate);
        return m;
    }

    private static long nz(Long v) {
        return v == null ? 0 : v;
    }
}
