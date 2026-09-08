package com.queqiao.server.controller;

import com.queqiao.server.entity.ConsentRecord;
import com.queqiao.server.entity.DocTemplate;
import com.queqiao.server.security.RequestContext;
import com.queqiao.server.service.DocService;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 合规三件套：/api/docs
 *  - GET              模板列表（已登录）
 *  - GET /{type}      指定类型模板（已登录）
 *  - PUT /{type}      更新模板并升版（管理员）
 *  - POST /consent    记录合规同意（注册/付费强制勾选，已登录）
 *  - GET /consent/{memberId}  某会员同意记录（主管/管理员）
 */
@RestController
@RequestMapping("/api/docs")
public class DocController {

    private final DocService svc;

    public DocController(DocService svc) {
        this.svc = svc;
    }

    @GetMapping
    public List<DocTemplate> list() {
        RequestContext.requireAuth();
        return svc.list();
    }

    @GetMapping("/{type}")
    public DocTemplate get(@PathVariable String type) {
        RequestContext.requireAuth();
        return svc.getByType(type);
    }

    @PutMapping("/{type}")
    public DocTemplate save(@PathVariable String type, @RequestBody Map<String, String> body) {
        RequestContext.requireAdmin();
        return svc.save(type, body.get("title"), body.get("content"), me());
    }

    @PostMapping("/consent")
    public Map<String, Object> consent(@RequestBody Map<String, String> body) {
        RequestContext.requireAuth();
        ConsentRecord c = svc.consent(body.get("memberId"), body.get("memberName"),
                body.get("docType"), body.get("version"), body.get("channel"));
        Map<String, Object> res = new LinkedHashMap<>();
        res.put("ok", true);
        res.put("record", c);
        res.put("acceptedAll", svc.hasAllConsents(body.get("memberId")));
        return res;
    }

    @GetMapping("/consent/{memberId}")
    public List<ConsentRecord> consents(@PathVariable String memberId) {
        RequestContext.requireManager();
        return svc.consents(memberId);
    }

    private static String me() {
        var c = RequestContext.get();
        return c == null ? "系统" : c.userName();
    }
}
