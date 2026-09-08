package com.queqiao.server.service;

import com.queqiao.server.common.Ids;
import com.queqiao.server.common.Money;
import com.queqiao.server.entity.*;
import com.queqiao.server.repo.*;
import jakarta.annotation.PostConstruct;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.*;

/**
 * 支付对账服务：每日比对本地订单（t_finance_order）与渠道流水（t_channel_ledger），
 * 差异写入 t_pay_reconcile 供「对账」页查询。
 */
@Service
public class ReconcileService {

    private final FinanceOrderRepository orders;
    private final ChannelLedgerRepository ledgers;
    private final PayReconcileRepository reconciles;
    private final LogService logService;

    public ReconcileService(FinanceOrderRepository orders, ChannelLedgerRepository ledgers,
                            PayReconcileRepository reconciles, LogService logService) {
        this.orders = orders;
        this.ledgers = ledgers;
        this.reconciles = reconciles;
        this.logService = logService;
    }

    /**
     * 启动回填：为历史已支付订单补建渠道流水，使其对账干净（幂等）。
     */
    @PostConstruct
    public void backfillLegacy() {
        backfillLedgers();
    }

    /**
     * 回填缺失的渠道流水（幂等，可重复调用）。
     * <p>覆盖范围与 {@link #reconcile(LocalDate)} 的稽核范围保持一致：终态为「已完成」或「已退款」的收款单。
     * 此前仅回填「已完成」，导致「已退款」订单一旦流水缺失便永远无法修复、每日对账恒报差异。
     * <p>规则：
     * <ul>
     *   <li>缺少 PAID 流水 → 按订单金额补一条 PAID；</li>
     *   <li>「已退款」订单缺少 REFUND 冲正流水 → 补一条负数 REFUND；</li>
     *   <li>负数退款单（type=退款）本身不是渠道收款，不生成流水。</li>
     * </ul>
     *
     * @return 新建流水条数
     */
    @Transactional
    public int backfillLedgers() {
        int created = 0;
        for (FinanceOrder o : orders.findAll()) {
            String st = o.getStatus();
            if (!"已完成".equals(st) && !"已退款".equals(st)) continue;
            if ("退款".equals(o.getType())) continue; // 负数退款单不产生渠道收款流水
            List<ChannelLedger> lgs = ledgers.findByOrderId(o.getId());
            if (lgs.stream().noneMatch(x -> "PAID".equals(x.getStatus()))) {
                ledgers.save(legacyRow(o, "PAID", o.getAmount()));
                created++;
            }
            if ("已退款".equals(st) && lgs.stream().noneMatch(x -> "REFUND".equals(x.getStatus()))) {
                BigDecimal amt = Money.abs(o.getAmount());
                created++;
                ledgers.save(legacyRow(o, "REFUND", amt.negate()));
            }
        }
        return created;
    }

    private ChannelLedger legacyRow(FinanceOrder o, String status, BigDecimal amount) {
        ChannelLedger l = new ChannelLedger();
        l.setId(Ids.biz("CL"));
        l.setOrderId(o.getId());
        l.setOrderNo(o.getOrderNo());
        l.setTradeNo(o.getTradeNo() != null ? o.getTradeNo() : "LEGACY-" + o.getOrderNo());
        l.setChannel(o.getChannel() != null ? o.getChannel() : "线下");
        l.setAmount(amount);
        l.setStatus(status);
        l.setPaidAt(o.getPaidAt() != null ? o.getPaidAt() : o.getCreatedAt());
        l.setCreatedAt(o.getCreatedAt() != null ? o.getCreatedAt() : LocalDateTime.now());
        return l;
    }

    /** 对账某日：本地订单 vs 渠道流水，差异写入 t_pay_reconcile（覆盖当天旧差异）。 */
    @Transactional
    public ReconcileResult reconcile(LocalDate date) {
        LocalDateTime from = date.atStartOfDay();
        LocalDateTime to = date.atTime(LocalTime.MAX);

        List<FinanceOrder> paidOrders = orders.findAll().stream()
                .filter(o -> o.getPaidAt() != null
                        && !o.getPaidAt().isBefore(from) && !o.getPaidAt().isAfter(to))
                .filter(o -> "已完成".equals(o.getStatus()) || "已退款".equals(o.getStatus()))
                .toList();
        List<ChannelLedger> dayLedgers = ledgers.findByCreatedAtBetween(from, to);

        // 全部本地订单 id（孤儿判定用：渠道有流水但本地根本无此订单，与日期无关）
        Set<String> allOrderIds = new HashSet<>();
        orders.findAll().forEach(o -> allOrderIds.add(o.getId()));

        List<PayReconcile> diffs = new ArrayList<>();
        int matched = 0;
        for (FinanceOrder o : paidOrders) {
            List<ChannelLedger> lgs = ledgers.findByOrderId(o.getId());
            // 优先取 PAID 流水做金额比对（退款流水 REFUND 为渠道侧冲正记录）
            ChannelLedger l = lgs.stream().filter(x -> "PAID".equals(x.getStatus())).findFirst()
                    .orElse(lgs.stream().findFirst().orElse(null));
            if (l == null) {
                diffs.add(diff(date, o, null, "本地有支付/退款，渠道无流水"));
                continue;
            }
            BigDecimal local = Money.nz(o.getAmount());
            BigDecimal ch = Money.nz(l.getAmount());
            if (Money.abs(Money.sub(local, ch)).compareTo(BigDecimal.valueOf(0.001)) > 0) {
                diffs.add(diff(date, o, l, "金额不一致 本地¥" + local + " 渠道¥" + ch));
                continue;
            }
            matched++;
        }
        // 渠道有流水、本地无对应订单（孤儿流水，按全量订单 id 判定，不受对账日期限制）
        for (ChannelLedger l : dayLedgers) {
            if ("PAID".equals(l.getStatus()) && !allOrderIds.contains(l.getOrderId())) {
                PayReconcile d = new PayReconcile();
                d.setId(Ids.biz("PR"));
                d.setReconcileDate(date);
                d.setOrderId(l.getOrderId());
                d.setOrderNo(l.getOrderNo());
                d.setChannel(l.getChannel());
                d.setTradeNo(l.getTradeNo());
                d.setLocalAmount(BigDecimal.ZERO);
                d.setChannelAmount(l.getAmount());
                d.setDiff(l.getAmount());
                d.setMatched(false);
                d.setNote("渠道有流水，本地无订单");
                d.setCreatedAt(LocalDateTime.now());
                diffs.add(d);
            }
        }

        reconciles.deleteByReconcileDate(date);
        diffs.forEach(reconciles::save);

        ReconcileResult res = new ReconcileResult();
        res.date = date;
        res.checked = paidOrders.size();
        res.matched = matched;
        res.diffCount = diffs.size();
        res.diffs = diffs;
        if (!diffs.isEmpty()) {
            logService.record("支付对账", date.toString(), "差异 " + diffs.size() + " 笔");
        }
        return res;
    }

    /** 每日 02:00 自动对账昨日 */
    @Scheduled(cron = "0 0 2 * * *")
    public void runDaily() {
        reconcile(LocalDate.now().minusDays(1));
    }

    /** 最近 N 天对账差异列表 */
    public List<PayReconcile> recent(int days) {
        LocalDateTime from = LocalDate.now().minusDays(Math.max(1, days)).atStartOfDay();
        return reconciles.findByCreatedAtBetween(from, LocalDateTime.now());
    }

    private PayReconcile diff(LocalDate date, FinanceOrder o, ChannelLedger l, String note) {
        PayReconcile d = new PayReconcile();
        d.setId(Ids.biz("PR"));
        d.setReconcileDate(date);
        d.setOrderId(o.getId());
        d.setOrderNo(o.getOrderNo());
        d.setChannel(o.getChannel());
        d.setTradeNo(o.getTradeNo());
        d.setLocalAmount(o.getAmount());
        d.setChannelAmount(l == null ? null : l.getAmount());
        BigDecimal local = Money.nz(o.getAmount());
        BigDecimal ch = l == null ? Money.ZERO : Money.nz(l.getAmount());
        d.setDiff(Money.sub(local, ch));
        d.setMatched(false);
        d.setNote(note);
        d.setCreatedAt(LocalDateTime.now());
        return d;
    }

    public static class ReconcileResult {
        public LocalDate date;
        public int checked;
        public int matched;
        public int diffCount;
        public List<PayReconcile> diffs;
    }
}
