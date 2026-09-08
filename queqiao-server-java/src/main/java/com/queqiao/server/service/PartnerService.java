package com.queqiao.server.service;

import com.queqiao.server.common.BizException;
import com.queqiao.server.common.Ids;
import com.queqiao.server.common.Money;
import com.queqiao.server.entity.*;
import com.queqiao.server.repo.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

/**
 * 产业链延伸导流（#111 5.3.4）：把「成功脱单」变成二次收入。
 *
 * 状态流：已导流 → 已成交 → 已结算（任意阶段可 已取消）。
 * 计佣沿用 {@link CommissionService} 的幂等范式：已成交/已结算不重复计佣。
 */
@Service
public class PartnerService {

    public static final String LEAD = "已导流";
    public static final String DEAL = "已成交";
    public static final String SETTLED = "已结算";
    public static final String CANCELED = "已取消";

    /** 成功脱单后默认推荐的类目优先级 */
    private static final List<String> DEFAULT_CATEGORIES =
            List.of("婚庆策划", "婚纱摄影", "婚宴酒店", "珠宝首饰", "蜜月旅行", "情感咨询");

    private final PartnerMerchantRepository merchants;
    private final MerchantOrderRepository orders;
    private final MemberRepository members;
    private final PairRepository pairs;
    private final LogService logs;

    public PartnerService(PartnerMerchantRepository merchants, MerchantOrderRepository orders,
                          MemberRepository members, PairRepository pairs, LogService logs) {
        this.merchants = merchants;
        this.orders = orders;
        this.members = members;
        this.pairs = pairs;
        this.logs = logs;
    }

    // —— 商户 ——

    public List<PartnerMerchant> listMerchants(String category, String status, String city) {
        return merchants.findAllByOrderByCreatedAtDesc().stream()
                .filter(m -> category == null || category.isBlank() || category.equals(m.getCategory()))
                .filter(m -> status == null || status.isBlank() || status.equals(m.getStatus()))
                .filter(m -> city == null || city.isBlank() || city.equals(m.getCity()))
                .toList();
    }

    @Transactional
    public PartnerMerchant saveMerchant(String id, Map<String, Object> body) {
        PartnerMerchant m;
        if (id == null || id.isBlank()) {
            m = new PartnerMerchant();
            m.setId(Ids.biz("PM"));
            m.setStatus("合作中");
            m.setCreatedAt(LocalDateTime.now());
        } else {
            m = merchants.findById(id).orElseThrow(() -> BizException.notFound("合作商户"));
        }
        if (body.get("name") != null) m.setName(str(body.get("name")));
        if (body.get("category") != null) m.setCategory(str(body.get("category")));
        if (body.get("city") != null) m.setCity(str(body.get("city")));
        if (body.get("contact") != null) m.setContact(str(body.get("contact")));
        if (body.get("phone") != null) m.setPhone(str(body.get("phone")));
        if (body.get("commissionRate") != null) m.setCommissionRate(dbl(body.get("commissionRate")));
        if (body.get("status") != null) m.setStatus(str(body.get("status")));
        if (body.get("remark") != null) m.setRemark(str(body.get("remark")));
        if (m.getName() == null || m.getName().isBlank()) throw new BizException("缺少商户名称");
        if (m.getCommissionRate() == null) m.setCommissionRate(10.0);
        m.setUpdatedAt(LocalDateTime.now());
        merchants.save(m);
        logs.record(id == null ? "新增合作商户" : "编辑合作商户", m.getId(),
                m.getName() + " · " + nz(m.getCategory()) + " " + m.getCommissionRate() + "%");
        return m;
    }

    @Transactional
    public void deleteMerchant(String id) {
        PartnerMerchant m = merchants.findById(id).orElseThrow(() -> BizException.notFound("合作商户"));
        merchants.deleteById(id);
        logs.record("删除合作商户", id, m.getName());
    }

    // —— 导流单 ——

    /** 创建导流单：比例快照自商户，避免后续改率影响历史单 */
    @Transactional
    public MerchantOrder createOrder(String merchantId, String memberId, String pairId,
                                     Double amount, String agent, String note) {
        PartnerMerchant pm = merchants.findById(merchantId)
                .orElseThrow(() -> BizException.notFound("合作商户"));
        if ("已暂停".equals(pm.getStatus())) throw new BizException("该商户已暂停合作，无法导流");
        Member m = memberId == null ? null : members.findById(memberId).orElse(null);
        if (memberId != null && m == null) throw BizException.notFound("会员");

        MerchantOrder o = new MerchantOrder();
        o.setId(Ids.biz("MO"));
        o.setMerchantId(pm.getId());
        o.setMerchantName(pm.getName());
        o.setCategory(pm.getCategory());
        o.setMemberId(memberId);
        o.setMemberName(m == null ? null : m.getName());
        o.setPairId(pairId);
        o.setAmount(Money.of(amount));
        o.setCommissionRate(pm.getCommissionRate());
        o.setCommissionAmount(BigDecimal.ZERO);
        o.setStatus(LEAD);
        o.setAgent(agent);
        o.setNote(note);
        o.setCreatedAt(LocalDateTime.now());
        orders.save(o);
        logs.record("产业链导流", o.getId(),
                (m == null ? "" : m.getName() + " → ") + pm.getName());
        return o;
    }

    /** 标记成交并计佣；幂等：已成交/已结算直接返回 */
    @Transactional
    public MerchantOrder deal(String id, Double amount) {
        MerchantOrder o = orders.findById(id).orElseThrow(() -> BizException.notFound("导流单"));
        if (CANCELED.equals(o.getStatus())) throw new BizException("已取消的导流单不可成交");
        if (DEAL.equals(o.getStatus()) || SETTLED.equals(o.getStatus())) return o; // 幂等
        if (amount != null) o.setAmount(Money.of(amount));
        if (o.getAmount() == null || !Money.gtZero(o.getAmount())) throw new BizException("成交金额必须大于 0");
        double rate = o.getCommissionRate() == null ? 0 : o.getCommissionRate();
        o.setCommissionAmount(Money.percent(o.getAmount(), rate));
        o.setStatus(DEAL);
        o.setDealAt(LocalDateTime.now());
        orders.save(o);
        logs.record("导流成交计佣", id,
                o.getMerchantName() + " ¥" + o.getAmount() + " → 分佣 ¥" + o.getCommissionAmount());
        return o;
    }

    /** 结算；幂等：已结算直接返回 */
    @Transactional
    public MerchantOrder settle(String id) {
        MerchantOrder o = orders.findById(id).orElseThrow(() -> BizException.notFound("导流单"));
        if (SETTLED.equals(o.getStatus())) return o; // 幂等
        if (!DEAL.equals(o.getStatus())) throw new BizException("仅「已成交」的导流单可结算");
        o.setStatus(SETTLED);
        o.setSettledAt(LocalDateTime.now());
        orders.save(o);
        logs.record("导流分佣结算", id, o.getMerchantName() + " ¥" + o.getCommissionAmount());
        return o;
    }

    @Transactional
    public MerchantOrder cancel(String id, String reason) {
        MerchantOrder o = orders.findById(id).orElseThrow(() -> BizException.notFound("导流单"));
        if (SETTLED.equals(o.getStatus())) throw new BizException("已结算的导流单不可取消");
        o.setStatus(CANCELED);
        o.setNote(reason);
        orders.save(o);
        logs.record("取消导流单", id, nz(o.getMerchantName()));
        return o;
    }

    public List<MerchantOrder> listOrders(String status, String memberId, String merchantId) {
        return orders.findAllByOrderByCreatedAtDesc().stream()
                .filter(o -> status == null || status.isBlank() || status.equals(o.getStatus()))
                .filter(o -> memberId == null || memberId.isBlank() || memberId.equals(o.getMemberId()))
                .filter(o -> merchantId == null || merchantId.isBlank() || merchantId.equals(o.getMerchantId()))
                .toList();
    }

    public MerchantOrder detail(String id) {
        return orders.findById(id).orElseThrow(() -> BizException.notFound("导流单"));
    }

    /**
     * 成功脱单 → 推荐商户：优先同城「合作中」商户，按默认类目优先级排序，
     * 每个类目最多推荐 1 家，避免刷屏。
     */
    public Map<String, Object> recommend(String pairId) {
        if (pairId == null || pairId.isBlank()) throw new BizException("缺少牵线ID");
        Pair p = pairs.findById(pairId).orElseThrow(() -> BizException.notFound("牵线"));
        Member male = p.getMaleId() == null ? null : members.findById(p.getMaleId()).orElse(null);
        Member female = p.getFemaleId() == null ? null : members.findById(p.getFemaleId()).orElse(null);
        String city = male != null && male.getCity() != null ? male.getCity()
                : (female != null ? female.getCity() : null);

        List<PartnerMerchant> pool = merchants.findByStatusOrderByCreatedAtDesc("合作中");
        List<PartnerMerchant> picked = new ArrayList<>();
        for (String cat : DEFAULT_CATEGORIES) {
            // 同城优先，无同城则取全国
            PartnerMerchant hit = pool.stream()
                    .filter(m -> cat.equals(m.getCategory()))
                    .filter(m -> city != null && city.equals(m.getCity()))
                    .findFirst()
                    .orElseGet(() -> pool.stream().filter(m -> cat.equals(m.getCategory())).findFirst().orElse(null));
            if (hit != null) picked.add(hit);
        }
        Map<String, Object> res = new LinkedHashMap<>();
        res.put("pairId", pairId);
        res.put("city", city);
        res.put("success", "已成功".equals(p.getStatus()));
        res.put("merchants", picked);
        res.put("existingOrders", orders.findByPairId(pairId));
        return res;
    }

    public Map<String, Object> summary() {
        List<MerchantOrder> all = orders.findAll();
        BigDecimal dealAmount = BigDecimal.ZERO, commissionTotal = BigDecimal.ZERO, settledCommission = BigDecimal.ZERO;
        long leadC = 0, dealC = 0, settledC = 0, cancelC = 0;
        for (MerchantOrder o : all) {
            switch (nz(o.getStatus())) {
                case LEAD -> leadC++;
                case DEAL -> dealC++;
                case SETTLED -> settledC++;
                case CANCELED -> cancelC++;
                default -> { }
            }
            if (DEAL.equals(o.getStatus()) || SETTLED.equals(o.getStatus())) {
                dealAmount = Money.add(dealAmount, o.getAmount());
                commissionTotal = Money.add(commissionTotal, o.getCommissionAmount());
            }
            if (SETTLED.equals(o.getStatus())) {
                settledCommission = Money.add(settledCommission, o.getCommissionAmount());
            }
        }
        // 按类目汇总
        Map<String, Object[]> byCat = new LinkedHashMap<>();
        for (MerchantOrder o : all) {
            String c = nz(o.getCategory());
            if (c.isEmpty()) continue;
            Object[] v = byCat.computeIfAbsent(c, x -> new Object[]{0L, BigDecimal.ZERO});
            v[0] = (Long) v[0] + 1;
            v[1] = Money.add((BigDecimal) v[1], o.getCommissionAmount());
        }
        List<Map<String, Object>> cats = byCat.entrySet().stream()
                .sorted((a, b) -> Double.compare(
                        Money.toDouble((BigDecimal) b.getValue()[1]),
                        Money.toDouble((BigDecimal) a.getValue()[1])))
                .map(e -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("category", e.getKey());
                    m.put("count", e.getValue()[0]);
                    m.put("commission", e.getValue()[1]);
                    return m;
                }).toList();

        Map<String, Object> res = new LinkedHashMap<>();
        res.put("merchantCount", merchants.count());
        res.put("orderTotal", all.size());
        res.put("leadCount", leadC);
        res.put("dealCount", dealC);
        res.put("settledCount", settledC);
        res.put("canceledCount", cancelC);
        res.put("dealAmount", dealAmount);
        res.put("commissionTotal", commissionTotal);
        res.put("settledCommission", settledCommission);
        res.put("dealRate", all.isEmpty() ? 0.0
                : Math.round((dealC + settledC) * 1000.0 / all.size()) / 10.0);
        res.put("byCategory", cats);
        return res;
    }

    private static String nz(String s) { return s == null ? "" : s; }
    private static String str(Object o) { return o == null ? null : String.valueOf(o); }
    private static Double dbl(Object o) {
        if (o == null) return null;
        try { return Double.parseDouble(String.valueOf(o)); } catch (Exception e) { return null; }
    }
    private static double round(double v) { return Math.round(v * 100.0) / 100.0; }
}
