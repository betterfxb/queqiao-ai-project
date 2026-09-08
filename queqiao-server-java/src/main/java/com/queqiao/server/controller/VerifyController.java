package com.queqiao.server.controller;

import com.queqiao.server.common.BizException;
import com.queqiao.server.entity.VerifyTask;
import com.queqiao.server.security.RequestContext;
import com.queqiao.server.service.RealVerifyGateway;
import com.queqiao.server.service.VerifyGateway;
import com.queqiao.server.service.VerifyService;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 第三方核验：/api/verify
 *  - POST          发起核验任务（主管/管理员）
 *  - POST /{id}/result  录入核验结果；核验通过回写认证标签（主管/管理员）
 *  - GET           核验任务列表（按会员/类型/状态过滤，主管/管理员）
 *  - GET /{id}    核验任务详情
 *  - GET /gateway 核验网关诊断（模式/密钥配置/可用性）
 */
@RestController
@RequestMapping("/api/verify")
public class VerifyController {

    private final VerifyService svc;
    private final VerifyGateway gateway;

    public VerifyController(VerifyService svc, VerifyGateway gateway) {
        this.svc = svc;
        this.gateway = gateway;
    }

    @PostMapping
    public VerifyTask create(@RequestBody Map<String, String> body) {
        RequestContext.requireManager();
        return svc.create(body.get("memberId"), body.get("type"), body.get("channel"), body.get("rawProof"));
    }

    /**
     * 自动实人认证（2-A）：会员本人或主管/管理员代发起。
     * 会员身份只能给自己做（currentMemberId，防 IDOR）；主管/管理员可指定 memberId 代发起。
     */
    @PostMapping("/real-person")
    public VerifyTask realPerson(@RequestBody Map<String, String> body) {
        String memberId;
        if (RequestContext.isMember()) {
            memberId = RequestContext.currentMemberId();
        } else {
            RequestContext.requireManager();
            memberId = body.get("memberId");
        }
        if (memberId == null || memberId.isBlank()) throw new BizException("缺少会员");
        return svc.realPerson(memberId, body.get("authToken"), body.get("liveImage"));
    }

    @PostMapping("/{id}/result")
    public VerifyTask result(@PathVariable String id, @RequestBody Map<String, String> body) {
        RequestContext.requireManager();
        return svc.recordResult(id, body.get("status"), body.get("result"), me());
    }

    @GetMapping
    public List<VerifyTask> list(@RequestParam(required = false) String memberId,
                                 @RequestParam(required = false) String type,
                                 @RequestParam(required = false) String status) {
        RequestContext.requireManager();
        return svc.list(memberId, type, status);
    }

    @GetMapping("/{id}")
    public VerifyTask detail(@PathVariable String id) {
        RequestContext.requireManager();
        return svc.detail(id);
    }

    /** 核验网关诊断：当前模式 / 是否已配置密钥 / 是否可用（主管/管理员） */
    @GetMapping("/gateway")
    public Map<String, Object> gateway() {
        RequestContext.requireManager();
        Map<String, Object> m = new HashMap<>();
        if (gateway instanceof RealVerifyGateway r) {
            m.put("mode", r.getMode());
            m.put("configured", r.isConfigured());
            m.put("available", r.available());
        } else {
            m.put("mode", "manual");
            m.put("configured", false);
            m.put("available", true);
        }
        return m;
    }

    private static String me() {
        var c = RequestContext.get();
        return c == null ? "系统" : c.userName();
    }
}
