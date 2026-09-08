package com.queqiao.server.service;

import com.queqiao.server.ai.AiClient;
import com.queqiao.server.entity.AiFeedback;
import com.queqiao.server.repo.AiFeedbackRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;

/**
 * AI 反馈学习编排。
 *
 * <p>风险防范设计：</p>
 * <ul>
 *   <li><b>事实与状态分离</b>：反馈事实持久化在业务库，AI 服务只持有可重建的内存权重，
 *       任一方重启都不会丢数据、也不会产生权重漂移。</li>
 *   <li><b>幂等回灌</b>：回灌走 replay 模式（AI 侧先复位再全量应用），
 *       重复执行结果一致，可安全重试。</li>
 *   <li><b>自愈</b>：定时对账检测 AI 侧已应用样本量，若少于本地事实数（说明 AI 重启过），
 *       自动触发一次回灌。</li>
 *   <li><b>降级</b>：AI 不可用时仅记录本地事实，业务链路不受影响。</li>
 * </ul>
 */
@Service
public class AiFeedbackService {

    private static final Logger log = LoggerFactory.getLogger(AiFeedbackService.class);

    /** 单次回灌上限，避免历史数据过多时一次性请求体过大 */
    private static final int REPLAY_LIMIT = 2000;

    private final AiFeedbackRepository repo;
    private final AiClient ai;
    private final AtomicLong lastReplayAt = new AtomicLong(0);

    public AiFeedbackService(AiFeedbackRepository repo, AiClient ai) {
        this.repo = repo;
        this.ai = ai;
    }

    /** 记录一条反馈并增量推送给 AI（推送失败不影响落库） */
    public Map<String, Object> record(AiFeedback fb) {
        fb.setCreatedAt(LocalDateTime.now());
        repo.save(fb);

        Map<String, Object> res = ai.feedback(Map.of("items", List.of(toItem(fb)), "replay", false));
        if (res == null) {
            return Map.of("ok", true, "synced", false,
                    "message", "反馈已记录，AI 服务暂不可用，将在恢复后自动回灌");
        }
        Map<String, Object> out = new LinkedHashMap<>(res);
        out.put("ok", true);
        out.put("synced", true);
        return out;
    }

    /** 全量回灌：让 AI 侧权重与本地事实完全一致（幂等） */
    public Map<String, Object> replayAll() {
        List<AiFeedback> all = repo.findBySceneOrderByIdAsc("match");
        if (all.size() > REPLAY_LIMIT) {
            all = all.subList(all.size() - REPLAY_LIMIT, all.size());
        }
        List<Map<String, Object>> items = all.stream().map(AiFeedbackService::toItem).toList();
        Map<String, Object> res = ai.feedback(Map.of("items", items, "replay", true));
        lastReplayAt.set(System.currentTimeMillis());
        if (res == null) {
            return Map.of("ok", false, "replayed", 0, "message", "AI 服务不可用，回灌未生效");
        }
        Map<String, Object> out = new LinkedHashMap<>(res);
        out.put("ok", true);
        out.put("replayed", items.size());
        return out;
    }

    /** 启动后异步回灌一次，不阻塞应用就绪（统一走 aiExecutor 线程池，不再裸起线程） */
    @EventListener(ApplicationReadyEvent.class)
    @Async("aiExecutor")
    public void onReady() {
        try {
            Thread.sleep(3000);
            long n = repo.count();
            if (n == 0) return;
            Map<String, Object> r = replayAll();
            log.info("[ai-feedback] 启动回灌完成: {} 条", r.get("replayed"));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } catch (Exception e) {
            log.warn("[ai-feedback] 启动回灌失败: {}", e.getMessage());
        }
    }

    /** 每 10 分钟与 AI 侧对账，发现权重被重置（AI 重启）则自动回灌 */
    @Scheduled(fixedDelay = 600_000L, initialDelay = 600_000L)
    public void reconcile() {
        try {
            long local = repo.count();
            if (local == 0) return;
            Map<String, Object> stats = ai.feedbackStats();
            if (stats == null) return;
            Object totalObj = stats.get("total");
            long remote = 0;
            if (totalObj instanceof Map<?, ?> t) {
                remote = num(t.get("accept")) + num(t.get("reject"));
            }
            if (remote < local) {
                Map<String, Object> r = replayAll();
                log.info("[ai-feedback] 对账发现差异 (AI={} 本地={})，已回灌 {} 条", remote, local, r.get("replayed"));
            }
        } catch (Exception e) {
            log.warn("[ai-feedback] 对账失败: {}", e.getMessage());
        }
    }

    public List<AiFeedback> recent() {
        return repo.findTop200ByOrderByIdDesc();
    }

    /** 本地反馈总量 / 采纳率（AI 不可用时前端也有数可看） */
    public Map<String, Object> localSummary() {
        List<AiFeedback> all = repo.findAll();
        long accept = all.stream().filter(f -> "accept".equals(f.getAction())).count();
        long reject = all.size() - accept;
        Map<String, Long> byDim = new LinkedHashMap<>();
        for (AiFeedback f : all) {
            for (String d : splitDims(f.getDimensions())) {
                byDim.merge(d, 1L, Long::sum);
            }
        }
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("total", all.size());
        m.put("accept", accept);
        m.put("reject", reject);
        m.put("acceptRate", all.isEmpty() ? null : Math.round(accept * 1000.0 / all.size()) / 10.0);
        m.put("byDimension", byDim);
        m.put("lastReplayAt", lastReplayAt.get() == 0 ? null : lastReplayAt.get());
        return m;
    }

    static List<String> splitDims(String s) {
        if (s == null || s.isBlank()) return List.of();
        return Arrays.stream(s.split(",")).map(String::trim).filter(x -> !x.isEmpty()).toList();
    }

    private static Map<String, Object> toItem(AiFeedback f) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("scene", f.getScene() == null ? "match" : f.getScene());
        m.put("itemId", f.getItemId() == null ? "" : f.getItemId());
        m.put("action", f.getAction() == null ? "accept" : f.getAction());
        m.put("dimensions", splitDims(f.getDimensions()));
        m.put("reason", f.getReason() == null ? "" : f.getReason());
        m.put("operator", f.getOperator() == null ? "" : f.getOperator());
        return m;
    }

    private static long num(Object o) {
        return o instanceof Number n ? n.longValue() : 0L;
    }
}
