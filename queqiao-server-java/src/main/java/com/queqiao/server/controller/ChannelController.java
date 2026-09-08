package com.queqiao.server.controller;

import com.queqiao.server.entity.MarketChannel;
import com.queqiao.server.security.RequestContext;
import com.queqiao.server.service.ChannelService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * 渠道归因与获客分析：/api/channels（#111 5.3.5）
 *  - GET            渠道列表（主管+）
 *  - POST/PUT/DELETE 渠道维护（管理员）
 *  - GET /roi       ROI 聚合，支持 from/to 时间窗（主管+）
 */
@RestController
@RequestMapping("/api/channels")
public class ChannelController {

    private final ChannelService svc;

    public ChannelController(ChannelService svc) {
        this.svc = svc;
    }

    @GetMapping
    public List<MarketChannel> list(@RequestParam(required = false) String status) {
        RequestContext.requireManager();
        return svc.list(status);
    }

    @GetMapping("/roi")
    public Map<String, Object> roi(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        RequestContext.requireManager();
        return svc.roi(from, to);
    }

    @GetMapping("/{id}")
    public MarketChannel detail(@PathVariable String id) {
        RequestContext.requireManager();
        return svc.detail(id);
    }

    @PostMapping
    public MarketChannel create(@RequestBody Map<String, Object> body) {
        RequestContext.requireAdmin();
        return svc.save(null, body);
    }

    @PutMapping("/{id}")
    public MarketChannel update(@PathVariable String id, @RequestBody Map<String, Object> body) {
        RequestContext.requireAdmin();
        return svc.save(id, body);
    }

    @DeleteMapping("/{id}")
    public Map<String, Object> delete(@PathVariable String id) {
        RequestContext.requireAdmin();
        svc.delete(id);
        return Map.of("ok", true);
    }
}
