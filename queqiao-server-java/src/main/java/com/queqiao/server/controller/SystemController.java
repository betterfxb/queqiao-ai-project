package com.queqiao.server.controller;

import com.queqiao.server.ai.AiClient;
import com.queqiao.server.common.BizException;
import com.queqiao.server.config.PayConfig;
import com.queqiao.server.config.VerifyConfig;
import com.queqiao.server.entity.SettingItem;
import com.queqiao.server.pay.PayGateway;
import com.queqiao.server.repo.SettingItemRepository;
import com.queqiao.server.security.RequestContext;
import com.queqiao.server.service.EscrowChannel;
import com.queqiao.server.service.LogService;
import com.queqiao.server.service.SettingService;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/** 健康检查 / 系统设置 / 操作日志 / AI 代理 */
@RestController
@RequestMapping("/api")
public class SystemController {

    private final SettingItemRepository settings;
    private final LogService logs;
    private final AiClient ai;
    private final SettingService settingService;
    private final PayConfig payConfig;
    private final VerifyConfig verifyConfig;
    private final List<PayGateway> gateways;
    private final List<EscrowChannel> escrows;

    public SystemController(SettingItemRepository settings, LogService logs, AiClient ai, SettingService settingService,
                            PayConfig payConfig, VerifyConfig verifyConfig, List<PayGateway> gateways,
                            List<EscrowChannel> escrows) {
        this.settings = settings;
        this.logs = logs;
        this.ai = ai;
        this.settingService = settingService;
        this.payConfig = payConfig;
        this.verifyConfig = verifyConfig;
        this.gateways = gateways;
        this.escrows = escrows;
    }

    @GetMapping("/health")
    public Map<String, Object> health() {
        return Map.of("status", "UP", "service", "queqiao-server",
                "time", LocalDateTime.now().toString(), "ai", ai.health() ? "UP" : "DOWN");
    }

    // —— 系统设置 ——

    /** 全部设置，按分类分组 */
    @GetMapping("/settings")
    public Map<String, List<SettingItem>> allSettings() {
        Map<String, List<SettingItem>> grouped = new LinkedHashMap<>();
        for (SettingItem s : settings.findAll()) {
            grouped.computeIfAbsent(s.getCategory() == null ? "其他" : s.getCategory(),
                    k -> new ArrayList<>()).add(s);
        }
        return grouped;
    }

    /** 批量保存设置（管理员） */
    @PutMapping("/settings")
    public Map<String, Object> saveSettings(@RequestBody Map<String, String> body) {
        RequestContext.requireAdmin();
        int n = 0;
        for (var e : body.entrySet()) {
            SettingItem s = settings.findById(e.getKey()).orElse(null);
            if (s == null) {
                s = new SettingItem();
                s.setId(e.getKey());
                s.setCategory("其他");
                s.setLabel(e.getKey());
                s.setType("text");
            }
            s.setValue(e.getValue());
            s.setUpdatedAt(LocalDateTime.now());
            settings.save(s);
            n++;
        }
        settingService.evictAll();
        logs.record("修改系统设置", String.join(",", body.keySet()), n + " 项");
        return Map.of("ok", true, "count", n);
    }

    /** 单项设置（管理员） */
    @PutMapping("/settings/{key}")
    public SettingItem saveOne(@PathVariable String key, @RequestBody Map<String, String> body) {
        RequestContext.requireAdmin();
        SettingItem s = settings.findById(key).orElseThrow(() -> BizException.notFound("设置项"));
        s.setValue(body.get("value"));
        s.setUpdatedAt(LocalDateTime.now());
        logs.record("修改设置 " + s.getLabel(), key, body.get("value"));
        settingService.evictAll();
        return settings.save(s);
    }

    // —— 操作日志 ——

    @GetMapping("/logs")
    public List<?> operationLogs(@RequestParam(required = false) String target) {
        return (target == null || target.isBlank()) ? logs.recent() : logs.byTarget(target);
    }

    // —— AI 代理（前端 /ai/* 也可直连 8090，此处提供带鉴权的服务端代理通道）——

    @PostMapping("/ai/{action}")
    public Map<String, Object> aiProxy(@PathVariable String action, @RequestBody Map<String, Object> body) {
        Map<String, Object> r = ai.forward("/" + action, body);
        if (r == null) throw new BizException(503, "AI 服务暂不可用，请稍后再试");
        return r;
    }

    /**
     * 第三方渠道接入状态总览（管理员）。
     * 一眼看清支付/核验/存管/AI 当前是「演示态」还是「已接真实渠道」，便于上线前核对。
     */
    @GetMapping("/system/channels")
    public Map<String, Object> channels() {
        RequestContext.requireManager();
        Map<String, Object> pay = new LinkedHashMap<>();
        pay.put("mode", payConfig.getMode());
        pay.put("channels", gateways.stream().map(g -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("channel", g.channel());
            m.put("available", g.available());
            return m;
        }).collect(Collectors.toList()));

        Map<String, Object> verify = new LinkedHashMap<>();
        verify.put("mode", verifyConfig.getMode());
        verify.put("configured", verifyConfig.isConfigured());

        Map<String, Object> res = new LinkedHashMap<>();
        res.put("pay", pay);
        res.put("verify", verify);
        res.put("escrowChannels", escrows.stream().map(EscrowChannel::name).collect(Collectors.toList()));
        res.put("ai", ai.health() ? "UP" : "DOWN");
        res.put("note", "offline/sandbox 为演示态；接真实渠道请把 QQ_PAY_MODE/QQ_VERIFY_MODE 改为 real 并填密钥后重启容器");
        return res;
    }
}
