package com.queqiao.server.controller;

import com.queqiao.server.entity.WorkOrder;
import com.queqiao.server.security.RequestContext;
import com.queqiao.server.service.RiskService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 反诈工单：/api/workorders
 *  - GET           工单列表（按 status/riskType 过滤，主管/管理员）
 *  - POST /{id}/handle  处置工单（主管/管理员）
 *  - GET /summary  工单概览（总数/未结案/按风险类型分布）
 */
@RestController
@RequestMapping("/api/workorders")
public class WorkOrderController {

    private final RiskService risk;

    public WorkOrderController(RiskService risk) {
        this.risk = risk;
    }

    @GetMapping
    public List<WorkOrder> list(@RequestParam(required = false) String status,
                                @RequestParam(required = false) String riskType) {
        RequestContext.requireManager();
        return risk.listWorkOrders(status, riskType);
    }

    @PostMapping("/{id}/handle")
    public WorkOrder handle(@PathVariable String id, @RequestBody(required = false) Map<String, String> body) {
        RequestContext.requireManager();
        String note = body == null ? null : body.get("note");
        String status = body == null ? null : body.get("status");
        return risk.handleWorkOrder(id, status == null ? "已结案" : status, note);
    }

    @GetMapping("/summary")
    public Map<String, Object> summary() {
        RequestContext.requireManager();
        return risk.workOrderSummary();
    }
}
