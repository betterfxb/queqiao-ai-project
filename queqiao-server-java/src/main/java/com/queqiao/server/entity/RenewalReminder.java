package com.queqiao.server.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 续费提醒（1-G）：直接引用真实交易订单（FinanceOrder），到期日来自订单，
 * 与 finance 同源，消除「会员 expire 字段」与「财务订单」双源口径。
 * 续费成功后由 FinanceService 调 closeReminder(orderId) 自动关闭。
 */
@Data
@Entity
@Table(name = "t_renewal_reminder")
public class RenewalReminder {

    @Id
    @Column(length = 64)
    private String id;

    /** 关联的真实订单 id（续费/新购/增值且已完成支付） */
    private String orderId;

    private String memberId;

    @Column(name = "member_name")
    private String memberName;

    private String agent;

    /** 到期日：来自订单 paidAt + 1 年（或订单既有到期字段） */
    private LocalDate expireDate;

    /** open=待续费 / closed=已续费关闭 */
    private String status;

    private LocalDateTime createdAt;

    private LocalDateTime closedAt;
}
