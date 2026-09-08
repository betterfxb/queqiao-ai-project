package com.queqiao.server.controller;

import com.queqiao.server.common.BizException;
import com.queqiao.server.common.Ids;
import com.queqiao.server.common.Money;
import com.queqiao.server.common.Csv;
import com.queqiao.server.common.OrderStatus;
import com.queqiao.server.entity.FinanceOrder;
import com.queqiao.server.repo.FinanceOrderRepository;
import com.queqiao.server.service.FinanceService;
import com.queqiao.server.service.LogService;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 财务模块。
 * 前端契约：GET /api/finance/records 返回 FinanceRecord[]{time,member,type,amount,status}。
 * 收款 / 退款等写操作委托 {@link FinanceService}（事务 + 状态机守卫 + 幂等）。
 */
@RestController
@RequestMapping("/api/finance")
public class FinanceController {

    private static final DateTimeFormatter TS = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final FinanceOrderRepository orderRepo;
    private final FinanceService financeService;
    private final LogService logService;

    public FinanceController(FinanceOrderRepository orderRepo, FinanceService financeService, LogService logService) {
        this.orderRepo = orderRepo;
        this.financeService = financeService;
        this.logService = logService;
    }

    /** 交易流水（前端表格直接消费） */
    @GetMapping("/records")
    public List<Map<String, Object>> records(@RequestParam(required = false) String type,
                                             @RequestParam(required = false) String status,
                                             @RequestParam(required = false) String keyword,
                                             @RequestParam(required = false, defaultValue = "200") int limit) {
        return orderRepo.findByOrderByCreatedAtDesc().stream()
                .filter(o -> !has(type) || type.equals(o.getType()))
                .filter(o -> !has(status) || status.equals(o.getStatus()))
                .filter(o -> !has(keyword)
                        || (o.getMember() != null && o.getMember().contains(keyword))
                        || (o.getOrderNo() != null && o.getOrderNo().contains(keyword)))
                .limit(Math.max(1, limit))
                .map(FinanceController::toVo)
                .collect(Collectors.toList());
    }

    /** 完整订单列表（含 id / 渠道 / 红娘），用于订单管理 */
    @GetMapping("/orders")
    public List<FinanceOrder> orders(@RequestParam(required = false) String status) {
        List<FinanceOrder> all = orderRepo.findByOrderByCreatedAtDesc();
        if (!has(status)) return all;
        return all.stream().filter(o -> status.equals(o.getStatus())).collect(Collectors.toList());
    }

    /** 财务概览：总营收 / 本月 / 待处理 / 退款 / 分类占比 / 近 6 月趋势 */
    @GetMapping("/stats")
    public Map<String, Object> stats() {
        List<FinanceOrder> all = orderRepo.findAll();
        LocalDate today = LocalDate.now();

        double total = sum(all.stream().filter(o -> !"退款".equals(o.getType())).toList());
        double refund = Math.abs(sum(all.stream().filter(o -> "退款".equals(o.getType())).toList()));
        List<FinanceOrder> month = all.stream()
                .filter(o -> o.getCreatedAt() != null
                        && o.getCreatedAt().getYear() == today.getYear()
                        && o.getCreatedAt().getMonthValue() == today.getMonthValue())
                .toList();
        double monthRevenue = sum(month.stream().filter(o -> !"退款".equals(o.getType())).toList());
        long pending = all.stream().filter(o -> "处理中".equals(o.getStatus())).count();

        // 分类占比
        Map<String, Double> byType = new LinkedHashMap<>();
        for (FinanceOrder o : all) {
            if ("退款".equals(o.getType())) continue;
            byType.merge(o.getType() == null ? "其他" : o.getType(), amount(o), Double::sum);
        }
        List<Map<String, Object>> categories = byType.entrySet().stream()
                .sorted((a, b) -> Double.compare(b.getValue(), a.getValue()))
                .map(e -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("name", e.getKey());
                    m.put("value", round(e.getValue()));
                    m.put("percent", total <= 0 ? 0 : round(e.getValue() * 100 / total));
                    return m;
                }).collect(Collectors.toList());

        // 按 SKU 维度营收占比
        Map<String, Double> byProduct = new LinkedHashMap<>();
        for (FinanceOrder o : all) {
            if ("退款".equals(o.getType())) continue;
            String key = o.getProductName() != null && !o.getProductName().isBlank()
                    ? o.getProductName() : (o.getProductId() != null ? "产品-" + o.getProductId() : "未关联产品");
            byProduct.merge(key, amount(o), Double::sum);
        }
        List<Map<String, Object>> sku = byProduct.entrySet().stream()
                .sorted((a, b) -> Double.compare(b.getValue(), a.getValue()))
                .map(e -> {
                    Map<String, Object> m = new LinkedHashMap<>();
                    m.put("name", e.getKey());
                    m.put("value", round(e.getValue()));
                    m.put("percent", total <= 0 ? 0 : round(e.getValue() * 100 / total));
                    return m;
                }).collect(Collectors.toList());

        // 近 6 个月趋势
        List<Map<String, Object>> trend = new ArrayList<>();
        for (int i = 5; i >= 0; i--) {
            LocalDate d = today.minusMonths(i);
            double v = all.stream()
                    .filter(o -> o.getCreatedAt() != null && !"退款".equals(o.getType())
                            && o.getCreatedAt().getYear() == d.getYear()
                            && o.getCreatedAt().getMonthValue() == d.getMonthValue())
                    .mapToDouble(FinanceController::amount).sum();
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("month", d.getMonthValue() + "月");
            m.put("value", round(v));
            trend.add(m);
        }

        Map<String, Object> res = new LinkedHashMap<>();
        res.put("total", round(total));
        res.put("monthRevenue", round(monthRevenue));
        res.put("monthCount", month.size());
        res.put("refund", round(refund));
        res.put("pending", pending);
        res.put("orderCount", all.size());
        res.put("avgOrder", all.isEmpty() ? 0 : round(total / all.size()));
        res.put("categories", categories);
        res.put("byProduct", sku);
        res.put("trend", trend);
        return res;
    }

    /** 新建订单 */
    @PostMapping("/orders")
    public FinanceOrder create(@RequestBody Map<String, Object> body) {
        String wantStatus = body.containsKey("status") ? str(body.get("status")) : null;
        if ("已退款".equals(wantStatus)) {
            throw new BizException("新建订单不可直接置为「已退款」，请先创建收款订单");
        }
        // 终态「已完成」必须经状态机（确认收款）写入，禁止直写——否则缺渠道流水/分佣/转介绍奖励
        boolean confirmAfter = "已完成".equals(wantStatus);
        FinanceOrder o = new FinanceOrder();
        o.setId(Ids.biz("FO"));
        o.setOrderNo("QQ" + System.currentTimeMillis());
        apply(o, body);
        if (confirmAfter) {
            o.setStatus("处理中");
        }
        if (!has(o.getStatus())) o.setStatus("处理中");
        if (!has(o.getOrderType())) o.setOrderType("新购");
        o.setCreatedAt(LocalDateTime.now());
        o.setUpdatedAt(LocalDateTime.now());
        o.setTime(o.getCreatedAt().format(TS));
        orderRepo.save(o);
        logService.record("新建订单", o.getId(), o.getMember() + " " + o.getType() + " ¥" + o.getAmount());
        // 历史线下收款录入：经状态机确认收款，补齐渠道流水 + 分佣计提 + 转介绍奖励
        if (confirmAfter) {
            return financeService.confirm(o.getId());
        }
        return o;
    }

    @PutMapping("/orders/{id}")
    public FinanceOrder update(@PathVariable String id, @RequestBody Map<String, Object> body) {
        FinanceOrder o = orderRepo.findById(id).orElseThrow(() -> BizException.notFound("订单不存在：" + id));
        // 禁止经编辑接口直写终态：终态流转必须走 /confirm /pay /refund 状态机
        if (body.containsKey("status")) {
            String want = str(body.get("status"));
            OrderStatus cur = OrderStatus.parse(o.getStatus());
            OrderStatus next = OrderStatus.parse(want);
            if (next != null && (next == OrderStatus.已完成 || next == OrderStatus.已退款) && !next.equals(cur)) {
                throw new BizException("禁止经编辑接口将订单置为「" + want + "」，请使用确认收款/退款接口");
            }
        }
        apply(o, body);
        o.setUpdatedAt(LocalDateTime.now());
        orderRepo.save(o);
        logService.record("编辑订单", id, o.getOrderNo());
        return o;
    }

    /** 确认收款：处理中 → 已完成（事务 + 状态机守卫 + 幂等） */
    @PostMapping("/orders/{id}/confirm")
    public FinanceOrder confirm(@PathVariable String id) {
        return financeService.confirm(id);
    }

    /** 退款：已完成 → 已退款，生成负数退款单（事务 + 状态机守卫 + 幂等） */
    @PostMapping("/orders/{id}/refund")
    public FinanceOrder refund(@PathVariable String id, @RequestBody(required = false) Map<String, Object> body) {
        Double amt = body != null && body.get("amount") != null ? num(body.get("amount")).doubleValue() : null;
        String reason = body == null ? null : str(body.get("reason"));
        return financeService.refund(id, amt, reason);
    }

    @DeleteMapping("/orders/{id}")
    public Map<String, Object> delete(@PathVariable String id) {
        FinanceOrder o = orderRepo.findById(id).orElseThrow(() -> BizException.notFound("订单不存在：" + id));
        orderRepo.deleteById(id);
        logService.record("删除订单", id, o.getOrderNo());
        return Map.of("ok", true);
    }

    /** 导出 CSV（带 BOM，Excel 直接可读） */
    @GetMapping(value = "/export", produces = "text/csv;charset=UTF-8")
    public void export(HttpServletResponse resp) throws IOException {
        resp.setContentType(MediaType.parseMediaType("text/csv;charset=UTF-8").toString());
        resp.setHeader("Content-Disposition", "attachment; filename=finance.csv");
        StringBuilder sb = new StringBuilder("\uFEFF");
        sb.append("订单号,会员,类型,金额,状态,红娘,渠道,时间\n");
        for (FinanceOrder o : orderRepo.findByOrderByCreatedAtDesc()) {
            sb.append(csv(o.getOrderNo())).append(',').append(csv(o.getMember())).append(',')
              .append(csv(o.getType())).append(',').append(amount(o)).append(',')
              .append(csv(o.getStatus())).append(',').append(csv(o.getAgent())).append(',')
              .append(csv(o.getChannel())).append(',').append(csv(o.getTime())).append('\n');
        }
        try (OutputStream out = resp.getOutputStream()) {
            out.write(sb.toString().getBytes(StandardCharsets.UTF_8));
        }
    }

    private void apply(FinanceOrder o, Map<String, Object> b) {
        if (b.containsKey("memberId")) o.setMemberId(str(b.get("memberId")));
        if (b.containsKey("member")) o.setMember(str(b.get("member")));
        if (b.containsKey("type")) o.setType(str(b.get("type")));
        if (b.containsKey("amount")) o.setAmount(Money.of(b.get("amount")));
        if (b.containsKey("status")) o.setStatus(str(b.get("status")));
        if (b.containsKey("agent")) o.setAgent(str(b.get("agent")));
        if (b.containsKey("channel")) o.setChannel(str(b.get("channel")));
        if (b.containsKey("remark")) o.setRemark(str(b.get("remark")));
        if (b.containsKey("productId")) o.setProductId(str(b.get("productId")));
        if (b.containsKey("productName")) o.setProductName(str(b.get("productName")));
        if (b.containsKey("orderType")) o.setOrderType(str(b.get("orderType")));
        if (b.containsKey("expireDate")) {
            try { o.setExpireDate(LocalDate.parse(str(b.get("expireDate")))); } catch (Exception ignored) {}
        }
    }

    static Map<String, Object> toVo(FinanceOrder o) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", o.getId());
        m.put("orderNo", o.getOrderNo());
        m.put("time", o.getTime() != null ? o.getTime()
                : (o.getCreatedAt() == null ? "" : o.getCreatedAt().format(TS)));
        m.put("member", o.getMember());
        m.put("type", o.getType());
        m.put("amount", amount(o));
        m.put("status", o.getStatus());
        m.put("agent", o.getAgent());
        m.put("channel", o.getChannel());
        return m;
    }

    private static double sum(List<FinanceOrder> list) {
        return list.stream().mapToDouble(FinanceController::amount).sum();
    }
    private static double amount(FinanceOrder o) { return Money.toDouble(o.getAmount()); }
    private static double round(double v) { return Math.round(v * 100) / 100.0; }
    private static boolean has(String s) { return s != null && !s.isBlank(); }
    private static String str(Object o) { return o == null ? null : String.valueOf(o); }
    private static Number num(Object o) { return o instanceof Number n ? n : Double.parseDouble(String.valueOf(o)); }
    /** CSV 单元格安全输出：复用通用 Csv（P0-13 防公式注入 = + - @ 前缀） */
    private static String csv(String s) {
        return Csv.cell(s);
    }
}
