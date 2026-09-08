package com.queqiao.server.service;

import org.springframework.stereotype.Component;

/**
 * 银行存管通道（离线桩）。
 * 真实环境：在 hold 内调用银行存管网关（配置项 escrow.bank-escrow.gateway / .ak / .sk），
 * 用网关返回的存管流水号替换下方仿真号即可。
 */
@Component
public class BankEscrowChannel implements EscrowChannel {

    @Override
    public String name() {
        return "bank-escrow";
    }

    @Override
    public String hold(String orderId, long amount, String currency) {
        // 离线桩：真实环境此处发起银行资金存管并回填通道流水号
        return "BESC-" + orderId;
    }
}
