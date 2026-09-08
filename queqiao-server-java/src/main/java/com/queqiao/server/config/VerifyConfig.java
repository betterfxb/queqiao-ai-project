package com.queqiao.server.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 核验配置：第三方实时核验的密钥全部来自环境变量（绝不明文）。
 * 本地默认 sandbox（沙箱模拟实时核验，工程闭环可跑通）；配置真实密钥后切 real 即对接。
 *
 * 环境变量（生产由 K8s Secret / 配置中心注入）：
 *   QQ_VERIFY_API_KEY       实时核验服务 API Key
 *   QQ_VERIFY_API_SECRET    实时核验服务 API Secret（签名 / 头校验用）
 *   QQ_VERIFY_API_BASE_URL  实时核验服务地址，如 https://verify.example.com
 *
 * mode 取值：
 *   offline  人工核验留痕（待核验，由运营录入结果）—— 兼容旧行为
 *   sandbox  沙箱模拟：本地确定性返回 已通过/未通过，无需外部依赖，演示实时回写路径
 *   real     真实第三方：需配置 QQ_VERIFY_API_KEY 等，否则提交即报错（清晰指引）
 */
@Component
public class VerifyConfig {

    @Value("${qq.verify.mode:sandbox}")
    private String mode;

    @Value("${qq.verify.api-key:}")
    private String apiKey;

    @Value("${qq.verify.api-secret:}")
    private String apiSecret;

    @Value("${qq.verify.api-base-url:}")
    private String apiBaseUrl;

    public String getMode() {
        return mode == null ? "sandbox" : mode;
    }

    /** 真实模式是否具备调用条件（密钥 + 地址齐全） */
    public boolean isConfigured() {
        return notBlank(apiKey) && notBlank(apiBaseUrl);
    }

    public boolean isReal() {
        return "real".equalsIgnoreCase(getMode());
    }

    /** 当前模式是否可用：real 需密钥齐全；offline / sandbox 始终可用 */
    public boolean available() {
        if (isReal()) return isConfigured();
        return true;
    }

    public String getApiKey() { return apiKey; }
    public String getApiSecret() { return apiSecret; }
    public String getApiBaseUrl() { return apiBaseUrl; }

    private static boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }
}
