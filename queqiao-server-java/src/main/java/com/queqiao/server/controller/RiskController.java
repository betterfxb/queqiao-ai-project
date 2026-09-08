package com.queqiao.server.controller;

import com.queqiao.server.entity.RiskEvent;
import com.queqiao.server.security.RequestContext;
import com.queqiao.server.service.RiskService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 风控预警：/api/risk
 *  - POST /scan        全量规则扫描，生成风险事件（主管/管理员）
 *  - GET /events       风险事件列表（按 handled/type/memberId 过滤，主管/管理员）
 *  - POST /events/{id}/handle  处置风险事件（主管/管理员）
 *  - GET /summary      风险概览（总数/未处理/按类型分布）
 */
@RestController
@RequestMapping("/api/risk")
public class RiskController {

    private final RiskService risk;

    public RiskController(RiskService risk) {
        this.risk = risk;
    }

    @PostMapping("/scan")
    public Map<String, Object> scan() {
        RequestContext.requireManager();
        return risk.scan();
    }

    @GetMapping("/events")
    public List<RiskEvent> events(@RequestParam(required = false) Boolean handled,
                                  @RequestParam(required = false) String type,
                                  @RequestParam(required = false) String memberId) {
        RequestContext.requireManager();
        return risk.list(handled, type, memberId);
    }

    @PostMapping("/events/{id}/handle")
    public RiskEvent handle(@PathVariable String id, @RequestBody(required = false) Map<String, String> body) {
        RequestContext.requireManager();
        String note = body == null ? null : body.get("note");
        return risk.handle(id, me(), note);
    }

    @GetMapping("/summary")
    public Map<String, Object> summary() {
        RequestContext.requireManager();
        return risk.summary();
    }

    private static String me() {
        var c = RequestContext.get();
        return c == null ? "系统" : c.userName();
    }
}
