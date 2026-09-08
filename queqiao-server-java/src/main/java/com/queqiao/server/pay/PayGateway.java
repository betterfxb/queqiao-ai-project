package com.queqiao.server.pay;

import com.queqiao.server.entity.FinanceOrder;

import java.util.Map;

/**
 * 支付渠道网关抽象。
 * 各渠道实现 prepay（生成预支付参数）与 verifyNotify（异步回调验签）。
 * 线下渠道信任门店，无需验签；微信/支付宝在密钥齐全时可用，SDK 接入层占位。
 */
public interface PayGateway {

    /** 渠道名：微信 / 支付宝 / 线下 */
    String channel();

    /** 是否已配置可用 */
    boolean available();

    /** 生成预支付参数 */
    PrepayResult prepay(FinanceOrder order);

    /** 校验异步回调签名 */
    boolean verifyNotify(Map<String, String> params, String sign);

    class PrepayResult {
        public boolean offline;
        public String payUrl;
        public String qrcode;
        public String channel;
        public Map<String, Object> params;
    }
}
