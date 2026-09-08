package com.queqiao.server.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

/** 会员投诉 / 举报（风控） */
@Data
@Entity
@Table(name = "t_member_report")
public class MemberReport {

    @Id
    @Column(length = 64)
    private String id;

    private String reporterId;

    private String reporterName;

    private String targetMemberId;

    private String targetName;

    /** 类型：欺诈 / 婚托 / 虚假资料 / 骚扰 / 其他 */
    private String type;

    @Column(columnDefinition = "TEXT")
    private String content;

    /** 状态：待处理 / 已受理 / 已驳回 / 已结案 */
    private String status;

    private String handler;

    @Column(columnDefinition = "TEXT")
    private String handleNote;

    private LocalDateTime handledAt;

    private LocalDateTime createdAt;
}
