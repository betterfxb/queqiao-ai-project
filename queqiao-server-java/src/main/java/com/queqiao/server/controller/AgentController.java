package com.queqiao.server.controller;

import com.queqiao.server.ai.AiClient;
import com.queqiao.server.common.BizException;
import com.queqiao.server.common.Ids;
import com.queqiao.server.common.PageResult;
import com.queqiao.server.common.Specs;
import com.queqiao.server.dto.AgentUpsertRequest;
import com.queqiao.server.entity.Agent;
import com.queqiao.server.repo.AgentRepository;
import com.queqiao.server.repo.MemberRepository;
import com.queqiao.server.repo.PairRepository;
import com.queqiao.server.security.RequestContext;
import com.queqiao.server.service.LogService;
import jakarta.validation.Valid;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.*;

/** 红娘团队管理 */
@RestController
@RequestMapping("/api/agents")
public class AgentController {

    private final AgentRepository agents;
    private final MemberRepository members;
    private final PairRepository pairs;
    private final AiClient ai;
    private final LogService logs;

    public AgentController(AgentRepository agents, MemberRepository members, PairRepository pairs,
                           AiClient ai, LogService logs) {
        this.agents = agents;
        this.members = members;
        this.pairs = pairs;
        this.ai = ai;
        this.logs = logs;
    }

    @GetMapping
    public PageResult<Agent> list(@RequestParam(required = false) String keyword,
                                  @RequestParam(required = false) String level,
                                  @RequestParam(required = false) String status,
                                  @RequestParam(defaultValue = "1") int page,
                                  @RequestParam(defaultValue = "50") int size) {
        var spec = Specs.<Agent>of()
                .keyword(keyword, "name", "id", "phone", "region")
                .eq("level", level).eq("status", status).build();
        var list = agents.findAll(spec);
        list.sort(Comparator.comparingDouble((Agent a) -> a.getMonthRevenue() == null ? 0 : a.getMonthRevenue()).reversed());
        return PageResult.of(list, page, size);
    }

    /** 绩效排行（含名次与达成率） */
    @GetMapping("/ranking")
    public List<Map<String, Object>> ranking() {
        List<Agent> list = new ArrayList<>(agents.findByStatus("在职"));
        list.sort(Comparator.comparingDouble((Agent a) -> a.getMonthRevenue() == null ? 0 : a.getMonthRevenue()).reversed());
        double top = list.isEmpty() || list.get(0).getMonthRevenue() == null ? 1 : list.get(0).getMonthRevenue();
        List<Map<String, Object>> out = new ArrayList<>();
        for (int i = 0; i < list.size(); i++) {
            Agent a = list.get(i);
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("rank", i + 1);
            m.put("agent", a);
            m.put("percent", Math.round((a.getMonthRevenue() == null ? 0 : a.getMonthRevenue()) / top * 100));
            out.add(m);
        }
        return out;
    }

    /** 团队汇总 */
    @GetMapping("/stats")
    public Map<String, Object> stats() {
        List<Agent> all = agents.findAll();
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("total", all.size());
        m.put("active", all.stream().filter(a -> "在职".equals(a.getStatus())).count());
        m.put("totalMembers", all.stream().mapToInt(a -> a.getMemberCount() == null ? 0 : a.getMemberCount()).sum());
        m.put("monthMatch", all.stream().mapToInt(a -> a.getMonthMatch() == null ? 0 : a.getMonthMatch()).sum());
        m.put("monthRevenue", all.stream().mapToDouble(a -> a.getMonthRevenue() == null ? 0 : a.getMonthRevenue()).sum());
        m.put("avgSuccessRate", all.isEmpty() ? 0 : Math.round(all.stream()
                .mapToInt(a -> a.getSuccessRate() == null ? 0 : a.getSuccessRate()).average().orElse(0)));
        m.put("avgRating", all.isEmpty() ? 0 : Math.round(all.stream()
                .mapToDouble(a -> a.getRating() == null ? 0 : a.getRating()).average().orElse(0) * 10) / 10.0);
        return m;
    }

    /** 红娘详情（含名下会员与牵线） */
    @GetMapping("/{id}")
    public Map<String, Object> detail(@PathVariable String id) {
        Agent a = agents.findById(id).orElseThrow(() -> BizException.notFound("红娘"));
        Map<String, Object> res = new LinkedHashMap<>();
        res.put("agent", a);
        res.put("members", members.findByAgent(a.getName()));
        res.put("pairs", pairs.findByAgent(a.getName()));
        return res;
    }

    /** 新增红娘（仅主管及以上；业绩字段由业务流程回写，客户端不可提交） */
    @PostMapping
    @Transactional
    public Agent create(@Valid @RequestBody AgentUpsertRequest body) {
        RequestContext.requireManager();
        if (body.getName() == null || body.getName().isBlank()) throw new BizException("请填写姓名");
        Agent a = new Agent();
        copyNonNull(body, a);
        a.setId(Ids.biz("A"));
        if (a.getStatus() == null) a.setStatus("在职");
        if (a.getLevel() == null) a.setLevel("普通红娘");
        if (a.getCapacity() == null) a.setCapacity(80);
        a.setMemberCount(0);
        a.setMonthMatch(0);
        a.setSuccessRate(0);
        a.setMonthRevenue(0.0);
        a.setRating(5.0);
        a.setCreatedAt(LocalDateTime.now());
        logs.record("新增红娘", a.getId(), a.getName());
        return agents.save(a);
    }

    /** 编辑红娘（仅主管及以上） */
    @PutMapping("/{id}")
    @Transactional
    public Agent update(@PathVariable String id, @Valid @RequestBody AgentUpsertRequest body) {
        RequestContext.requireManager();
        Agent a = agents.findById(id).orElseThrow(() -> BizException.notFound("红娘"));
        copyNonNull(body, a);
        logs.record("编辑红娘", id, a.getName());
        return agents.save(a);
    }

    @DeleteMapping("/{id}")
    @Transactional
    public Map<String, Object> delete(@PathVariable String id) {
        RequestContext.requireManager();
        Agent a = agents.findById(id).orElseThrow(() -> BizException.notFound("红娘"));
        a.setStatus("离职");
        agents.save(a);
        logs.record("红娘离职", id, a.getName());
        return Map.of("ok", true, "id", id);
    }

    private static void copyNonNull(AgentUpsertRequest src, Agent dst) {
        if (src.getName() != null) dst.setName(src.getName());
        if (src.getLevel() != null) dst.setLevel(src.getLevel());
        if (src.getStatus() != null) dst.setStatus(src.getStatus());
        if (src.getPhone() != null) dst.setPhone(src.getPhone());
        if (src.getRegion() != null) dst.setRegion(src.getRegion());
        if (src.getCapacity() != null) dst.setCapacity(src.getCapacity());
        if (src.getJoinDate() != null) dst.setJoinDate(src.getJoinDate());
        if (src.getAvatarBg() != null) dst.setAvatarBg(src.getAvatarBg());
    }

    /** AI 分配建议：为待分配会员推荐最合适的红娘 */
    @PostMapping("/assign-suggest")
    public Map<String, Object> assignSuggest(@RequestBody Map<String, Object> body) {
        List<Agent> pool = agents.findByStatus("在职");

        // 补齐 AI 需要的红娘池（前端只需传 member / memberLevel / memberCity）
        Map<String, Object> payload = new LinkedHashMap<>(body);
        payload.put("agents", pool.stream().map(a -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", a.getId());
            m.put("name", a.getName());
            m.put("level", a.getLevel());
            m.put("status", a.getStatus());
            m.put("memberCount", a.getMemberCount() == null ? 0 : a.getMemberCount());
            m.put("capacity", a.getCapacity() == null ? 80 : a.getCapacity());
            m.put("successRate", a.getSuccessRate() == null ? 0 : a.getSuccessRate());
            m.put("rating", a.getRating() == null ? 0 : a.getRating());
            m.put("region", a.getRegion());
            return m;
        }).toList());

        Map<String, Object> r = ai.assignSuggest(payload);
        if (r != null && r.get("agent") != null) {
            r.put("source", "ai");
            return r;
        }

        // 本地兜底：按负载率 + 成功率排序
        List<Map<String, Object>> out = new ArrayList<>();
        for (Agent a : pool) {
            int cap = a.getCapacity() == null ? 80 : a.getCapacity();
            int load = a.getMemberCount() == null ? 0 : a.getMemberCount();
            double loadRate = cap == 0 ? 1 : (double) load / cap;
            double score = (1 - Math.min(1, loadRate)) * 50
                    + (a.getSuccessRate() == null ? 0 : a.getSuccessRate()) * 0.35
                    + (a.getRating() == null ? 0 : a.getRating()) * 3;
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("agent", a);
            row.put("score", (int) Math.round(score));
            row.put("reason", String.format("负载 %d/%d，成功率 %d%%，评分 %.1f",
                    load, cap, a.getSuccessRate() == null ? 0 : a.getSuccessRate(),
                    a.getRating() == null ? 0 : a.getRating()));
            out.add(row);
        }
        out.sort((x, y) -> Integer.compare((int) y.get("score"), (int) x.get("score")));
        return Map.of("source", "local-fallback", "list", out.subList(0, Math.min(3, out.size())));
    }
}
