package com.queqiao.server.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.math.BigDecimal;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 对账差异日志（t_pay_reconcile）：本地订单与渠道流水比对后不一致的记录。
 * 对账无差异时不落库，仅差异可查。
 */
@Data
@Entity
@Table(name = "t_pay_reconcile")
public class PayReconcile {

    @Id
    @Column(length = 64)
    private String id;

    /** 对账日期（按天） */
    private LocalDate reconcileDate;

    private String orderId;

    private String orderNo;

    private String channel;

    private String tradeNo;

    /** 本地订单金额 */
    private BigDecimal localAmount;

    /** 渠道流水金额 */
    private BigDecimal channelAmount;

    /** 差异金额（本地 - 渠道） */
    private BigDecimal diff;

    /** 是否吻合（差异行恒为 false，保留字段便于扩展） */
    private Boolean matched;

    private String note;

    private LocalDateTime createdAt;
}
