package com.queqiao.server.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.math.BigDecimal;

import java.time.LocalDateTime;

/**
 * 优惠券（抵现券）。
 * 来源：转介绍奖励自动发放（sourceType=REFERRAL，幂等按 sourceId）、管理员手动发放（MANUAL）。
 * 生命周期：未使用 → 已使用（支付成功核销）→ 已过期（超过 expireAt）。
 */
@Data
@Entity
@Table(name = "t_coupon")
public class Coupon {

    @Id
    @Column(length = 64)
    private String id;

    /** 券码（展示用，唯一） */
    @Column(unique = true)
    private String code;

    private String memberId;

    private String memberName;

    /** 抵扣金额（元） */
    private BigDecimal amount;

    /** 类型：抵现（当前实现） */
    private String type;

    /** 状态：未使用 / 已使用 / 已过期 */
    private String status;

    /** 来源 id（如转介绍奖励 id） */
    private String sourceId;

    /** 来源类型：REFERRAL / MANUAL */
    private String sourceType;

    /** 核销所用订单 id */
    private String orderId;

    private LocalDateTime expireAt;

    private LocalDateTime usedAt;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
