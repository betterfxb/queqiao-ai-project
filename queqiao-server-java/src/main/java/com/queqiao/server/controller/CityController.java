package com.queqiao.server.controller;

import com.queqiao.server.entity.CityPartner;
import com.queqiao.server.entity.CommunityTier;
import com.queqiao.server.entity.FinanceOrder;
import com.queqiao.server.security.RequestContext;
import com.queqiao.server.service.CityService;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 同城运营模板：/api/city（#111 5.3.3）
 *  - GET/POST/PUT/DELETE /partners   城市合伙人（读 主管+ / 写 管理员）
 *  - GET/POST/PUT/DELETE /tiers      分层社群档位（读 登录 / 写 管理员）
 *  - GET /overview?city=             单城运营总览（主管+）
 *  - GET /list                       多城概览（主管+）
 */
@RestController
@RequestMapping("/api/city")
public class CityController {

    private final CityService svc;

    public CityController(CityService svc) {
        this.svc = svc;
    }

    // —— 城市合伙人 ——

    @GetMapping("/partners")
    public List<CityPartner> partners(@RequestParam(required = false) String city,
                                      @RequestParam(required = false) String status) {
        RequestContext.requireManager();
        return svc.listPartners(city, status);
    }

    @PostMapping("/partners")
    public CityPartner createPartner(@RequestBody Map<String, Object> body) {
        RequestContext.requireAdmin();
        return svc.savePartner(null, body);
    }

    @PutMapping("/partners/{id}")
    public CityPartner updatePartner(@PathVariable String id, @RequestBody Map<String, Object> body) {
        RequestContext.requireAdmin();
        return svc.savePartner(id, body);
    }

    @DeleteMapping("/partners/{id}")
    public Map<String, Object> deletePartner(@PathVariable String id) {
        RequestContext.requireAdmin();
        svc.deletePartner(id);
        return Map.of("ok", true);
    }

    // —— 社群档位 ——

    @GetMapping("/tiers")
    public List<CommunityTier> tiers(@RequestParam(required = false) String city,
                                     @RequestParam(required = false) String status) {
        RequestContext.requireAuth();
        return svc.listTiers(city, status);
    }

    @PostMapping("/tiers")
    public CommunityTier createTier(@RequestBody Map<String, Object> body) {
        RequestContext.requireAdmin();
        return svc.saveTier(null, body);
    }

    @PutMapping("/tiers/{id}")
    public CommunityTier updateTier(@PathVariable String id, @RequestBody Map<String, Object> body) {
        RequestContext.requireAdmin();
        return svc.saveTier(id, body);
    }

    @DeleteMapping("/tiers/{id}")
    public Map<String, Object> deleteTier(@PathVariable String id) {
        RequestContext.requireAdmin();
        svc.deleteTier(id);
        return Map.of("ok", true);
    }

    // —— 社群档位支付打通（#157 D）——
    // 订阅下单（登录即可，会员自购）→ 返回待支付订单 → 支付成功回调开通会员资格

    /** 订阅下单：生成「社群订阅」待支付订单（在售 + 容量校验） */
    @PostMapping("/tiers/{id}/subscribe")
    public Map<String, Object> subscribe(@PathVariable String id, @RequestBody Map<String, Object> body) {
        RequestContext.requireAuth();
        FinanceOrder order = svc.subscribe(id, str(body.get("memberId")), str(body.get("memberName")));
        Map<String, Object> res = new LinkedHashMap<>();
        res.put("order", order);
        return res;
    }

    /** 档位真实会员列表（按会员维度） */
    @GetMapping("/tiers/{id}/memberships")
    public List<com.queqiao.server.entity.CommunityMembership> tierMemberships(@PathVariable String id) {
        RequestContext.requireAuth();
        return svc.memberships(id);
    }

    /** 全量社群会员（按会员维度） */
    @GetMapping("/memberships")
    public List<com.queqiao.server.entity.CommunityMembership> allMemberships() {
        RequestContext.requireAuth();
        return svc.memberships(null);
    }

    // —— 聚合 ——

    @GetMapping("/overview")
    public Map<String, Object> overview(@RequestParam(required = false) String city) {
        RequestContext.requireManager();
        return svc.overview(city);
    }

    @GetMapping("/list")
    public List<Map<String, Object>> cityList() {
        RequestContext.requireManager();
        return svc.cityList();
    }

    private static String str(Object o) { return o == null ? null : String.valueOf(o); }
}
