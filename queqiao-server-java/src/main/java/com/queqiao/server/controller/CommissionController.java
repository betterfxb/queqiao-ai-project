package com.queqiao.server.controller;

import com.queqiao.server.entity.CommissionRecord;
import com.queqiao.server.security.RequestContext;
import com.queqiao.server.service.CommissionService;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * 红娘分佣结算：/api/commission
 *  - mine     红娘本人佣金（AGENT）
 *  - team     团队佣金（MANAGER/ADMIN，可按红娘姓名过滤）
 *  - summary  聚合统计（含分佣占营收比，MANAGER/ADMIN）
 *  - settle   生成结算单：待结算 → 已结算（ADMIN）
 *  - withdraw 提现记录：已结算 → 已提现（ADMIN）
 */
@RestController
@RequestMapping("/api/commission")
public class CommissionController {

    private final CommissionService commissionService;

    public CommissionController(CommissionService commissionService) {
        this.commissionService = commissionService;
    }

    /** 红娘本人佣金 */
    @GetMapping("/mine")
    public List<CommissionRecord> mine() {
        RequestContext.requireAuth();
        return commissionService.mine(RequestContext.agentName());
    }

    /** 团队佣金（主管/管理员，可按红娘姓名过滤） */
    @GetMapping("/team")
    public List<CommissionRecord> team(@RequestParam(required = false) String agentName) {
        RequestContext.requireManager();
        return commissionService.team(agentName);
    }

    /** 聚合统计；团队维度含「分佣占营收比」 */
    @GetMapping("/summary")
    public Map<String, Object> summary(@RequestParam(defaultValue = "team") String scope,
                                       @RequestParam(required = false) String agentName) {
        RequestContext.requireManager();
        String realScope = "mine".equals(scope) ? "mine" : "team";
        String realAgent = "mine".equals(realScope) ? RequestContext.agentName() : agentName;
        return commissionService.summary(realScope, realAgent);
    }

    /** 生成结算单（管理员） */
    @PostMapping("/settle")
    public Map<String, Object> settle(@RequestBody(required = false) Map<String, String> body) {
        RequestContext.requireAdmin();
        body = body == null ? Map.of() : body;
        return commissionService.generateSettlement(
                body.get("agentName"), parseDate(body.get("from")), parseDate(body.get("to")));
    }

    /** 提现记录（管理员） */
    @PostMapping("/withdraw")
    public Map<String, Object> withdraw(@RequestBody(required = false) Map<String, String> body) {
        RequestContext.requireAdmin();
        body = body == null ? Map.of() : body;
        return commissionService.withdraw(
                body.get("agentName"), parseDate(body.get("from")), parseDate(body.get("to")));
    }

    private static LocalDate parseDate(String s) {
        if (s == null || s.isBlank()) return null;
        try {
            return LocalDate.parse(s.trim());
        } catch (Exception e) {
            return null;
        }
    }
}
