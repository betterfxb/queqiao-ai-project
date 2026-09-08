package com.queqiao.server.controller;

import com.queqiao.server.entity.SuccessStory;
import com.queqiao.server.security.RequestContext;
import com.queqiao.server.service.StoryService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 成功案例营销：/api/stories（#111 5.3.2）
 *  - GET /public        对外公开列表（**免登录**，仅「已发布 且 公开」）
 *  - GET                内部列表（主管+）
 *  - POST /draft        由「已成功」牵线一键生成脱敏草稿（主管+）
 *  - POST               新建（主管+）
 *  - PUT /{id}          编辑（主管+）
 *  - POST /{id}/publish 发布（管理员）
 *  - POST /{id}/offline 下架（管理员）
 *  - DELETE /{id}       删除（管理员）
 */
@RestController
@RequestMapping("/api/stories")
public class StoryController {

    private final StoryService svc;

    public StoryController(StoryService svc) {
        this.svc = svc;
    }

    /** 对外公开列表：免登录。库内仅存别名，无 PII。 */
    @GetMapping("/public")
    public List<SuccessStory> publicList() {
        return svc.publicList();
    }

    @GetMapping
    public List<SuccessStory> list(@RequestParam(required = false) String status) {
        RequestContext.requireManager();
        return svc.list(status);
    }

    @GetMapping("/summary")
    public Map<String, Object> summary() {
        RequestContext.requireManager();
        return svc.summary();
    }

    @PostMapping("/draft")
    public SuccessStory draft(@RequestBody Map<String, Object> body) {
        RequestContext.requireManager();
        return svc.draftFromPair(String.valueOf(body.get("pairId")), RequestContext.agentName());
    }

    @PostMapping
    public SuccessStory create(@RequestBody Map<String, Object> body) {
        RequestContext.requireManager();
        return svc.save(null, body, RequestContext.agentName());
    }

    @GetMapping("/{id}")
    public SuccessStory detail(@PathVariable String id) {
        RequestContext.requireManager();
        return svc.detail(id);
    }

    @PutMapping("/{id}")
    public SuccessStory update(@PathVariable String id, @RequestBody Map<String, Object> body) {
        RequestContext.requireManager();
        return svc.save(id, body, RequestContext.agentName());
    }

    @PostMapping("/{id}/publish")
    public SuccessStory publish(@PathVariable String id, @RequestBody(required = false) Map<String, Object> body) {
        RequestContext.requireAdmin();
        Boolean pub = body == null || body.get("publicVisible") == null
                ? Boolean.TRUE : Boolean.TRUE.equals(body.get("publicVisible"));
        return svc.publish(id, pub);
    }

    @PostMapping("/{id}/offline")
    public SuccessStory offline(@PathVariable String id) {
        RequestContext.requireAdmin();
        return svc.offline(id);
    }

    @DeleteMapping("/{id}")
    public Map<String, Object> delete(@PathVariable String id) {
        RequestContext.requireAdmin();
        svc.delete(id);
        return Map.of("ok", true);
    }
}
