package com.queqiao.server.controller;

import com.queqiao.server.common.BizException;
import com.queqiao.server.common.Ids;
import com.queqiao.server.common.Specs;
import com.queqiao.server.entity.Member;
import com.queqiao.server.entity.MemberReport;
import com.queqiao.server.repo.MemberReportRepository;
import com.queqiao.server.repo.MemberRepository;
import com.queqiao.server.security.RequestContext;
import com.queqiao.server.service.LogService;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * 会员举报/投诉：/api/complaints
 *  - POST        提交举报（已登录用户）
 *  - GET         举报列表（主管/管理员，按状态/类型过滤）
 *  - POST /{id}/handle  处理举报（主管/管理员）
 * 命名避开已存在的报表中心 ReportController(/api/reports)。
 */
@RestController
@RequestMapping("/api/complaints")
public class ComplaintController {

    private final MemberReportRepository reports;
    private final MemberRepository members;
    private final LogService logs;

    public ComplaintController(MemberReportRepository reports, MemberRepository members, LogService logs) {
        this.reports = reports;
        this.members = members;
        this.logs = logs;
    }

    @PostMapping
    public MemberReport create(@RequestBody Map<String, String> body) {
        RequestContext.requireAuth();
        String targetId = body.get("targetMemberId");
        Member t = members.findById(targetId).orElseThrow(() -> BizException.notFound("被举报会员"));
        MemberReport r = new MemberReport();
        r.setId(Ids.biz("MR"));
        var c = RequestContext.get();
        r.setReporterId(c == null ? null : c.userId());
        r.setReporterName(c == null ? "匿名" : c.userName());
        r.setTargetMemberId(targetId);
        r.setTargetName(t.getName());
        r.setType(body.get("type"));
        r.setContent(body.get("content"));
        r.setStatus("待处理");
        r.setCreatedAt(LocalDateTime.now());
        reports.save(r);
        logs.record("提交举报", targetId, t.getName() + " / " + body.get("type"));
        return r;
    }

    @GetMapping
    public List<MemberReport> list(@RequestParam(required = false) String status,
                                   @RequestParam(required = false) String type) {
        RequestContext.requireManager();
        var spec = Specs.<MemberReport>of().eq("status", status).eq("type", type).build();
        List<MemberReport> list = reports.findAll(spec);
        list.sort(Comparator.comparing(MemberReport::getCreatedAt,
                Comparator.nullsLast(Comparator.reverseOrder())));
        return list;
    }

    @PostMapping("/{id}/handle")
    public MemberReport handle(@PathVariable String id, @RequestBody Map<String, String> body) {
        RequestContext.requireManager();
        MemberReport r = reports.findById(id).orElseThrow(() -> BizException.notFound("举报"));
        r.setStatus(body.get("status"));
        r.setHandleNote(body.get("note"));
        r.setHandler(me());
        r.setHandledAt(LocalDateTime.now());
        reports.save(r);
        logs.record("处理举报", id, body.get("status"));
        return r;
    }

    private static String me() {
        var c = RequestContext.get();
        return c == null ? "系统" : c.userName();
    }
}
