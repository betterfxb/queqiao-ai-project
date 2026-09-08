package com.queqiao.server.pay;

import com.queqiao.server.config.PayConfig;
import com.queqiao.server.entity.FinanceOrder;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 微信支付网关（接入层占位）。
 * 密钥由 {@link PayConfig} 从环境变量读取，未配置时 prepay/验签均不可用并给出清晰指引。
 * 真实环境在此接入 wechatpay-java SDK：prepay 生成 JSAPI prepay_id，verifyNotify 校验平台证书签名。
 */
@Component
public class WechatGateway implements PayGateway {

    private final PayConfig cfg;

    public WechatGateway(PayConfig cfg) {
        this.cfg = cfg;
    }

    @Override
    public String channel() {
        return "微信";
    }

    @Override
    public boolean available() {
        return cfg.isWechatConfigured();
    }

    @Override
    public PrepayResult prepay(FinanceOrder order) {
        if (!available()) {
            throw new IllegalStateException(
                    "微信支付未配置：请设置环境变量 WECHAT_APP_ID / WECHAT_MCH_ID / WECHAT_API_KEY（密钥走环境变量，绝不明文）");
        }
        PrepayResult r = new PrepayResult();
        r.channel = "微信";
        r.params = Map.of(
                "appId", cfg.getWechatAppId(),
                "mchId", cfg.getWechatMchId(),
                "notifyUrl", cfg.getWechatNotifyUrl(),
                "note", "SDK 未接入：配置就绪后在此生成 JSAPI 预支付参数");
        return r;
    }

    @Override
    public boolean verifyNotify(Map<String, String> params, String sign) {
        if (!available()) return false;
        // 接入层占位：校验微信支付 v3 回调签名（平台证书 / APIv3 密钥）
        return true;
    }
}
