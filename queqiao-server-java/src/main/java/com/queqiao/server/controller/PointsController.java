package com.queqiao.server.controller;

import com.queqiao.server.service.PointsService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/** 会员积分：排行榜与规则说明（全局只读 + 展示用） */
@RestController
@RequestMapping("/api/points")
public class PointsController {

    private final PointsService points;

    public PointsController(PointsService points) {
        this.points = points;
    }

    /** 积分排行榜（按累计积分降序，取前 limit 名） */
    @GetMapping("/leaderboard")
    public List<Map<String, Object>> leaderboard(@RequestParam(defaultValue = "20") int limit) {
        return points.leaderboard(limit);
    }

    /** 积分规则说明（前端积分卡 / 帮助文案数据源） */
    @GetMapping("/rules")
    public List<Map<String, Object>> rules() {
        return points.rules();
    }
}
