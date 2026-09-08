package com.queqiao.server.controller;

import com.queqiao.server.entity.MerchantOrder;
import com.queqiao.server.entity.PartnerMerchant;
import com.queqiao.server.security.RequestContext;
import com.queqiao.server.service.PartnerService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 产业链延伸导流：/api/partners（#111 5.3.4）
 *  - GET  /merchants          商户列表（主管+）
 *  - POST/PUT/DELETE /merchants  商户维护（管理员）
 *  - GET  /orders             导流单列表（主管+）
 *  - POST /orders             创建导流单（登录即可，红娘可为自己会员导流）
 *  - POST /orders/{id}/deal   标记成交并计佣（主管+）
 *  - POST /orders/{id}/settle 结算（管理员）
 *  - POST /orders/{id}/cancel 取消（主管+）
 *  - GET  /recommend?pairId=  成功脱单推荐商户（登录即可）
 *  - GET  /summary            汇总（主管+）
 */
@RestController
@RequestMapping("/api/partners")
public class PartnerController {

    private final PartnerService svc;

    public PartnerController(PartnerService svc) {
        this.svc = svc;
    }

    // —— 商户 ——

    @GetMapping("/merchants")
    public List<PartnerMerchant> merchants(@RequestParam(required = false) String category,
                                           @RequestParam(required = false) String status,
                                           @RequestParam(required = false) String city) {
        RequestContext.requireManager();
        return svc.listMerchants(category, status, city);
    }

    @PostMapping("/merchants")
    public PartnerMerchant createMerchant(@RequestBody Map<String, Object> body) {
        RequestContext.requireAdmin();
        return svc.saveMerchant(null, body);
    }

    @PutMapping("/merchants/{id}")
    public PartnerMerchant updateMerchant(@PathVariable String id, @RequestBody Map<String, Object> body) {
        RequestContext.requireAdmin();
        return svc.saveMerchant(id, body);
    }

    @DeleteMapping("/merchants/{id}")
    public Map<String, Object> deleteMerchant(@PathVariable String id) {
        RequestContext.requireAdmin();
        svc.deleteMerchant(id);
        return Map.of("ok", true);
    }

    // —— 导流单 ——

    @GetMapping("/orders")
    public List<MerchantOrder> orders(@RequestParam(required = false) String status,
                                      @RequestParam(required = false) String memberId,
                                      @RequestParam(required = false) String merchantId) {
        RequestContext.requireManager();
        return svc.listOrders(status, memberId, merchantId);
    }

    @PostMapping("/orders")
    public MerchantOrder createOrder(@RequestBody Map<String, Object> body) {
        RequestContext.requireAuth();
        Double amount = null;
        if (body.get("amount") != null) {
            try { amount = Double.parseDouble(String.valueOf(body.get("amount"))); } catch (Exception ignored) { }
        }
        String agent = body.get("agent") != null ? String.valueOf(body.get("agent")) : RequestContext.agentName();
        return svc.createOrder(str(body.get("merchantId")), str(body.get("memberId")),
                str(body.get("pairId")), amount, agent, str(body.get("note")));
    }

    @GetMapping("/orders/{id}")
    public MerchantOrder detail(@PathVariable String id) {
        RequestContext.requireManager();
        return svc.detail(id);
    }

    @PostMapping("/orders/{id}/deal")
    public MerchantOrder deal(@PathVariable String id, @RequestBody(required = false) Map<String, Object> body) {
        RequestContext.requireManager();
        Double amount = null;
        if (body != null && body.get("amount") != null) {
            try { amount = Double.parseDouble(String.valueOf(body.get("amount"))); } catch (Exception ignored) { }
        }
        return svc.deal(id, amount);
    }

    @PostMapping("/orders/{id}/settle")
    public MerchantOrder settle(@PathVariable String id) {
        RequestContext.requireAdmin();
        return svc.settle(id);
    }

    @PostMapping("/orders/{id}/cancel")
    public MerchantOrder cancel(@PathVariable String id, @RequestBody(required = false) Map<String, Object> body) {
        RequestContext.requireManager();
        return svc.cancel(id, body == null ? null : str(body.get("reason")));
    }

    // —— 推荐 / 汇总 ——

    @GetMapping("/recommend")
    public Map<String, Object> recommend(@RequestParam(required = false) String pairId) {
        RequestContext.requireAuth();
        return svc.recommend(pairId);
    }

    @GetMapping("/summary")
    public Map<String, Object> summary() {
        RequestContext.requireManager();
        return svc.summary();
    }

    private static String str(Object o) {
        return o == null ? null : String.valueOf(o);
    }
}
