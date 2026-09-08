package com.queqiao.server.service;

import com.queqiao.server.common.BizException;
import com.queqiao.server.common.Ids;
import com.queqiao.server.common.Money;
import com.queqiao.server.entity.*;
import com.queqiao.server.repo.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * 同城运营模板（#111 5.3.3）：城市合伙人 + 分层社群 + 活动常态化。
 * 聚合口径：活动优先按 {@code Activity.city} 匹配，为空时回退用 {@code place} 包含城市名。
 */
@Service
public class CityService {

    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final CityPartnerRepository partners;
    private final CommunityTierRepository tiers;
    private final CommunityMembershipRepository memberships;
    private final FinanceOrderRepository orders;
    private final MemberRepository members;
    private final ActivityRepository activities;
    private final AgentRepository agents;
    private final LogService logs;

    public CityService(CityPartnerRepository partners, CommunityTierRepository tiers,
                       CommunityMembershipRepository memberships, FinanceOrderRepository orders,
                       MemberRepository members, ActivityRepository activities,
                       AgentRepository agents, LogService logs) {
        this.partners = partners;
        this.tiers = tiers;
        this.memberships = memberships;
        this.orders = orders;
        this.members = members;
        this.activities = activities;
        this.agents = agents;
        this.logs = logs;
    }

    // —— 城市合伙人 ——

    public List<CityPartner> listPartners(String city, String status) {
        return partners.findAllByOrderByCreatedAtDesc().stream()
                .filter(p -> city == null || city.isBlank() || city.equals(p.getCity()))
                .filter(p -> status == null || status.isBlank() || status.equals(p.getStatus()))
                .toList();
    }

    @Transactional
    public CityPartner savePartner(String id, Map<String, Object> body) {
        CityPartner p;
        if (id == null || id.isBlank()) {
            p = new CityPartner();
            p.setId(Ids.biz("CP"));
            p.setStatus("筹备中");
            p.setCreatedAt(LocalDateTime.now());
        } else {
            p = partners.findById(id).orElseThrow(() -> BizException.notFound("城市合伙人"));
        }
        if (body.get("city") != null) p.setCity(str(body.get("city")));
        if (body.get("ownerAgentName") != null) {
            String an = str(body.get("ownerAgentName"));
            p.setOwnerAgentName(an);
            agents.findByName(an).ifPresent(a -> p.setOwnerAgentId(a.getId()));
        }
        if (body.get("ownerAgentId") != null) p.setOwnerAgentId(str(body.get("ownerAgentId")));
        if (body.get("tier") != null) p.setTier(str(body.get("tier")));
        if (body.get("status") != null) p.setStatus(str(body.get("status")));
        if (body.get("phone") != null) p.setPhone(str(body.get("phone")));
        if (body.get("shareRate") != null) p.setShareRate(dbl(body.get("shareRate")));
        if (body.get("remark") != null) p.setRemark(str(body.get("remark")));
        if (body.get("startDate") != null) {
            try { p.setStartDate(LocalDate.parse(str(body.get("startDate")))); } catch (Exception ignored) { }
        }
        if (p.getCity() == null || p.getCity().isBlank()) throw new BizException("缺少城市");
        if (p.getStartDate() == null) p.setStartDate(LocalDate.now());
        p.setUpdatedAt(LocalDateTime.now());
        partners.save(p);
        logs.record(id == null ? "新增城市合伙人" : "编辑城市合伙人", p.getId(), p.getCity() + " · " + p.getOwnerAgentName());
        return p;
    }

    @Transactional
    public void deletePartner(String id) {
        CityPartner p = partners.findById(id).orElseThrow(() -> BizException.notFound("城市合伙人"));
        partners.deleteById(id);
        logs.record("删除城市合伙人", id, p.getCity());
    }

    // —— 社群档位 ——

    public List<CommunityTier> listTiers(String city, String status) {
        return tiers.findAllByOrderBySortOrderAsc().stream()
                .filter(t -> city == null || city.isBlank() || city.equals(t.getCity()))
                .filter(t -> status == null || status.isBlank() || status.equals(t.getStatus()))
                .toList();
    }

    @Transactional
    public CommunityTier saveTier(String id, Map<String, Object> body) {
        CommunityTier t;
        if (id == null || id.isBlank()) {
            t = new CommunityTier();
            t.setId(Ids.biz("CT"));
            t.setStatus("在售");
            t.setMemberCount(0);
            t.setCreatedAt(LocalDateTime.now());
        } else {
            t = tiers.findById(id).orElseThrow(() -> BizException.notFound("社群档位"));
        }
        if (body.get("city") != null) t.setCity(str(body.get("city")));
        if (body.get("level") != null) t.setLevel(str(body.get("level")));
        if (body.get("price") != null) t.setPrice(Money.of(body.get("price")));
        if (body.get("benefits") != null) t.setBenefits(str(body.get("benefits")));
        if (body.get("capacity") != null) t.setCapacity(intv(body.get("capacity")));
        if (body.get("memberCount") != null) t.setMemberCount(intv(body.get("memberCount")));
        if (body.get("productId") != null) t.setProductId(str(body.get("productId")));
        if (body.get("status") != null) t.setStatus(str(body.get("status")));
        if (body.get("sortOrder") != null) t.setSortOrder(intv(body.get("sortOrder")));
        if (t.getLevel() == null || t.getLevel().isBlank()) throw new BizException("缺少档位名称");
        if (t.getSortOrder() == null) t.setSortOrder(defaultSort(t.getLevel()));
        if (t.getPrice() == null) t.setPrice(BigDecimal.ZERO);
        t.setUpdatedAt(LocalDateTime.now());
        tiers.save(t);
        logs.record(id == null ? "新增社群档位" : "编辑社群档位", t.getId(),
                nz(t.getCity()) + " " + t.getLevel() + " ¥" + t.getPrice());
        return t;
    }

    @Transactional
    public void deleteTier(String id) {
        CommunityTier t = tiers.findById(id).orElseThrow(() -> BizException.notFound("社群档位"));
        tiers.deleteById(id);
        logs.record("删除社群档位", id, nz(t.getCity()) + " " + t.getLevel());
    }

    // —— 社群档位支付打通（#157 D）——
    // 下单（待支付）→ FinanceService.pay 回调 onOrderPaid → 开通真实 membership + 档位人数 +1

    /**
     * 订阅下单：校验档位在售与容量，生成一笔「社群订阅」待支付订单（弱关联 productId=tierId）。
     * 返回订单；支付成功后由 {@link #onOrderPaid} 开通会员资格。
     */
    @Transactional
    public FinanceOrder subscribe(String tierId, String memberId, String memberName) {
        CommunityTier t = tiers.findById(tierId).orElseThrow(() -> BizException.notFound("社群档位"));
        if (!"在售".equals(t.getStatus())) throw new BizException("该档位已下架，无法订阅");
        if (t.getCapacity() != null && t.getCapacity() > 0
                && (t.getMemberCount() == null ? 0 : t.getMemberCount()) >= t.getCapacity()) {
            throw new BizException("档位已满（" + t.getMemberCount() + "/" + t.getCapacity() + "）");
        }
        if (memberId == null || memberId.isBlank()) throw new BizException("缺少会员标识");

        FinanceOrder o = new FinanceOrder();
        o.setId(Ids.biz("FO"));
        o.setOrderNo("QQ" + System.currentTimeMillis());
        o.setMemberId(memberId);
        o.setMember(memberName);
        o.setType("社群订阅");
        o.setAmount(Money.nz(t.getPrice()));
        o.setStatus("待支付");
        o.setChannel("线下");
        o.setProductId(t.getId());
        o.setProductName(nz(t.getCity()) + " " + nz(t.getLevel()));
        o.setOrderType("社群订阅");
        o.setAgent("系统");
        o.setCreatedAt(LocalDateTime.now());
        o.setUpdatedAt(LocalDateTime.now());
        o.setTime(o.getCreatedAt().format(TS));
        orders.save(o);
        logs.record("社群档位订阅下单", t.getId(), nz(t.getCity()) + " " + nz(t.getLevel()) + " · " + memberName);
        return o;
    }

    /**
     * 支付回调（幂等）：仅处理「社群订阅」订单；按 orderId 去重，已开通则直接返回。
     * 首次开通时创建真实 membership 并自增档位 memberCount。
     */
    @Transactional
    public CommunityMembership onOrderPaid(FinanceOrder o) {
        if (!"社群订阅".equals(o.getOrderType())) return null;
        Optional<CommunityMembership> exist = memberships.findByOrderId(o.getId());
        if (exist.isPresent()) return exist.get(); // 幂等：已开通

        CommunityTier t = (o.getProductId() != null) ? tiers.findById(o.getProductId()).orElse(null) : null;
        CommunityMembership m = new CommunityMembership();
        m.setId(Ids.biz("CM"));
        m.setTierId(o.getProductId());
        m.setTierLevel(t != null ? t.getLevel() : null);
        m.setCity(t != null ? t.getCity() : null);
        m.setMemberId(o.getMemberId());
        m.setMemberName(o.getMember());
        m.setOrderId(o.getId());
        m.setStatus("有效");
        m.setActiveFrom(LocalDateTime.now());
        m.setActiveTo(activeToByLevel(t != null ? t.getLevel() : null));
        m.setCreatedAt(LocalDateTime.now());
        m.setUpdatedAt(LocalDateTime.now());
        memberships.save(m);

        if (t != null) {
            t.setMemberCount((t.getMemberCount() == null ? 0 : t.getMemberCount()) + 1);
            t.setUpdatedAt(LocalDateTime.now());
            tiers.save(t);
        }
        logs.record("社群档位开通", m.getId(), (t != null ? t.getLevel() : "") + " · " + o.getMember());
        return m;
    }

    /** 档位会员列表（按会员维度真实记录） */
    public List<CommunityMembership> memberships(String tierId) {
        List<CommunityMembership> list = tierId == null || tierId.isBlank()
                ? memberships.findAll()
                : memberships.findByTierId(tierId);
        list.sort((a, b) -> {
            LocalDateTime x = a.getCreatedAt(), y = b.getCreatedAt();
            if (x == null) return 1;
            if (y == null) return -1;
            return y.compareTo(x);
        });
        return list;
    }

    private static LocalDateTime activeToByLevel(String level) {
        LocalDateTime now = LocalDateTime.now();
        if ("VIP".equals(level)) return now.plusDays(365);
        if ("轻会员".equals(level)) return now.plusDays(90);
        if ("免费群".equals(level)) return now.plusDays(7);
        return now.plusDays(30);
    }

    // —— 聚合 ——

    /** 单城运营总览 */
    public Map<String, Object> overview(String city) {
        if (city == null || city.isBlank()) throw new BizException("缺少城市");
        List<Member> cityMembers = members.findAll().stream()
                .filter(m -> city.equals(m.getCity())).toList();
        List<Activity> cityActs = activities.findAll().stream()
                .filter(a -> matchCity(a, city)).toList();
        List<CommunityTier> cityTiers = tiers.findAllByOrderBySortOrderAsc().stream()
                .filter(t -> city.equals(t.getCity())).toList();
        List<CityPartner> cityPartners = partners.findByCity(city);

        BigDecimal actRevenue = Money.sum(cityActs, Activity::getRevenue);
        int signed = cityActs.stream().mapToInt(a -> a.getSigned() == null ? 0 : a.getSigned()).sum();
        int communityMembers = cityTiers.stream()
                .mapToInt(t -> t.getMemberCount() == null ? 0 : t.getMemberCount()).sum();
        BigDecimal communityRevenue = Money.sum(cityTiers,
                t -> Money.times(Money.nz(t.getPrice()),
                        (long) (t.getMemberCount() == null ? 0 : t.getMemberCount())));

        List<CommunityMembership> activeMemberships = memberships.findByCity(city).stream()
                .filter(m -> "有效".equals(m.getStatus())).toList();

        Map<String, Object> res = new LinkedHashMap<>();
        res.put("city", city);
        res.put("memberCount", cityMembers.size());
        res.put("activityCount", cityActs.size());
        res.put("activitySigned", signed);
        res.put("activityRevenue", actRevenue);
        res.put("partnerCount", cityPartners.size());
        res.put("partners", cityPartners);
        res.put("tiers", cityTiers);
        res.put("communityMembers", communityMembers);
        res.put("communityMembersActive", activeMemberships.size());
        res.put("communityRevenue", communityRevenue);
        res.put("totalRevenue", Money.add(actRevenue, communityRevenue));
        return res;
    }

    /** 多城概览：以「有会员 / 有合伙人 / 有档位」的城市并集为口径 */
    public List<Map<String, Object>> cityList() {
        Set<String> cities = new LinkedHashSet<>();
        members.findAll().forEach(m -> { if (m.getCity() != null && !m.getCity().isBlank()) cities.add(m.getCity()); });
        partners.findAll().forEach(p -> { if (p.getCity() != null && !p.getCity().isBlank()) cities.add(p.getCity()); });
        tiers.findAll().forEach(t -> { if (t.getCity() != null && !t.getCity().isBlank()) cities.add(t.getCity()); });
        List<Map<String, Object>> res = new ArrayList<>();
        for (String c : cities) {
            Map<String, Object> o = overview(c);
            o.remove("partners");
            o.remove("tiers");
            res.add(o);
        }
        res.sort((a, b) -> Integer.compare((int) b.get("memberCount"), (int) a.get("memberCount")));
        return res;
    }

    private static boolean matchCity(Activity a, String city) {
        if (a.getCity() != null && !a.getCity().isBlank()) return city.equals(a.getCity());
        String p = a.getPlace();
        if (p == null) return false;
        // 「杭州市」「杭州」互相包含即算命中
        String bare = city.replace("市", "");
        return p.contains(bare);
    }

    private static int defaultSort(String level) {
        return switch (level) {
            case "免费群" -> 1;
            case "轻会员" -> 2;
            case "VIP" -> 3;
            default -> 9;
        };
    }

    private static String nz(String s) { return s == null ? "" : s; }
    private static String str(Object o) { return o == null ? null : String.valueOf(o); }
    private static Double dbl(Object o) {
        if (o == null) return null;
        try { return Double.parseDouble(String.valueOf(o)); } catch (Exception e) { return null; }
    }
    private static Integer intv(Object o) {
        if (o == null) return null;
        try { return (int) Double.parseDouble(String.valueOf(o)); } catch (Exception e) { return null; }
    }
    private static double round(double v) { return Math.round(v * 100.0) / 100.0; }
}
