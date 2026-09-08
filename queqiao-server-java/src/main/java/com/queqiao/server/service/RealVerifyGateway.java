package com.queqiao.server.service;

import com.queqiao.server.common.BizException;
import com.queqiao.server.config.VerifyConfig;
import com.queqiao.server.entity.VerifyTask;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;

/**
 * 实时核验网关（主实现，@Primary）。
 * 取代原先统一的「人工核验留痕」占位：第三方渠道（学信网/公安/实名/材料）默认走实时核验，
 * 仅显式 channel=人工 时退回人工复核（待核验），保留人工兜底能力。
 *
 * 三种模式（由 {@link VerifyConfig} 控制）：
 *   offline  委托 ManualVerifyGateway，返回 待核验（人工复核）
 *   sandbox  本地确定性模拟，无需外部依赖，演示实时回写路径
 *   real     真实第三方：密钥来自环境变量，未配置即抛清晰错误；已配置则 HTTP 调用
 */
@Service
@Primary
public class RealVerifyGateway implements VerifyGateway {

    private final VerifyConfig cfg;
    private final ManualVerifyGateway manual;
    private final HttpClient http;
    private final ObjectMapper om = new ObjectMapper();

    public RealVerifyGateway(VerifyConfig cfg, ManualVerifyGateway manual) {
        this.cfg = cfg;
        this.manual = manual;
        this.http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(5))
                .build();
    }

    public String getMode() { return cfg.getMode(); }
    public boolean isConfigured() { return cfg.isConfigured(); }
    public boolean available() { return cfg.available(); }

    @Override
    public VerifyResult submit(VerifyTask task) {
        String mode = cfg.getMode();
        if ("offline".equalsIgnoreCase(mode)) {
            return manual.submit(task);
        }
        if ("real".equalsIgnoreCase(mode)) {
            return realSubmit(task);
        }
        // sandbox（默认）：本地确定性模拟
        return sandboxSubmit(task);
    }

    /** 沙箱模拟：人工渠道转人工复核；其余根据凭证有效性确定性返回 */
    private VerifyResult sandboxSubmit(VerifyTask task) {
        String channel = nz(task.getChannel());
        if (channel.contains("人工")) {
            return new VerifyResult("待核验", "人工复核：请运营在审核页录入核验结果");
        }
        String proof = nz(task.getRawProof()).toLowerCase();
        boolean fail = proof.contains("invalid")
                || proof.contains("fail")
                || proof.contains("不符")
                || proof.contains("失效")
                || proof.contains("fake");
        if (fail) {
            return new VerifyResult("未通过", "沙箱实时核验未通过：提交材料存疑，转人工复核");
        }
        return new VerifyResult("已通过", "沙箱实时核验通过");
    }

    /** 真实第三方：密钥缺失清晰报错；已配置则携带密钥 HTTP 调用并解析结果 */
    private VerifyResult realSubmit(VerifyTask task) {
        if (!cfg.isConfigured()) {
            throw new BizException("实时核验通道未配置：请设置环境变量 QQ_VERIFY_API_KEY / "
                    + "QQ_VERIFY_API_SECRET / QQ_VERIFY_API_BASE_URL（密钥走环境变量，绝不明文）");
        }
        String url = cfg.getApiBaseUrl().replaceAll("/+$", "") + "/verify";
        Map<String, Object> body = Map.of(
                "memberId", nz(task.getMemberId()),
                "memberName", nz(task.getMemberName()),
                "type", nz(task.getType()),
                "channel", nz(task.getChannel()),
                "rawProof", nz(task.getRawProof()));
        try {
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(8))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + cfg.getApiKey())
                    .header("X-Verify-Secret", nz(cfg.getApiSecret()))
                    .POST(HttpRequest.BodyPublishers.ofString(om.writeValueAsString(body)))
                    .build();
            HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() != 200) {
                throw new BizException("实时核验服务返回异常状态码 " + resp.statusCode());
            }
            Map<String, Object> m = om.readValue(resp.body(), Map.class);
            String status = String.valueOf(m.getOrDefault("status", "待核验"));
            String note = String.valueOf(m.getOrDefault("note", "第三方核验完成"));
            return new VerifyResult(status, note);
        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            throw new BizException("实时核验调用失败：" + e.getMessage());
        }
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }
}
