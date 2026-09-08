package com.queqiao.server.controller;

import com.queqiao.server.common.BizException;
import com.queqiao.server.common.Csv;
import com.queqiao.server.common.Ids;
import com.queqiao.server.common.Money;
import com.queqiao.server.entity.*;
import com.queqiao.server.repo.*;
import com.queqiao.server.service.LogService;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 报表中心。
 * 前端契约：GET /api/reports/records 返回 ReportRecord[]。
 * 导出走 POST /api/reports/generate（登记记录）+ GET /api/reports/{id}/download（下载 CSV）。
 */
@RestController
@RequestMapping("/api/reports")
public class ReportController {

    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final ReportRecordRepository reportRepo;
    private final MemberRepository memberRepo;
    private final AgentRepository agentRepo;
    private final PairRepository pairRepo;
    private final ActivityRepository activityRepo;
    private final FinanceOrderRepository orderRepo;
    private final LogService logService;

    public ReportController(ReportRecordRepository reportRepo, MemberRepository memberRepo,
                            AgentRepository agentRepo, PairRepository pairRepo,
                            ActivityRepository activityRepo, FinanceOrderRepository orderRepo,
                            LogService logService) {
        this.reportRepo = reportRepo;
        this.memberRepo = memberRepo;
        this.agentRepo = agentRepo;
        this.pairRepo = pairRepo;
        this.activityRepo = activityRepo;
        this.orderRepo = orderRepo;
        this.logService = logService;
    }

    /** 导出记录列表 */
    @GetMapping("/records")
    public List<Map<String, Object>> records(@RequestParam(required = false) String category) {
        return reportRepo.findByOrderByCreatedAtDesc().stream()
                .filter(r -> category == null || category.isBlank() || category.equals(r.getCategory()))
                .map(ReportController::toVo)
                .collect(Collectors.toList());
    }

    /** 报表看板汇总数据（会员增长 / 牵线漏斗 / 红娘业绩 / 活动转化） */
    @GetMapping("/summary")
    public Map<String, Object> summary() {
        List<Member> members = memberRepo.findAll();
        List<Pair> pairs = pairRepo.findAll();
        List<Agent> agents = agentRepo.findAll();
        List<Activity> activities = activityRepo.findAll();
        List<FinanceOrder> orders = orderRepo.findAll();
        LocalDate today = LocalDate.now();

        // 近 6 月会员增长
        List<Map<String, Object>> growth = new ArrayList<>();
        for (int i = 5; i >= 0; i--) {
            LocalDate d = today.minusMonths(i);
            long c = members.stream().filter(m -> m.getCreatedAt() != null
                    && m.getCreatedAt().getYear() == d.getYear()
                    && m.getCreatedAt().getMonthValue() == d.getMonthValue()).count();
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("month", d.getMonthValue() + "月");
            m.put("value", c);
            growth.add(m);
        }

        // 牵线漏斗
        long total = pairs.size();
        long feedback = pairs.stream().filter(p -> !"牵线中".equals(p.getStatus())).count();
        long met = pairs.stream().filter(p -> "已约见".equals(p.getStatus()) || "双方同意".equals(p.getStatus())
                || "已成功".equals(p.getStatus())).count();
        long success = pairs.stream().filter(p -> "已成功".equals(p.getStatus())).count();
        List<Map<String, Object>> funnel = List.of(
                stage("发起牵线", total), stage("双方反馈", feedback),
                stage("安排见面", met), stage("牵手成功", success));

        // 红娘业绩 TOP
        List<Map<String, Object>> agentRank = agents.stream()
                .sorted((a, b) -> Double.compare(nz(b.getMonthRevenue()), nz(a.getMonthRevenue())))
                .limit(8)
                .map(a -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("name", a.getName());
                    m.put("revenue", nz(a.getMonthRevenue()));
                    m.put("match", a.getMonthMatch() == null ? 0 : a.getMonthMatch());
                    m.put("successRate", a.getSuccessRate() == null ? 0 : a.getSuccessRate());
                    return m;
                }).collect(Collectors.toList());

        // 活动转化
        List<Map<String, Object>> actConv = activities.stream()
                .filter(a -> a.getSigned() != null && a.getSigned() > 0)
                .sorted((a, b) -> Integer.compare(b.getSigned(), a.getSigned()))
                .limit(6)
                .map(a -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("title", a.getTitle());
                    m.put("signed", a.getSigned());
                    m.put("capacity", a.getCapacity() == null ? 0 : a.getCapacity());
                    m.put("pairs", a.getExpectedPairs() == null ? 0 : a.getExpectedPairs());
                    m.put("revenue", a.getRevenue() == null ? 0 : a.getRevenue());
                    return m;
                }).collect(Collectors.toList());

        Map<String, Object> res = new LinkedHashMap<>();
        res.put("memberTotal", members.size());
        res.put("pairTotal", total);
        res.put("successRate", total == 0 ? 0 : Math.round(success * 1000.0 / total) / 10.0);
        BigDecimal orderRevenue = Money.sum(orders.stream()
                .filter(o -> !"退款".equals(o.getType())).toList(), FinanceOrder::getAmount);
        res.put("revenue", Money.toDouble(orderRevenue));
        res.put("growth", growth);
        res.put("funnel", funnel);
        res.put("agentRank", agentRank);
        res.put("activityConversion", actConv);
        return res;
    }

    /** 生成报表：登记一条记录，真实内容在下载时按 category 现算 */
    @PostMapping("/generate")
    public Map<String, Object> generate(@RequestBody Map<String, Object> body) {
        String category = str(body.getOrDefault("category", "会员"));
        String format = str(body.getOrDefault("format", "CSV"));
        String range = str(body.getOrDefault("range", "全部"));

        ReportRecord r = new ReportRecord();
        r.setId(Ids.biz("RP"));
        r.setName(category + "报表_" + LocalDate.now());
        r.setCategory(category);
        r.setFormat(format);
        r.setRange(range);
        r.setExportedAt(LocalDateTime.now().format(TS));
        r.setOperator(logService.currentUserName());
        r.setStatus("已完成");
        r.setSize(estimateSize(category));
        r.setCreatedAt(LocalDateTime.now());
        reportRepo.save(r);
        logService.record("生成报表", r.getId(), r.getName());

        Map<String, Object> m = toVo(r);
        m.put("downloadUrl", "/api/reports/" + r.getId() + "/download");
        return m;
    }

    /** 下载报表内容（CSV） */
    @GetMapping(value = "/{id}/download", produces = "text/csv;charset=UTF-8")
    public void download(@PathVariable String id, HttpServletResponse resp) throws IOException {
        ReportRecord r = reportRepo.findById(id).orElseThrow(() -> BizException.notFound("报表不存在：" + id));
        resp.setContentType("text/csv;charset=UTF-8");
        resp.setHeader("Content-Disposition", "attachment; filename=" + id + ".csv");
        StringBuilder sb = new StringBuilder("\uFEFF");
        switch (r.getCategory() == null ? "" : r.getCategory()) {
            case "财务" -> {
                sb.append("订单号,会员,类型,金额,状态,时间\n");
                orderRepo.findByOrderByCreatedAtDesc().forEach(o -> sb
                        .append(csv(o.getOrderNo())).append(',').append(csv(o.getMember())).append(',')
                        .append(csv(o.getType())).append(',').append(o.getAmount()).append(',')
                        .append(csv(o.getStatus())).append(',').append(csv(o.getTime())).append('\n'));
            }
            case "红娘" -> {
                sb.append("编号,姓名,等级,状态,负责会员,本月牵线,成功率,本月营收\n");
                agentRepo.findAll().forEach(a -> sb
                        .append(csv(a.getId())).append(',').append(csv(a.getName())).append(',')
                        .append(csv(a.getLevel())).append(',').append(csv(a.getStatus())).append(',')
                        .append(a.getMemberCount()).append(',').append(a.getMonthMatch()).append(',')
                        .append(a.getSuccessRate()).append(',').append(a.getMonthRevenue()).append('\n'));
            }
            case "活动" -> {
                sb.append("编号,标题,日期,地点,状态,报名,容量,营收\n");
                activityRepo.findByOrderByDateDesc().forEach(a -> sb
                        .append(csv(a.getId())).append(',').append(csv(a.getTitle())).append(',')
                        .append(a.getDate()).append(',').append(csv(a.getPlace())).append(',')
                        .append(csv(a.getStatus())).append(',').append(a.getSigned()).append(',')
                        .append(a.getCapacity()).append(',').append(a.getRevenue()).append('\n'));
            }
            case "牵线" -> {
                sb.append("编号,男方,女方,匹配度,状态,红娘,创建时间\n");
                pairRepo.findAll().forEach(p -> sb
                        .append(csv(p.getId())).append(',').append(csv(p.getMaleName())).append(',')
                        .append(csv(p.getFemaleName())).append(',').append(p.getScore()).append(',')
                        .append(csv(p.getStatus())).append(',').append(csv(p.getAgent())).append(',')
                        .append(p.getCreatedAt()).append('\n'));
            }
            default -> {
                sb.append("编号,姓名,性别,年龄,城市,学历,职业,等级,状态,红娘\n");
                memberRepo.findAll().forEach(m -> sb
                        .append(csv(m.getId())).append(',').append(csv(m.getName())).append(',')
                        .append(csv(m.getGender())).append(',').append(m.getAge()).append(',')
                        .append(csv(m.getCity())).append(',').append(csv(m.getEducation())).append(',')
                        .append(csv(m.getJob())).append(',').append(csv(m.getLevel())).append(',')
                        .append(csv(m.getStatus())).append(',').append(csv(m.getAgent())).append('\n'));
            }
        }
        try (OutputStream out = resp.getOutputStream()) {
            out.write(sb.toString().getBytes(StandardCharsets.UTF_8));
        }
    }

    @DeleteMapping("/{id}")
    public Map<String, Object> delete(@PathVariable String id) {
        ReportRecord r = reportRepo.findById(id).orElseThrow(() -> BizException.notFound("报表不存在：" + id));
        reportRepo.deleteById(id);
        logService.record("删除报表", id, r.getName());
        return Map.of("ok", true);
    }

    private String estimateSize(String category) {
        long rows = switch (category == null ? "" : category) {
            case "财务" -> orderRepo.count();
            case "红娘" -> agentRepo.count();
            case "活动" -> activityRepo.count();
            case "牵线" -> pairRepo.count();
            default -> memberRepo.count();
        };
        double kb = Math.max(1, rows * 0.12);
        return kb >= 1024 ? String.format("%.1f MB", kb / 1024) : String.format("%.0f KB", kb);
    }

    private static Map<String, Object> stage(String name, long value) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("name", name);
        m.put("value", value);
        return m;
    }

    static Map<String, Object> toVo(ReportRecord r) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", r.getId());
        m.put("name", r.getName());
        m.put("category", r.getCategory());
        m.put("format", r.getFormat());
        m.put("range", r.getRange());
        m.put("exportedAt", r.getExportedAt());
        m.put("operator", r.getOperator());
        m.put("status", r.getStatus());
        m.put("size", r.getSize());
        m.put("downloadUrl", "/api/reports/" + r.getId() + "/download");
        return m;
    }

    private static double nz(Double d) { return d == null ? 0 : d; }
    private static String str(Object o) { return o == null ? null : String.valueOf(o); }
    /** CSV 单元格安全输出：复用通用 Csv（P0-13 防公式注入 = + - @ 前缀） */
    private static String csv(String s) {
        return Csv.cell(s);
    }
}
