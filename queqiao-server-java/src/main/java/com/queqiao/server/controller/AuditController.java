package com.queqiao.server.controller;

import com.queqiao.server.ai.AiClient;
import com.queqiao.server.common.BizException;
import com.queqiao.server.common.PageResult;
import com.queqiao.server.common.Specs;
import com.queqiao.server.entity.AuditTask;
import com.queqiao.server.entity.Member;
import com.queqiao.server.repo.AuditTaskRepository;
import com.queqiao.server.repo.MemberRepository;
import com.queqiao.server.service.LogService;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.*;

/** 审核中心 */
@RestController
@RequestMapping("/api/audits")
public class AuditController {

    private final AuditTaskRepository audits;
    private final MemberRepository members;
    private final AiClient ai;
    private final LogService logs;

    public AuditController(AuditTaskRepository audits, MemberRepository members, AiClient ai, LogService logs) {
        this.audits = audits;
        this.members = members;
        this.ai = ai;
        this.logs = logs;
    }

    @GetMapping
    public PageResult<AuditTask> list(@RequestParam(required = false) String keyword,
                                      @RequestParam(required = false) String type,
                                      @RequestParam(required = false) String urgency,
                                      @RequestParam(required = false) String status,
                                      @RequestParam(defaultValue = "1") int page,
                                      @RequestParam(defaultValue = "20") int size) {
        var spec = Specs.<AuditTask>of()
                .keyword(keyword, "applicant", "memberId", "id")
                .eq("type", type).eq("urgency", urgency).eq("status", status).build();
        var list = audits.findAll(spec);
        list.sort(Comparator.comparing(AuditTask::getSubmittedAt,
                Comparator.nullsLast(Comparator.reverseOrder())));
        return PageResult.of(list, page, size);
    }

    /** 顶部统计 */
    @GetMapping("/stats")
    public Map<String, Object> stats() {
        List<AuditTask> all = audits.findAll();
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("pending", all.stream().filter(a -> "待审核".equals(a.getStatus())).count());
        m.put("urgent", all.stream().filter(a -> "待审核".equals(a.getStatus())
                && "紧急".equals(a.getUrgency())).count());
        m.put("approved", all.stream().filter(a -> "已通过".equals(a.getStatus())).count());
        m.put("rejected", all.stream().filter(a -> "已驳回".equals(a.getStatus())).count());
        Map<String, Long> byType = new LinkedHashMap<>();
        for (AuditTask a : all) {
            if (!"待审核".equals(a.getStatus())) continue;
            byType.merge(a.getType() == null ? "其他" : a.getType(), 1L, Long::sum);
        }
        m.put("byType", byType);
        return m;
    }

    @GetMapping("/{id}")
    public Map<String, Object> detail(@PathVariable String id) {
        AuditTask a = audits.findById(id).orElseThrow(() -> BizException.notFound("审核事项"));
        Map<String, Object> res = new LinkedHashMap<>();
        res.put("audit", a);
        res.put("member", a.getMemberId() == null ? null : members.findById(a.getMemberId()).orElse(null));
        return res;
    }

    /** 通过 */
    @PostMapping("/{id}/approve")
    @Transactional
    public AuditTask approve(@PathVariable String id, @RequestBody(required = false) Map<String, String> body) {
        AuditTask a = audits.findById(id).orElseThrow(() -> BizException.notFound("审核事项"));
        if (!"待审核".equals(a.getStatus())) {
            throw new BizException("该审核事项已处理（" + a.getStatus() + "），无法重复通过");
        }
        a.setStatus("已通过");
        a.setHandler(logs.currentUserName());
        a.setHandledAt(LocalDateTime.now());
        if (body != null && body.get("remark") != null) a.setReason(body.get("remark"));
        applyToMember(a, true);
        logs.record("审核通过", id, a.getType() + " · " + a.getApplicant());
        return audits.save(a);
    }

    /** 驳回 */
    @PostMapping("/{id}/reject")
    @Transactional
    public AuditTask reject(@PathVariable String id, @RequestBody Map<String, String> body) {
        AuditTask a = audits.findById(id).orElseThrow(() -> BizException.notFound("审核事项"));
        if (!"待审核".equals(a.getStatus())) {
            throw new BizException("该审核事项已处理（" + a.getStatus() + "），无法重复驳回");
        }
        String reason = body == null ? null : body.get("reason");
        if (reason == null || reason.isBlank()) throw new BizException("请填写驳回原因");
        a.setStatus("已驳回");
        a.setReason(reason);
        a.setHandler(logs.currentUserName());
        a.setHandledAt(LocalDateTime.now());
        applyToMember(a, false);
        logs.record("审核驳回", id, reason);
        return audits.save(a);
    }

    /** 批量通过 */
    @PostMapping("/batch-approve")
    @Transactional
    public Map<String, Object> batchApprove(@RequestBody Map<String, List<String>> body) {
        List<String> ids = body.getOrDefault("ids", List.of());
        int n = 0;
        for (String id : ids) {
            AuditTask a = audits.findById(id).orElse(null);
            if (a == null || !"待审核".equals(a.getStatus())) continue;
            a.setStatus("已通过");
            a.setHandler(logs.currentUserName());
            a.setHandledAt(LocalDateTime.now());
            applyToMember(a, true);
            audits.save(a);
            n++;
        }
        logs.record("批量审核通过", String.join(",", ids), n + " 条");
        return Map.of("ok", true, "count", n);
    }

    /** AI 审核建议 */
    @PostMapping("/{id}/ai-suggest")
    public Map<String, Object> aiSuggest(@PathVariable String id) {
        AuditTask a = audits.findById(id).orElseThrow(() -> BizException.notFound("审核事项"));
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("type", a.getType());
        payload.put("applicant", a.getApplicant());
        payload.put("gender", a.getGender());
        payload.put("age", a.getAge());
        payload.put("urgency", a.getUrgency());
        payload.put("memberId", a.getMemberId());
        payload.put("material", a.getMaterial());
        payload.put("materials", a.getMaterial() == null || a.getMaterial().isBlank()
                ? List.of() : Arrays.asList(a.getMaterial().split("[,，、]")));
        payload.put("changeField", a.getChangeField());
        payload.put("changeFrom", a.getChangeFrom());
        payload.put("changeTo", a.getChangeTo());
        payload.put("payText", a.getPayText());
        payload.put("paid", a.getPaid());
        // 同会员历史审核记录（供 AI 判断是否有不良记录）
        payload.put("history", audits.findAll().stream()
                .filter(x -> a.getMemberId() != null && a.getMemberId().equals(x.getMemberId()))
                .filter(x -> !x.getId().equals(a.getId()))
                .map(x -> x.getType() + " " + x.getStatus() + (x.getReason() == null ? "" : " " + x.getReason()))
                .toList());

        Map<String, Object> r = ai.auditSuggest(payload);
        if (r != null) {
            r.put("source", "ai");
            return r;
        }

        // 本地兜底规则
        List<String> risks = new ArrayList<>();
        String advice = "建议通过";
        if ("资料修改".equals(a.getType()) && "年收入".equals(a.getChangeField())) {
            risks.add("收入信息大幅上调，建议要求补充收入证明");
            advice = "建议补充材料后通过";
        }
        if ("实名认证".equals(a.getType()) && (a.getMaterial() == null || a.getMaterial().isBlank())) {
            risks.add("认证材料缺失");
            advice = "建议驳回并要求重新提交";
        }
        if (Boolean.FALSE.equals(a.getPaid()) && a.getPayText() != null) {
            risks.add("费用未支付");
            advice = "建议待支付完成后再通过";
        }
        if (risks.isEmpty()) risks.add("未发现明显风险点");
        return Map.of("source", "local-fallback", "advice", advice, "risks", risks,
                "confidence", risks.size() == 1 ? 0.82 : 0.61);
    }

    /** 审核结果回写会员状态 */
    private void applyToMember(AuditTask a, boolean approved) {
        if (a.getMemberId() == null) return;
        Member m = members.findById(a.getMemberId()).orElse(null);
        if (m == null) return;
        if (approved) {
            switch (nz(a.getType())) {
                case "实名认证" -> {
                    m.setStatus("已认证");
                    m.setVerified(true);
                    m.setVerifyRealName(true);
                    m.setVerifyRealNameAt(LocalDateTime.now());
                }
                case "资产认证" -> {
                    m.setVerifyAsset(true);
                    m.setVerifyAssetAt(LocalDateTime.now());
                }
                case "资料修改" -> applyChange(m, a);
                case "会员升级" -> {
                    if (a.getPayText() != null && a.getPayText().contains("12,800")) m.setLevel("SVIP");
                    else m.setLevel("VIP");
                }
                default -> {
                }
            }
        } else if ("实名认证".equals(a.getType())) {
            m.setStatus("待审核");
        }
        m.setUpdatedAt(LocalDateTime.now());
        members.save(m);
    }

    private void applyChange(Member m, AuditTask a) {
        String f = nz(a.getChangeField());
        String to = a.getChangeTo();
        if (to == null) return;
        switch (f) {
            case "年收入" -> m.setIncome(to);
            case "职业" -> m.setJob(to);
            case "学历" -> {
                m.setEducation(to);
                m.setVerifyEducation(true);
                m.setVerifyEducationAt(LocalDateTime.now());
            }
            case "居住城市" -> m.setCity(to);
            default -> {
            }
        }
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }
}
