package com.queqiao.server.service;

import org.springframework.stereotype.Component;

/**
 * 数字人民币存管通道（离线桩）。
 * 真实环境：在 hold 内调用数字人民币运营机构网关（配置项 escrow.digital-rmb.gateway / .ak / .sk），
 * 用网关返回的存管凭证号替换下方仿真号即可。
 */
@Component
public class DigitalRmbEscrowChannel implements EscrowChannel {

    @Override
    public String name() {
        return "digital-rmb";
    }

    @Override
    public String hold(String orderId, long amount, String currency) {
        // 离线桩：真实环境此处发起数字人民币存管并回填通道凭证号
        return "DCNY-" + orderId;
    }
}
