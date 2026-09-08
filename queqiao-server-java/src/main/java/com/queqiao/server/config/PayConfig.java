package com.queqiao.server.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 支付配置：密钥全部来自环境变量（绝不明文）。
 * 本地默认 offline（线下模式），工程闭环可跑通；配置微信/支付宝密钥后自动可用。
 *
 * 环境变量（生产由 K8s Secret / 配置中心注入）：
 *   WECHAT_APP_ID / WECHAT_MCH_ID / WECHAT_API_KEY / WECHAT_NOTIFY_URL
 *   ALIPAY_APP_ID / ALIPAY_PRIVATE_KEY / ALIPAY_PUBLIC_KEY / ALIPAY_NOTIFY_URL
 */
@Component
public class PayConfig {

    @Value("${qq.pay.mode:offline}")
    private String mode;

    @Value("${qq.pay.wechat.app-id:}")
    private String wechatAppId;
    @Value("${qq.pay.wechat.mch-id:}")
    private String wechatMchId;
    @Value("${qq.pay.wechat.api-key:}")
    private String wechatApiKey;
    @Value("${qq.pay.wechat.notify-url:}")
    private String wechatNotifyUrl;

    @Value("${qq.pay.alipay.app-id:}")
    private String alipayAppId;
    @Value("${qq.pay.alipay.private-key:}")
    private String alipayPrivateKey;
    @Value("${qq.pay.alipay.public-key:}")
    private String alipayPublicKey;
    @Value("${qq.pay.alipay.notify-url:}")
    private String alipayNotifyUrl;

    public String getMode() { return mode; }

    public boolean isWechatConfigured() {
        return notBlank(wechatAppId) && notBlank(wechatMchId) && notBlank(wechatApiKey);
    }

    public boolean isAlipayConfigured() {
        return notBlank(alipayAppId) && notBlank(alipayPrivateKey) && notBlank(alipayPublicKey);
    }

    /** 某渠道是否可用（线下始终可用；微信/支付宝需密钥齐全） */
    public boolean channelAvailable(String channel) {
        if (channel == null) return false;
        return switch (channel) {
            case "微信" -> isWechatConfigured();
            case "支付宝" -> isAlipayConfigured();
            case "线下" -> true;
            default -> false;
        };
    }

    public String getWechatAppId() { return wechatAppId; }
    public String getWechatMchId() { return wechatMchId; }
    public String getWechatNotifyUrl() { return wechatNotifyUrl; }
    public String getAlipayAppId() { return alipayAppId; }
    public String getAlipayNotifyUrl() { return alipayNotifyUrl; }

    private static boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }
}
