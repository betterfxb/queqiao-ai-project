package com.queqiao.server.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.math.BigDecimal;

import java.time.LocalDateTime;

/**
 * 转介绍关系（老带新）。
 * 生命周期：已登记 → 已转化（被推荐人首次付费）→ 已发奖。
 * 幂等：同一 (referrerId + refereePhone) 只登记一条；已发奖不重复计金额。
 */
@Data
@Entity
@Table(name = "t_referral")
public class Referral {

    @Id
    @Column(length = 64)
    private String id;

    /** 推荐人类型：会员 / 红娘 */
    private String referrerType;

    private String referrerId;

    private String referrerName;

    /** 被推荐人对应线索 id（可空） */
    private String refereeLeadId;

    /** 被推荐人转化后的会员 id（可空，付费转化时回填） */
    private String refereeMemberId;

    private String refereeName;

    private String refereePhone;

    /** 已登记 / 已转化 / 已发奖 / 已失效 */
    private String status;

    /** 奖励形式：佣金 / 优惠券 / 服务折扣 */
    private String rewardType;

    /** 奖励金额（元） */
    private BigDecimal rewardAmount;

    /** 触发转化的订单 id */
    private String orderId;

    /** 触发转化的订单金额 */
    private BigDecimal orderAmount;

    private LocalDateTime convertedAt;

    private LocalDateTime rewardedAt;

    private String operator;

    @Column(columnDefinition = "TEXT")
    private String remark;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;
}
