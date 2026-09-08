package com.queqiao.server.controller;

import com.queqiao.server.config.PresenceService;
import org.springframework.web.bind.annotation.GetMapping;
import java.util.Map;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 在线状态快照（REST）。前端接入 WebSocket 前先拉一次，避免上线瞬间的短暂盲区。
 */
@RestController
@RequestMapping("/api")
public class PresenceController {

    private final PresenceService presence;

    public PresenceController(PresenceService presence) {
        this.presence = presence;
    }

    @GetMapping("/presence")
    public Map<String, Object> snapshot() {
        return presence.snapshot();
    }
}
