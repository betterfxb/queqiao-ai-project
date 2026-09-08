package com.queqiao.server.service;

/**
 * 存管通道策略接口（3-D）。
 *
 * <p>不同「高客单价套餐」可走不同资金存管通道。当前实现为离线桩，
 * 返回仿真存管流水号，便于无外部密钥时完整跑通存管状态机与退费透明链路。</p>
 *
 * <p>真实接入：在对应实现类 {@code hold} 内调用第三方网关（数字人民币运营机构 / 银行存管），
 * 用配置项（如 {@code escrow.digital-rmb.gateway} / {@code escrow.bank-escrow.gateway}）注入密钥与地址，
 * 将网关返回的存管流水号回填即可，状态机与上层服务无需改动。</p>
 */
public interface EscrowChannel {

    /** 通道标识，与 EscrowOrder.channel 对齐 */
    String name();

    /**
     * 发起资金托管，返回通道侧存管流水号。
     *
     * @param orderId  本系统存管订单号
     * @param amount   托管金额（分）
     * @param currency 币种
     */
    String hold(String orderId, long amount, String currency);
}
