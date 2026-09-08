package com.queqiao.server.controller;

import com.queqiao.server.common.BizException;
import com.queqiao.server.common.Ids;
import com.queqiao.server.entity.CalendarEvent;
import com.queqiao.server.repo.CalendarEventRepository;
import com.queqiao.server.security.RequestContext;
import com.queqiao.server.service.LogService;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 日程日历。
 * 前端契约：GET /api/calendar/events?year=&month= 返回事件数组（date 为 yyyy-MM-dd）。
 */
@RestController
@RequestMapping("/api/calendar")
public class CalendarController {

    private final CalendarEventRepository eventRepo;
    private final LogService logService;

    public CalendarController(CalendarEventRepository eventRepo, LogService logService) {
        this.eventRepo = eventRepo;
        this.logService = logService;
    }

    /** 事件列表：不传参返回全部；传 year/month 返回当月；传 from/to 返回区间 */
    @GetMapping("/events")
    public List<Map<String, Object>> events(@RequestParam(required = false) Integer year,
                                            @RequestParam(required = false) Integer month,
                                            @RequestParam(required = false) String from,
                                            @RequestParam(required = false) String to,
                                            @RequestParam(required = false) String type,
                                            @RequestParam(required = false) String agent) {
        List<CalendarEvent> list;
        if (year != null && month != null) {
            YearMonth ym = YearMonth.of(year, month);
            list = eventRepo.findByDateBetween(ym.atDay(1), ym.atEndOfMonth());
        } else if (from != null && to != null) {
            list = eventRepo.findByDateBetween(LocalDate.parse(from), LocalDate.parse(to));
        } else {
            list = eventRepo.findByOrderByDateAsc();
        }
        String me = RequestContext.isAgent() ? RequestContext.agentName() : null;
        return list.stream()
                // 红娘自动收敛为本人日程；管理员可传 agent 参数按人筛选
                .filter(e -> me == null || me.equals(e.getAgent()))
                .filter(e -> type == null || type.isBlank() || type.equals(e.getType()))
                .filter(e -> (me != null) || agent == null || agent.isBlank() || agent.equals(e.getAgent()))
                .sorted(Comparator.comparing(CalendarEvent::getDate, Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(e -> e.getStartTime() == null ? "" : e.getStartTime()))
                .map(CalendarController::toVo)
                .collect(Collectors.toList());
    }

    /** 今日日程（工作台右侧） */
    @GetMapping("/today")
    public List<Map<String, Object>> today() {
        String me = RequestContext.isAgent() ? RequestContext.agentName() : null;
        return eventRepo.findByDate(LocalDate.now()).stream()
                .filter(e -> me == null || me.equals(e.getAgent()))
                .sorted(Comparator.comparing(e -> e.getStartTime() == null ? "" : e.getStartTime()))
                .map(CalendarController::toVo)
                .collect(Collectors.toList());
    }

    /** 月度统计（各类型数量 + 忙碌日） */
    @GetMapping("/stats")
    public Map<String, Object> stats(@RequestParam(required = false) Integer year,
                                     @RequestParam(required = false) Integer month) {
        YearMonth ym = (year != null && month != null) ? YearMonth.of(year, month) : YearMonth.now();
        String me = RequestContext.isAgent() ? RequestContext.agentName() : null;
        List<CalendarEvent> list = eventRepo.findByDateBetween(ym.atDay(1), ym.atEndOfMonth()).stream()
                .filter(e -> me == null || me.equals(e.getAgent()))
                .collect(Collectors.toList());

        Map<String, Long> byType = list.stream().collect(Collectors.groupingBy(
                e -> e.getType() == null ? "其他" : e.getType(), LinkedHashMap::new, Collectors.counting()));
        Map<String, Long> byDay = list.stream().collect(Collectors.groupingBy(
                e -> String.valueOf(e.getDate()), TreeMap::new, Collectors.counting()));

        Map<String, Object> m = new LinkedHashMap<>();
        m.put("month", ym.toString());
        m.put("total", list.size());
        m.put("confirmed", list.stream().filter(e -> "已确认".equals(e.getStatus())).count());
        m.put("pending", list.stream().filter(e -> "待确认".equals(e.getStatus())).count());
        m.put("byType", byType);
        m.put("byDay", byDay);
        return m;
    }

    @GetMapping("/events/{id}")
    public Map<String, Object> detail(@PathVariable String id) {
        CalendarEvent e = eventRepo.findById(id).orElseThrow(() -> BizException.notFound("日程不存在：" + id));
        Map<String, Object> m = toVo(e);
        m.put("remark", e.getRemark());
        m.put("pairId", e.getPairId());
        m.put("createdAt", e.getCreatedAt());
        return m;
    }

    /** 新建日程 */
    @PostMapping("/events")
    public Map<String, Object> create(@RequestBody Map<String, Object> body) {
        CalendarEvent e = new CalendarEvent();
        e.setId(Ids.biz("CE"));
        apply(e, body);
        if (e.getDate() == null) throw new IllegalArgumentException("date 不能为空");
        if (!has(e.getTitle())) throw new IllegalArgumentException("title 不能为空");
        if (!has(e.getStatus())) e.setStatus("待确认");
        if (!has(e.getType())) e.setType("其他");
        if (RequestContext.isAgent()) e.setAgent(RequestContext.agentName()); // 红娘新建日程强制归属本人
        e.setCreatedAt(LocalDateTime.now());
        e.setUpdatedAt(LocalDateTime.now());
        eventRepo.save(e);
        logService.record("新建日程", e.getId(), e.getTitle() + " " + e.getDate());
        return toVo(e);
    }

    @PutMapping("/events/{id}")
    public Map<String, Object> update(@PathVariable String id, @RequestBody Map<String, Object> body) {
        CalendarEvent e = eventRepo.findById(id).orElseThrow(() -> BizException.notFound("日程不存在：" + id));
        assertOwned(e);
        apply(e, body);
        e.setUpdatedAt(LocalDateTime.now());
        eventRepo.save(e);
        logService.record("编辑日程", id, e.getTitle());
        return toVo(e);
    }

    /** 状态流转：待确认 / 已确认 / 已完成 / 已取消 */
    @PostMapping("/events/{id}/status")
    public Map<String, Object> status(@PathVariable String id, @RequestBody Map<String, String> body) {
        CalendarEvent e = eventRepo.findById(id).orElseThrow(() -> BizException.notFound("日程不存在：" + id));
        assertOwned(e);
        String s = body.get("status");
        if (!has(s)) throw new IllegalArgumentException("status 不能为空");
        e.setStatus(s);
        e.setUpdatedAt(LocalDateTime.now());
        eventRepo.save(e);
        logService.record("日程状态变更", id, e.getTitle() + " → " + s);
        return toVo(e);
    }

    @DeleteMapping("/events/{id}")
    public Map<String, Object> delete(@PathVariable String id) {
        CalendarEvent e = eventRepo.findById(id).orElseThrow(() -> BizException.notFound("日程不存在：" + id));
        assertOwned(e);
        eventRepo.deleteById(id);
        logService.record("删除日程", id, e.getTitle());
        return Map.of("ok", true);
    }

    @SuppressWarnings("unchecked")
    private void apply(CalendarEvent e, Map<String, Object> b) {
        if (b.containsKey("title")) e.setTitle(str(b.get("title")));
        if (b.containsKey("type")) e.setType(str(b.get("type")));
        if (b.containsKey("date")) e.setDate(parseDate(str(b.get("date"))));
        if (b.containsKey("startTime")) e.setStartTime(str(b.get("startTime")));
        if (b.containsKey("endTime")) e.setEndTime(str(b.get("endTime")));
        if (b.containsKey("place")) e.setPlace(str(b.get("place")));
        if (b.containsKey("agent")) e.setAgent(str(b.get("agent")));
        if (b.containsKey("status")) e.setStatus(str(b.get("status")));
        if (b.containsKey("remark")) e.setRemark(str(b.get("remark")));
        if (b.containsKey("pairId")) e.setPairId(str(b.get("pairId")));
        if (b.containsKey("memberIds")) e.setMemberIds(join(b.get("memberIds")));
        if (b.containsKey("memberNames")) e.setMemberNames(join(b.get("memberNames")));
    }

    @SuppressWarnings("unchecked")
    private static String join(Object o) {
        if (o == null) return null;
        if (o instanceof List) return String.join(",", ((List<Object>) o).stream().map(String::valueOf).toList());
        return String.valueOf(o);
    }

    static Map<String, Object> toVo(CalendarEvent e) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", e.getId());
        m.put("title", e.getTitle());
        m.put("type", e.getType());
        m.put("date", e.getDate() == null ? null : e.getDate().toString());
        m.put("startTime", e.getStartTime());
        m.put("endTime", e.getEndTime());
        m.put("time", (e.getStartTime() == null ? "" : e.getStartTime())
                + (e.getEndTime() == null ? "" : "-" + e.getEndTime()));
        m.put("place", e.getPlace());
        m.put("agent", e.getAgent());
        m.put("status", e.getStatus());
        m.put("members", has(e.getMemberNames()) ? Arrays.asList(e.getMemberNames().split(",")) : List.of());
        m.put("memberIds", has(e.getMemberIds()) ? Arrays.asList(e.getMemberIds().split(",")) : List.of());
        return m;
    }

    private static LocalDate parseDate(String s) {
        if (!has(s)) return null;
        String v = s.trim().replace('.', '-').replace('/', '-');
        try {
            return LocalDate.parse(v);
        } catch (Exception e) {
            return null;
        }
    }

    private static boolean has(String s) { return s != null && !s.isBlank(); }
    private static String str(Object o) { return o == null ? null : String.valueOf(o); }

    /** 红娘只能改本人日程，越权按 403 拦截 */
    private static void assertOwned(CalendarEvent e) {
        if (!RequestContext.isAgent()) return;
        String me = RequestContext.agentName();
        if (me != null && e.getAgent() != null && !me.equals(e.getAgent())) {
            throw BizException.forbidden("无权操作他人日程");
        }
    }
}
