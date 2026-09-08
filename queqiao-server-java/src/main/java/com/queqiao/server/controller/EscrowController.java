package com.queqiao.server.controller;

import com.queqiao.server.entity.EscrowOrder;
import com.queqiao.server.security.RequestContext;
import com.queqiao.server.service.EscrowService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 预付资金存管（3-D）。
 * 高客单价套餐的预付款进入存管，履约后放款、争议时退款，全程状态留痕（退费透明）。
 * 财务操作需主管及以上；列表/统计主管可读。
 */
@RestController
@RequestMapping("/api/escrow")
public class EscrowController {

    private final EscrowService escrows;

    public EscrowController(EscrowService escrows) {
        this.escrows = escrows;
    }

    /** 发起托管（主管即可操作，对应会员支付高客单价套餐） */
    @PostMapping("/hold")
    public EscrowOrder hold(@RequestBody Map<String, Object> body) {
        RequestContext.requireManager();
        String memberId = asStr(body.get("memberId"));
        if (memberId.isBlank()) throw new IllegalArgumentException("memberId 不能为空");
        String packageId = asStr(body.get("packageId"));
        String packageName = asStr(body.get("packageName"), "高客单价套餐");
        Long amount = asLong(body.get("amount"));
        if (amount == null || amount <= 0) throw new IllegalArgumentException("amount（分）必须大于 0");
        String currency = asStr(body.get("currency"), "rmb");
        String channel = asStr(body.get("channel"), "digital-rmb");
        return escrows.hold(memberId, packageId, packageName, amount, currency, channel);
    }

    /** 放款：服务履约完成，释放资金给服务商 */
    @PostMapping("/{id}/release")
    public EscrowOrder release(@PathVariable String id) {
        RequestContext.requireManager();
        return escrows.release(id);
    }

    /** 退款：退费透明（记录金额与原因） */
    @PostMapping("/{id}/refund")
    public EscrowOrder refund(@PathVariable String id, @RequestBody(required = false) Map<String, Object> body) {
        RequestContext.requireManager();
        Long amount = body == null ? null : asLong(body.get("amount"));
        String reason = body == null ? null : asStr(body.get("reason"));
        return escrows.refund(id, amount, reason);
    }

    /** 存管订单列表（可按会员过滤） */
    @GetMapping
    public List<EscrowOrder> list(@RequestParam(required = false) String memberId) {
        RequestContext.requireManager();
        return escrows.list(memberId);
    }

    /** 存管透明统计 */
    @GetMapping("/stats")
    public Map<String, Object> stats() {
        RequestContext.requireManager();
        return escrows.stats();
    }

    private static String asStr(Object v) {
        return v == null ? "" : String.valueOf(v).trim();
    }

    private static String asStr(Object v, String def) {
        String s = asStr(v);
        return s.isBlank() ? def : s;
    }

    private static Long asLong(Object v) {
        if (v == null) return null;
        if (v instanceof Number n) return n.longValue();
        try {
            return Long.parseLong(String.valueOf(v).trim());
        } catch (Exception e) {
            return null;
        }
    }
}
