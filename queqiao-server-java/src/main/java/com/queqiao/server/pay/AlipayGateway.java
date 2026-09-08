package com.queqiao.server.pay;

import com.queqiao.server.config.PayConfig;
import com.queqiao.server.entity.FinanceOrder;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * 支付宝网关（接入层占位）。
 * 密钥由 {@link PayConfig} 从环境变量读取，未配置时不可用并给出清晰指引。
 * 真实环境在此接入 alipay-sdk：prepay 生成交易串，verifyNotify 用支付宝公钥验签。
 */
@Component
public class AlipayGateway implements PayGateway {

    private final PayConfig cfg;

    public AlipayGateway(PayConfig cfg) {
        this.cfg = cfg;
    }

    @Override
    public String channel() {
        return "支付宝";
    }

    @Override
    public boolean available() {
        return cfg.isAlipayConfigured();
    }

    @Override
    public PrepayResult prepay(FinanceOrder order) {
        if (!available()) {
            throw new IllegalStateException(
                    "支付宝未配置：请设置环境变量 ALIPAY_APP_ID / ALIPAY_PRIVATE_KEY / ALIPAY_PUBLIC_KEY（密钥走环境变量，绝不明文）");
        }
        PrepayResult r = new PrepayResult();
        r.channel = "支付宝";
        r.params = Map.of(
                "appId", cfg.getAlipayAppId(),
                "notifyUrl", cfg.getAlipayNotifyUrl(),
                "note", "SDK 未接入：配置就绪后在此生成支付宝交易串");
        return r;
    }

    @Override
    public boolean verifyNotify(Map<String, String> params, String sign) {
        if (!available()) return false;
        // 接入层占位：用支付宝公钥校验异步通知签名
        return true;
    }
}
