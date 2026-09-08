package com.queqiao.server.ai;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import com.queqiao.server.service.SettingService;

import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * AI 能力代理：转发至 Python FastAPI 服务（:8090）。
 * AI 不可用时返回 null，由调用方降级为规则算法，保证业务不中断。
 */
@Component
public class AiClient {

    private static final Logger log = LoggerFactory.getLogger(AiClient.class);

    /** 熔断阈值：连续失败达到该次数后进入熔断 */
    private static final int FAIL_THRESHOLD = 3;
    /** 熔断持续时间：期间直接返回 null 走本地降级，不再发起网络调用 */
    private static final long OPEN_MS = 30_000L;
    /** 健康探测结果缓存时长 */
    private static final long HEALTH_TTL_MS = 5_000L;

    private final RestTemplate rest = new RestTemplate();
    private final java.util.concurrent.atomic.AtomicInteger fails = new java.util.concurrent.atomic.AtomicInteger();
    private final java.util.concurrent.atomic.AtomicLong openUntil = new java.util.concurrent.atomic.AtomicLong();
    private final java.util.concurrent.atomic.AtomicLong healthCachedUntil = new java.util.concurrent.atomic.AtomicLong();
    private volatile boolean healthCached;

    @Value("${ai.base:http://localhost:8090}")
    private String aiBase;

    private final SettingService settings;

    public AiClient(SettingService settings) {
        this.settings = settings;
        // 使用 JDK HttpClient：内置连接复用（keep-alive 连接池），避免每次调用新建 TCP 连接
        java.net.http.HttpClient httpClient = java.net.http.HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(2000))
                .version(java.net.http.HttpClient.Version.HTTP_1_1)
                .build();
        var factory = new org.springframework.http.client.JdkClientHttpRequestFactory(httpClient);
        factory.setReadTimeout(Duration.ofMillis(8000));
        rest.setRequestFactory(factory);
    }

    /** 熔断是否打开（打开期间调用方直接走本地降级，避免请求线程被拖垮） */
    public boolean circuitOpen() {
        return System.currentTimeMillis() < openUntil.get();
    }

    private void onSuccess() {
        fails.set(0);
        openUntil.set(0);
    }

    private void onFailure(String path, String msg) {
        int n = fails.incrementAndGet();
        if (n >= FAIL_THRESHOLD && !circuitOpen()) {
            openUntil.set(System.currentTimeMillis() + OPEN_MS);
            log.warn("[ai] 连续失败 {} 次，熔断 {}s，期间走本地降级", n, OPEN_MS / 1000);
        }
        log.warn("[ai] call {} failed: {}", path, msg);
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> forward(String path, Map<String, Object> body) {
        // 总开关：系统设置「启用 AI 能力」关闭时，统一走本地降级，避免任何 AI 调用
        if (!settings.getBool("ai.enabled", true)) return null;
        if (circuitOpen()) return null;
        try {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            HttpEntity<Map<String, Object>> entity = new HttpEntity<>(body, headers);
            ResponseEntity<Map> resp = rest.postForEntity(aiBase + path, entity, Map.class);
            onSuccess();
            return (Map<String, Object>) resp.getBody();
        } catch (Exception e) {
            onFailure(path, e.getMessage());
            return null;
        }
    }

    /** 文本实体提取（自然语言 -> 结构化会员画像） */
    public Map<String, Object> parse(String text) {
        return forward("/parse", Map.of("text", text));
    }

    /** 智能匹配打分 */
    public Map<String, Object> match(Map<String, Object> target, List<Map<String, Object>> candidates) {
        return forward("/match", Map.of("target", target, "candidates", candidates));
    }

    /** 审核建议 */
    public Map<String, Object> auditSuggest(Map<String, Object> payload) {
        return forward("/audit-suggest", payload);
    }

    /** 沟通话术建议 */
    public Map<String, Object> replySuggest(Map<String, Object> payload) {
        return forward("/reply-suggest", payload);
    }

    /** 红娘分配建议 */
    public Map<String, Object> assignSuggest(Map<String, Object> payload) {
        return forward("/assign-suggest", payload);
    }

    /** 下一步行动建议 */
    public Map<String, Object> nextAction(Map<String, Object> payload) {
        return forward("/next-action", payload);
    }

    /** 会员画像教练：完整度判断 + 补全清单 */
    public Map<String, Object> coachProfile(Map<String, Object> payload) {
        return forward("/coach/profile", payload);
    }

    /** 约会教练：基于牵线进展的下一步约会建议 + 话题/穿搭话术库 */
    public Map<String, Object> coachNextStep(Map<String, Object> payload) {
        return forward("/coach/next-step", payload);
    }

    /** 内容风控：发送前的合规检测与脱敏 */
    public Map<String, Object> moderate(Map<String, Object> payload) {
        return forward("/moderate", payload);
    }

    /** 反馈学习：上采纳/否决，返回最新维度权重 */
    public Map<String, Object> feedback(Map<String, Object> payload) {
        return forward("/feedback", payload);
    }

    /** 反馈学习：当前权重与采纳率 */
    @SuppressWarnings("unchecked")
    public Map<String, Object> feedbackStats() {
        if (circuitOpen()) return null;
        try {
            ResponseEntity<Map> r = rest.getForEntity(aiBase + "/feedback/stats", Map.class);
            onSuccess();
            return (Map<String, Object>) r.getBody();
        } catch (Exception e) {
            onFailure("/feedback/stats", e.getMessage());
            return null;
        }
    }

    /** 会员跟进摘要：汇总跟进记录，给出意向判断与下一步建议 */
    public Map<String, Object> followupSummary(Map<String, Object> payload) {
        return forward("/followup-summary", payload);
    }

    /**
     * 健康探测：会主动清除熔断（用于运维手动恢复与前端状态展示）。
     * 结果缓存 {@value #HEALTH_TTL_MS} ms，避免 /health 等高频接口每次都同步打一次 AI 服务。
     */
    public boolean health() {
        long now = System.currentTimeMillis();
        if (now < healthCachedUntil.get()) return healthCached;
        boolean ok = probeHealth();
        healthCached = ok;
        healthCachedUntil.set(now + HEALTH_TTL_MS);
        return ok;
    }

    private boolean probeHealth() {
        try {
            ResponseEntity<Map> r = rest.getForEntity(aiBase + "/health", Map.class);
            boolean ok = r.getStatusCode().is2xxSuccessful();
            if (ok) onSuccess();
            return ok;
        } catch (Exception e) {
            return false;
        }
    }
}
