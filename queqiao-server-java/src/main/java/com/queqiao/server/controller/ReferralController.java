package com.queqiao.server.controller;

import com.queqiao.server.entity.Referral;
import com.queqiao.server.security.RequestContext;
import com.queqiao.server.service.ReferralService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 转介绍激励：/api/referral（#111 5.3.1）
 *  - POST              登记推荐关系（登录即可）
 *  - GET /mine         我推荐的（登录即可）
 *  - GET               全量列表（主管+）
 *  - GET /{id}         详情（主管+）
 *  - POST /{id}/convert 手动标记转化（主管+）
 *  - POST /{id}/reward  发放奖励（管理员）
 *  - POST /{id}/invalid 作废（主管+）
 *  - GET /summary      汇总（主管+）
 */
@RestController
@RequestMapping("/api/referral")
public class ReferralController {

    private final ReferralService svc;

    public ReferralController(ReferralService svc) {
        this.svc = svc;
    }

    @PostMapping
    public Referral register(@RequestBody Map<String, Object> body) {
        RequestContext.requireAuth();
        return svc.register(
                str(body.get("referrerId")),
                str(body.get("referrerType")),
                str(body.get("refereeName")),
                str(body.get("refereePhone")),
                str(body.get("refereeLeadId")),
                str(body.get("remark")));
    }

    /** 会员本人专属推荐码（不存在则生成）。C 端裂变分享入口。 */
    @GetMapping("/code")
    public Map<String, String> myCode() {
        RequestContext.requireMember();
        String code = svc.ensureCode(RequestContext.currentMemberId());
        return Map.of("code", code);
    }

    /** C 端裂变：好友凭专属码登记为被推荐人（登录即可，推荐人由码解析）。 */
    @PostMapping("/register-by-code")
    public Referral registerByCode(@RequestBody Map<String, Object> body) {
        RequestContext.requireAuth();
        return svc.registerByCode(str(body.get("code")),
                str(body.get("refereeName")), str(body.get("refereePhone")));
    }

    @GetMapping("/mine")
    public List<Referral> mine(@RequestParam(required = false) String referrerId) {
        RequestContext.requireAuth();
        String id = referrerId;
        if (RequestContext.isMember()) id = RequestContext.currentMemberId(); // 会员只能看自己
        return svc.mine(id);
    }

    @GetMapping
    public List<Referral> list(@RequestParam(required = false) String status,
                               @RequestParam(required = false) String referrerId) {
        RequestContext.requireManager();
        return svc.list(status, referrerId);
    }

    @GetMapping("/summary")
    public Map<String, Object> summary() {
        RequestContext.requireManager();
        return svc.summary();
    }

    @GetMapping("/{id}")
    public Referral detail(@PathVariable String id) {
        RequestContext.requireManager();
        return svc.detail(id);
    }

    @PostMapping("/{id}/convert")
    public Referral convert(@PathVariable String id, @RequestBody(required = false) Map<String, Object> body) {
        RequestContext.requireManager();
        Map<String, Object> b = body == null ? Map.of() : body;
        Double amt = null;
        if (b.get("orderAmount") != null) {
            try { amt = Double.parseDouble(String.valueOf(b.get("orderAmount"))); } catch (Exception ignored) { }
        }
        return svc.convert(id, str(b.get("memberId")), amt, RequestContext.agentName());
    }

    @PostMapping("/{id}/reward")
    public Referral reward(@PathVariable String id, @RequestBody(required = false) Map<String, Object> body) {
        RequestContext.requireAdmin();
        Map<String, Object> b = body == null ? Map.of() : body;
        Double amt = null;
        if (b.get("amount") != null) {
            try { amt = Double.parseDouble(String.valueOf(b.get("amount"))); } catch (Exception ignored) { }
        }
        return svc.reward(id, str(b.get("rewardType")), amt, RequestContext.agentName());
    }

    @PostMapping("/{id}/invalid")
    public Referral invalidate(@PathVariable String id) {
        RequestContext.requireManager();
        return svc.invalidate(id, RequestContext.agentName());
    }

    private static String str(Object o) {
        return o == null ? null : String.valueOf(o);
    }
}
