package com.queqiao.server.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.math.BigDecimal;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** 交易/订单记录 */
@Data
@Entity
@Table(name = "t_finance_order")
public class FinanceOrder {

    @Id
    @Column(length = 64)
    private String id;

    private String orderNo;

    /** 关联原订单 id（退款单指向被退的订单） */
    @Column(name = "order_id")
    private String orderId;

    private String memberId;

    @Column(name = "member_name")
    private String member;

    @Column(name = "order_type")
    private String type;

    /** 关联服务产品（SKU） */
    private String productId;

    /** 冗余存储产品名，便于财务统计按 SKU 分组 */
    private String productName;

    /** 订单业务类型：新购 / 续费 / 增值 / 退款 */
    @Column(name = "biz_type")
    private String orderType;

    private BigDecimal amount;

    private String status;

    private String agent;

    /** 支付渠道：微信 / 支付宝 / 线下 */
    private String channel;

    private String remark;

    /** 会员服务到期日（1-G：续费提醒同源依据；为空则按 createdAt+1 年推导） */
    @Column(name = "expire_date")
    private LocalDate expireDate;

    /** 第三方交易号（支付回调写入） */
    private String tradeNo;

    /** 实际支付时间 */
    private LocalDateTime paidAt;

    /** 使用的优惠券 id（抵现券） */
    private String couponId;

    /** 优惠券抵扣金额 */
    private BigDecimal couponAmount;

    /** 退款原因 */
    private String refundReason;

    @Column(name = "trade_time")
    private String time;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

}
