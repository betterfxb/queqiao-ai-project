package com.queqiao.server.service;

import com.queqiao.server.common.BizException;
import com.queqiao.server.common.Ids;
import com.queqiao.server.common.Money;
import com.queqiao.server.entity.Agent;
import com.queqiao.server.entity.CommissionRecord;
import com.queqiao.server.entity.FinanceOrder;
import com.queqiao.server.entity.Pair;
import com.queqiao.server.repo.AgentRepository;
import com.queqiao.server.repo.CommissionRepository;
import com.queqiao.server.repo.FinanceOrderRepository;
import com.queqiao.server.repo.PairRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * 红娘分佣核心：撮合成功 / 续费 两类事件按机构配置比例自动计提佣金，
 * 支持结算单生成（待结算→已结算）与提现记录（已结算→已提现）。
 *
 * 触发点：
 *  - 支付完成（FinanceService.pay）→ {@link #settleFromOrder}：续费订单计提续费佣金；
 *    新购/增值订单若会员已有「已成功」牵线，则计提成功佣金。
 *  - 牵线成功（MatchController 置为「已成功」）→ {@link #settleFromPair}：为该牵线双方已支付订单计提成功佣金。
 *
 * 幂等：以 (orderId + type) 唯一去重，重复触发不会重复计提。全部写操作在事务内完成。
 */
@Service
public class CommissionService {

    private static final String SUCCESS = "成功";
    private static final String RENEWAL = "续费";
    private static final String PENDING = "待结算";
    private static final String SETTLED = "已结算";
    private static final String WITHDRAWN = "已提现";

    private static final List<String> PAID_TYPES = List.of("新购", "增值");

    private final CommissionRepository commissionRepo;
    private final FinanceOrderRepository orderRepo;
    private final PairRepository pairRepo;
    private final AgentRepository agentRepo;
    private final SettingService settings;
    private final LogService logService;

    public CommissionService(CommissionRepository commissionRepo, FinanceOrderRepository orderRepo,
                             PairRepository pairRepo, AgentRepository agentRepo,
                             SettingService settings, LogService logService) {
        this.commissionRepo = commissionRepo;
        this.orderRepo = orderRepo;
        this.pairRepo = pairRepo;
        this.agentRepo = agentRepo;
        this.settings = settings;
        this.logService = logService;
    }

    /** 订单支付完成时调用：按订单类型分流到续费 / 成功计提 */
    public void settleFromOrder(FinanceOrder order) {
        String type = order.getOrderType();
        if (RENEWAL.equals(type)) {
            settleRenewal(order);
        } else if (type != null && PAID_TYPES.contains(type)) {
            Pair pair = findSuccessPair(order.getMemberId());
            if (pair != null) settleSuccess(order, pair);
        }
    }

    /** 牵线置为「已成功」时调用：为该牵线双方已支付订单计提成功佣金 */
    @Transactional
    public void settleFromPair(Pair pair) {
        List<FinanceOrder> paid = new ArrayList<>();
        if (pair.getMaleId() != null) paid.addAll(paidOrders(pair.getMaleId()));
        if (pair.getFemaleId() != null) paid.addAll(paidOrders(pair.getFemaleId()));
        for (FinanceOrder o : paid) {
            if (commissionRepo.findByOrderIdAndType(o.getId(), SUCCESS).isEmpty()) {
                settleSuccess(o, pair);
            }
        }
    }

    /** 成功撮合佣金：幂等（同一订单只计提一次） */
    private void settleSuccess(FinanceOrder order, Pair pair) {
        if (commissionRepo.findByOrderIdAndType(order.getId(), SUCCESS).isPresent()) return;
        double rate = settings.getInt("commission.successRate", 10);
        BigDecimal amt = Money.percent(order.getAmount(), rate);
        CommissionRecord r = new CommissionRecord();
        r.setId(Ids.biz("CM"));
        r.setAgentName(pair != null && pair.getAgent() != null ? pair.getAgent() : order.getAgent());
        r.setAgentId(resolveAgentId(r.getAgentName()));
        r.setOrderId(order.getId());
        r.setPairId(pair != null ? pair.getId() : null);
        r.setType(SUCCESS);
        r.setAmount(amt);
        r.setOrderAmount(order.getAmount());
        r.setRate(rate);
        r.setStatus(PENDING);
        r.setCreatedAt(LocalDateTime.now());
        commissionRepo.save(r);
        logService.record("生成成功佣金", r.getId(),
                r.getAgentName() + " 订单" + order.getOrderNo() + " 计提 ¥" + amt);
    }

    /** 续费佣金：幂等 */
    private void settleRenewal(FinanceOrder order) {
        if (commissionRepo.findByOrderIdAndType(order.getId(), RENEWAL).isPresent()) return;
        double rate = settings.getInt("commission.renewRate", 15);
        BigDecimal amt = Money.percent(order.getAmount(), rate);
        CommissionRecord r = new CommissionRecord();
        r.setId(Ids.biz("CM"));
        r.setAgentName(order.getAgent());
        r.setAgentId(resolveAgentId(r.getAgentName()));
        r.setOrderId(order.getId());
        r.setPairId(null);
        r.setType(RENEWAL);
        r.setAmount(amt);
        r.setOrderAmount(order.getAmount());
        r.setRate(rate);
        r.setStatus(PENDING);
        r.setCreatedAt(LocalDateTime.now());
        commissionRepo.save(r);
        logService.record("生成续费佣金", r.getId(),
                r.getAgentName() + " 订单" + order.getOrderNo() + " 计提 ¥" + amt);
    }

    /** 生成结算单：待结算 → 已结算（管理员） */
    @Transactional
    public Map<String, Object> generateSettlement(String agentName, LocalDate from, LocalDate to) {
        List<CommissionRecord> list = pending(agentName, from, to);
        String batchNo = "ST" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
        BigDecimal total = BigDecimal.ZERO;
        for (CommissionRecord r : list) {
            r.setStatus(SETTLED);
            r.setSettledAt(LocalDateTime.now());
            r.setBatchNo(batchNo);
            total = Money.add(total, r.getAmount());
            commissionRepo.save(r);
        }
        logService.record("生成结算单", batchNo, "笔数 " + list.size() + " 金额 ¥" + total);
        Map<String, Object> res = new LinkedHashMap<>();
        res.put("batchNo", batchNo);
        res.put("count", list.size());
        res.put("total", total);
        return res;
    }

    /** 提现：已结算 → 已提现（管理员） */
    @Transactional
    public Map<String, Object> withdraw(String agentName, LocalDate from, LocalDate to) {
        List<CommissionRecord> list = settled(agentName, from, to);
        BigDecimal total = BigDecimal.ZERO;
        for (CommissionRecord r : list) {
            r.setStatus(WITHDRAWN);
            r.setWithdrawnAt(LocalDateTime.now());
            total = Money.add(total, r.getAmount());
            commissionRepo.save(r);
        }
        logService.record("佣金提现", "withdraw", "笔数 " + list.size() + " 金额 ¥" + total);
        Map<String, Object> res = new LinkedHashMap<>();
        res.put("count", list.size());
        res.put("total", total);
        return res;
    }

    /** 红娘本人佣金（按当前登录红娘姓名过滤） */
    public List<CommissionRecord> mine(String agentName) {
        if (agentName == null) return List.of();
        return commissionRepo.findByAgentName(agentName);
    }

    /** 团队佣金（主管/管理员，可按红娘姓名过滤） */
    public List<CommissionRecord> team(String agentName) {
        return agentName == null || agentName.isBlank()
                ? commissionRepo.findAll()
                : commissionRepo.findByAgentName(agentName);
    }

    /** 聚合统计：按状态汇总金额/笔数；团队维度额外给出「分佣占营收比」 */
    public Map<String, Object> summary(String scope, String agentName) {
        List<CommissionRecord> list = "mine".equals(scope)
                ? mine(agentName)
                : team(agentName);
        BigDecimal pending = BigDecimal.ZERO, settled = BigDecimal.ZERO, withdrawn = BigDecimal.ZERO, total = BigDecimal.ZERO;
        int pC = 0, sC = 0, wC = 0;
        for (CommissionRecord r : list) {
            BigDecimal a = Money.nz(r.getAmount());
            total = Money.add(total, a);
            if (PENDING.equals(r.getStatus())) { pending = Money.add(pending, a); pC++; }
            else if (SETTLED.equals(r.getStatus())) { settled = Money.add(settled, a); sC++; }
            else if (WITHDRAWN.equals(r.getStatus())) { withdrawn = Money.add(withdrawn, a); wC++; }
        }
        Map<String, Object> res = new LinkedHashMap<>();
        res.put("pendingAmount", pending);
        res.put("settledAmount", settled);
        res.put("withdrawnAmount", withdrawn);
        res.put("totalAmount", total);
        res.put("pendingCount", pC);
        res.put("settledCount", sC);
        res.put("withdrawnCount", wC);
        res.put("totalCount", list.size());
        if (!"mine".equals(scope)) {
            BigDecimal revenue = Money.sum(orderRepo.findByStatus("已完成"),
                    o -> "退款".equals(o.getType()) ? null : o.getAmount());
            double ratio = Money.gtZero(revenue) ? Money.toDouble(total) * 100.0 / Money.toDouble(revenue) : 0.0;
            res.put("revenueTotal", revenue);
            res.put("commissionRatio", ratio);
        }
        return res;
    }

    // —— 内部辅助 ——

    private List<FinanceOrder> paidOrders(String memberId) {
        List<FinanceOrder> all = orderRepo.findByMemberIdAndStatus(memberId, "已完成");
        List<FinanceOrder> res = new ArrayList<>();
        for (FinanceOrder o : all) {
            if (o.getOrderType() != null && PAID_TYPES.contains(o.getOrderType())) res.add(o);
        }
        return res;
    }

    private Pair findSuccessPair(String memberId) {
        if (memberId == null) return null;
        List<Pair> m = pairRepo.findByMaleIdAndStatus(memberId, "已成功");
        if (m != null && !m.isEmpty()) return m.get(0);
        List<Pair> f = pairRepo.findByFemaleIdAndStatus(memberId, "已成功");
        return (f != null && !f.isEmpty()) ? f.get(0) : null;
    }

    private List<CommissionRecord> pending(String agentName, LocalDate from, LocalDate to) {
        List<CommissionRecord> all = agentName == null || agentName.isBlank()
                ? commissionRepo.findByStatus(PENDING)
                : commissionRepo.findByAgentNameAndStatus(agentName, PENDING);
        return inRange(all, from, to);
    }

    private List<CommissionRecord> settled(String agentName, LocalDate from, LocalDate to) {
        List<CommissionRecord> all = agentName == null || agentName.isBlank()
                ? commissionRepo.findByStatus(SETTLED)
                : commissionRepo.findByAgentNameAndStatus(agentName, SETTLED);
        return inRange(all, from, to);
    }

    private List<CommissionRecord> inRange(List<CommissionRecord> list, LocalDate from, LocalDate to) {
        if (from == null && to == null) return list;
        LocalDateTime f = from == null ? LocalDateTime.of(1970, 1, 1, 0, 0) : from.atStartOfDay();
        LocalDateTime t = to == null ? LocalDateTime.now().plusDays(1) : to.plusDays(1).atStartOfDay();
        List<CommissionRecord> res = new ArrayList<>();
        for (CommissionRecord r : list) {
            LocalDateTime c = r.getCreatedAt();
            if (c != null && !c.isBefore(f) && c.isBefore(t)) res.add(r);
        }
        return res;
    }

    private String resolveAgentId(String name) {
        if (name == null) return null;
        Agent a = agentRepo.findByName(name).orElse(null);
        return a == null ? null : a.getId();
    }

    private static double round(double v) {
        return Math.round(v * 100.0) / 100.0;
    }
}
