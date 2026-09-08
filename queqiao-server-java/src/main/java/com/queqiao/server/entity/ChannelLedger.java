package com.queqiao.server.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.math.BigDecimal;

import java.time.LocalDateTime;

/**
 * 渠道侧流水：支付渠道回推的交易记录，作为对账的「权威来源」。
 * 本地订单（t_finance_order）支付成功时由 {@link com.queqiao.server.service.FinanceService}
 * 同步写入一条 PAID 记录；退款时写入一条 REFUND 记录。
 */
@Data
@Entity
@Table(name = "t_channel_ledger")
public class ChannelLedger {

    @Id
    @Column(length = 64)
    private String id;

    /** 关联本地订单 id */
    private String orderId;

    /** 关联本地订单号（冗余，便于排查） */
    private String orderNo;

    /** 第三方交易号 */
    private String tradeNo;

    /** 支付渠道：微信 / 支付宝 / 线下 */
    private String channel;

    /** 金额（退款为负数） */
    private BigDecimal amount;

    /** PAID 收款成功 / REFUND 退款 */
    private String status;

    /** 渠道侧支付时间 */
    private LocalDateTime paidAt;

    private LocalDateTime createdAt;
}
