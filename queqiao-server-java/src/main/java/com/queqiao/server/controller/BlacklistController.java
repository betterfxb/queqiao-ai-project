package com.queqiao.server.controller;

import com.queqiao.server.common.BizException;
import com.queqiao.server.common.Ids;
import com.queqiao.server.entity.Blacklist;
import com.queqiao.server.entity.Member;
import com.queqiao.server.repo.BlacklistRepository;
import com.queqiao.server.repo.MemberRepository;
import com.queqiao.server.security.RequestContext;
import com.queqiao.server.service.LogService;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 黑名单：/api/blacklist
 *  - POST        拉黑会员（管理员）
 *  - GET         生效中黑名单列表（主管/管理员）
 *  - GET /all    全部黑名单（含已解封，主管/管理员）
 *  - POST /{id}/unban  解封（管理员）
 *  - GET /check  查询某会员是否拉黑（牵线前拦截依据）
 */
@RestController
@RequestMapping("/api/blacklist")
public class BlacklistController {

    private final BlacklistRepository blacklists;
    private final MemberRepository members;
    private final LogService logs;

    public BlacklistController(BlacklistRepository blacklists, MemberRepository members, LogService logs) {
        this.blacklists = blacklists;
        this.members = members;
        this.logs = logs;
    }

    @PostMapping
    public Blacklist create(@RequestBody Map<String, String> body) {
        RequestContext.requireAdmin();
        Member m = members.findById(body.get("memberId")).orElseThrow(() -> BizException.notFound("会员"));
        Blacklist bl = new Blacklist();
        bl.setId(Ids.biz("BL"));
        bl.setMemberId(m.getId());
        bl.setMemberName(m.getName());
        bl.setReason(body.get("reason"));
        String until = body.get("until");
        bl.setUntil(until == null || until.isBlank() ? null : LocalDate.parse(until.trim()));
        bl.setStatus("生效");
        bl.setOperator(me());
        bl.setCreatedAt(LocalDateTime.now());
        blacklists.save(bl);
        logs.record("拉黑会员", m.getId(), m.getName());
        return bl;
    }

    @GetMapping
    public List<Blacklist> list() {
        RequestContext.requireManager();
        return blacklists.findByStatusOrderByCreatedAtDesc("生效");
    }

    @GetMapping("/all")
    public List<Blacklist> all() {
        RequestContext.requireManager();
        return blacklists.findAll();
    }

    @PostMapping("/{id}/unban")
    public Blacklist unban(@PathVariable String id) {
        RequestContext.requireAdmin();
        Blacklist bl = blacklists.findById(id).orElseThrow(() -> BizException.notFound("黑名单记录"));
        bl.setStatus("已解封");
        blacklists.save(bl);
        logs.record("解除拉黑", id, bl.getMemberName());
        return bl;
    }

    @GetMapping("/check")
    public Map<String, Object> check(@RequestParam String memberId) {
        RequestContext.requireManager();
        boolean on = blacklists.findByMemberIdAndStatus(memberId, "生效").isPresent();
        Map<String, Object> res = new LinkedHashMap<>();
        res.put("memberId", memberId);
        res.put("blacklisted", on);
        return res;
    }

    private static String me() {
        var c = RequestContext.get();
        return c == null ? "系统" : c.userName();
    }
}
