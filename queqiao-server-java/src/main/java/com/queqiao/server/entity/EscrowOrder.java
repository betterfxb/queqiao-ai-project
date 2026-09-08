package com.queqiao.server.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 预付资金存管订单（3-D 高客单价套餐资金安全）。
 *
 * <p>高客单价套餐的预付款进入「存管」而非直接入账服务商：先 {@code 待托管}，
 * 通道确认后 {@code 已托管}，服务履约完成后 {@code 已放款}（资金释放给服务商），
 * 争议/未履约则 {@code 退款中} → {@code 已退款}。每个状态变更均有时间戳留痕，
 * 实现「退费透明」——会员与平台随时可查资金处于哪一环。</p>
 *
 * <p>外部通道（数字人民币运营机构 / 银行存管网关）当前以离线桩模拟，
 * 真实接入点见 {@code EscrowChannel} 实现类注释。</p>
 */
@Data
@Entity
@Table(name = "t_escrow_order")
public class EscrowOrder {

    @Id
    @Column(length = 64)
    private String id;

    private String memberId;

    private String memberName;

    /** 关联套餐/服务产品 */
    private String packageId;

    private String packageName;

    /** 关联的原始交易订单（FinanceOrder.id），实现「套餐购买 → 资金托管」闭环溯源 */
    private String financeOrderId;

    /** 托管金额（分） */
    private Long amount;

    /** 币种：rmb / digital-rmb（数字人民币） */
    private String currency;

    /** 存管通道：digital-rmb / bank-escrow */
    private String channel;

    /** 状态：待托管 / 已托管 / 放款中 / 已放款 / 退款中 / 已退款 */
    private String status;

    /** 通道侧存管流水号（托管成功后回填） */
    private String escrowNo;

    private LocalDateTime paidAt;

    private LocalDateTime holdAt;

    private LocalDateTime releasedAt;

    /** 退款金额（分，退费透明） */
    private Long refundAmount;

    /** 退款原因（退费透明） */
    @Column(columnDefinition = "TEXT")
    private String refundReason;

    private LocalDateTime refundAt;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
