package com.queqiao.server.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.math.BigDecimal;

import java.time.LocalDateTime;

/**
 * 产业链导流单：成功脱单 → 推荐商户 → 成交 → 分佣 → 结算。
 * 状态流：已导流 → 已成交 → 已结算（任意阶段可 已取消）。
 * 幂等：已成交/已结算不重复计佣；已结算重复结算直接返回。
 */
@Data
@Entity
@Table(name = "t_merchant_order")
public class MerchantOrder {

    @Id
    @Column(length = 64)
    private String id;

    private String merchantId;

    private String merchantName;

    private String category;

    private String memberId;

    private String memberName;

    /** 来源牵线 id（可空） */
    private String pairId;

    /** 商户侧成交金额 */
    private BigDecimal amount;

    /** 快照分佣比例（%） */
    private Double commissionRate;

    /** 我方分佣金额 */
    private BigDecimal commissionAmount;

    /** 已导流 / 已成交 / 已结算 / 已取消 */
    private String status;

    private String agent;

    @Column(columnDefinition = "TEXT")
    private String note;

    private LocalDateTime createdAt;

    private LocalDateTime dealAt;

    private LocalDateTime settledAt;
}
