package com.queqiao.server.service;

import com.queqiao.server.common.BizException;
import com.queqiao.server.common.Ids;
import com.queqiao.server.common.Money;
import com.queqiao.server.entity.CrmLead;
import com.queqiao.server.entity.FinanceOrder;
import com.queqiao.server.entity.MarketChannel;
import com.queqiao.server.entity.Member;
import com.queqiao.server.repo.CrmLeadRepository;
import com.queqiao.server.repo.FinanceOrderRepository;
import com.queqiao.server.repo.MarketChannelRepository;
import com.queqiao.server.repo.MemberRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

/**
 * 渠道归因与获客分析（#111 5.3.5）。
 *
 * 归因链路：线索 {@code CrmLead.channel} → 阶段「已成交」记为转化
 * → 通过手机号匹配 {@link Member} → 汇总该会员「已完成」订单营收。
 *
 * 指标口径：
 *  cost      = 渠道累计投入 + Σ 线索单条成本
 *  CAC       = cost / dealCount
 *  ROI(%)    = (revenue - cost) / cost × 100
 *  convRate  = dealCount / leadCount × 100
 */
@Service
public class ChannelService {

    private static final String DEAL_STAGE = "已成交";

    private final MarketChannelRepository repo;
    private final CrmLeadRepository leads;
    private final MemberRepository members;
    private final FinanceOrderRepository orders;
    private final LogService logs;

    public ChannelService(MarketChannelRepository repo, CrmLeadRepository leads,
                          MemberRepository members, FinanceOrderRepository orders, LogService logs) {
        this.repo = repo;
        this.leads = leads;
        this.members = members;
        this.orders = orders;
        this.logs = logs;
    }

    public List<MarketChannel> list(String status) {
        return (status == null || status.isBlank())
                ? repo.findAllByOrderByCreatedAtDesc()
                : repo.findByStatusOrderByCreatedAtDesc(status);
    }

    public MarketChannel detail(String id) {
        return repo.findById(id).orElseThrow(() -> BizException.notFound("渠道"));
    }

    @Transactional
    public MarketChannel save(String id, Map<String, Object> body) {
        MarketChannel c;
        if (id == null || id.isBlank()) {
            c = new MarketChannel();
            c.setId(Ids.biz("MC"));
            c.setStatus("启用");
            c.setCost(0.0);
            c.setCreatedAt(LocalDateTime.now());
        } else {
            c = repo.findById(id).orElseThrow(() -> BizException.notFound("渠道"));
        }
        if (body.get("name") != null) c.setName(str(body.get("name")));
        if (body.get("category") != null) c.setCategory(str(body.get("category")));
        if (body.get("code") != null) c.setCode(str(body.get("code")));
        if (body.get("cost") != null) c.setCost(dbl(body.get("cost")));
        if (body.get("owner") != null) c.setOwner(str(body.get("owner")));
        if (body.get("status") != null) c.setStatus(str(body.get("status")));
        if (body.get("remark") != null) c.setRemark(str(body.get("remark")));
        if (c.getName() == null || c.getName().isBlank()) throw new BizException("缺少渠道名称");
        if (c.getCode() == null || c.getCode().isBlank()) c.setCode(c.getName());
        if (c.getCost() == null) c.setCost(0.0);
        c.setUpdatedAt(LocalDateTime.now());
        repo.save(c);
        logs.record(id == null ? "新增获客渠道" : "编辑获客渠道", c.getId(),
                c.getName() + " 投入 ¥" + c.getCost());
        return c;
    }

    @Transactional
    public void delete(String id) {
        MarketChannel c = repo.findById(id).orElseThrow(() -> BizException.notFound("渠道"));
        repo.deleteById(id);
        logs.record("删除获客渠道", id, c.getName());
    }

    /**
     * ROI 聚合。以「已登记渠道」为主轴；线索里出现但未登记的渠道以
     * 「(未登记) 渠道名」形式补充，保证归因不丢数据。
     */
    public Map<String, Object> roi(LocalDate from, LocalDate to) {
        LocalDateTime f = from == null ? LocalDateTime.of(1970, 1, 1, 0, 0) : from.atStartOfDay();
        LocalDateTime t = to == null ? LocalDateTime.now().plusYears(10) : to.plusDays(1).atStartOfDay();

        List<CrmLead> scoped = leads.findAll().stream()
                .filter(l -> l.getCreatedAt() == null || (!l.getCreatedAt().isBefore(f) && l.getCreatedAt().isBefore(t)))
                .toList();

        // 手机号 → 会员，用于把线索关联到营收
        Map<String, Member> byPhone = new HashMap<>();
        for (Member m : members.findAll()) {
            if (m.getPhone() != null && !m.getPhone().isBlank()) byPhone.putIfAbsent(m.getPhone(), m);
        }
        // 会员 → 已完成订单营收
        Map<String, BigDecimal> revenueByMember = new HashMap<>();
        for (FinanceOrder o : orders.findAll()) {
            if (!"已完成".equals(o.getStatus())) continue;
            if (o.getType() != null && o.getType().contains("退款")) continue;
            if (o.getMemberId() == null) continue;
            revenueByMember.merge(o.getMemberId(), Money.nz(o.getAmount()), Money::add);
        }

        // 渠道桶
        LinkedHashMap<String, Bucket> buckets = new LinkedHashMap<>();
        for (MarketChannel c : repo.findAllByOrderByCreatedAtDesc()) {
            Bucket b = buckets.computeIfAbsent(key(c.getName()), k -> new Bucket());
            b.name = c.getName();
            b.category = c.getCategory();
            b.channelId = c.getId();
            b.status = c.getStatus();
            b.cost += c.getCost() == null ? 0 : c.getCost();
            if (c.getCode() != null && !c.getCode().isBlank()) b.codes.add(key(c.getCode()));
            b.codes.add(key(c.getName()));
        }
        // 名称/短码 → 桶
        Map<String, Bucket> index = new HashMap<>();
        buckets.values().forEach(b -> b.codes.forEach(code -> index.putIfAbsent(code, b)));

        for (CrmLead l : scoped) {
            String raw = l.getChannel() != null && !l.getChannel().isBlank() ? l.getChannel() : l.getSource();
            String k = key(raw);
            Bucket b = index.get(k);
            if (b == null) {
                String label = (raw == null || raw.isBlank()) ? "未标记" : raw;
                b = buckets.computeIfAbsent(k.isEmpty() ? "__none__" : k, x -> {
                    Bucket nb = new Bucket();
                    nb.name = label;
                    nb.category = "未登记";
                    nb.registered = false;
                    return nb;
                });
                index.putIfAbsent(k, b);
            }
            b.leadCount++;
            b.cost += l.getCost() == null ? 0 : l.getCost();
            if (DEAL_STAGE.equals(l.getStage())) {
                b.dealCount++;
                // 硬关联优先：先按线索的 memberId，再回退手机号匹配（生产环境以 memberId 为准）
                Member m = l.getMemberId() != null ? members.findById(l.getMemberId()).orElse(null)
                        : (l.getPhone() == null ? null : byPhone.get(l.getPhone()));
                if (m != null) b.revenue += Money.toDouble(revenueByMember.getOrDefault(m.getId(), BigDecimal.ZERO));
            }
        }

        List<Map<String, Object>> rows = new ArrayList<>();
        double totalCost = 0, totalRevenue = 0;
        long totalLead = 0, totalDeal = 0;
        for (Bucket b : buckets.values()) {
            totalCost += b.cost;
            totalRevenue += b.revenue;
            totalLead += b.leadCount;
            totalDeal += b.dealCount;
            rows.add(b.toRow());
        }
        rows.sort((a, b) -> Double.compare(num(b.get("revenue")), num(a.get("revenue"))));

        Map<String, Object> res = new LinkedHashMap<>();
        res.put("rows", rows);
        res.put("channelCount", rows.size());
        res.put("leadTotal", totalLead);
        res.put("dealTotal", totalDeal);
        res.put("costTotal", round(totalCost));
        res.put("revenueTotal", round(totalRevenue));
        res.put("cacAvg", totalDeal > 0 ? round(totalCost / totalDeal) : 0.0);
        res.put("roiTotal", totalCost > 0 ? round((totalRevenue - totalCost) / totalCost * 100) : 0.0);
        res.put("convRateTotal", totalLead > 0 ? round(totalDeal * 100.0 / totalLead) : 0.0);
        res.put("from", from == null ? null : from.toString());
        res.put("to", to == null ? null : to.toString());
        res.put("updatedAt", LocalDateTime.now().toString());
        return res;
    }

    private static class Bucket {
        String channelId;
        String name;
        String category;
        String status;
        boolean registered = true;
        Set<String> codes = new HashSet<>();
        long leadCount;
        long dealCount;
        double cost;
        double revenue;

        Map<String, Object> toRow() {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("channelId", channelId);
            m.put("name", name);
            m.put("category", category);
            m.put("status", status);
            m.put("registered", registered);
            m.put("leadCount", leadCount);
            m.put("dealCount", dealCount);
            m.put("convRate", leadCount > 0 ? round(dealCount * 100.0 / leadCount) : 0.0);
            m.put("cost", round(cost));
            m.put("revenue", round(revenue));
            m.put("cac", dealCount > 0 ? round(cost / dealCount) : 0.0);
            m.put("roi", cost > 0 ? round((revenue - cost) / cost * 100) : 0.0);
            m.put("profit", round(revenue - cost));
            return m;
        }
    }

    private static String key(String s) {
        return s == null ? "" : s.trim().toLowerCase();
    }
    private static double num(Object o) {
        return o instanceof Number n ? n.doubleValue() : 0;
    }
    private static String str(Object o) { return o == null ? null : String.valueOf(o); }
    private static Double dbl(Object o) {
        if (o == null) return null;
        try { return Double.parseDouble(String.valueOf(o)); } catch (Exception e) { return null; }
    }
    private static double round(double v) { return Math.round(v * 100.0) / 100.0; }
}
