package com.queqiao.server.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDateTime;

/** 风险事件（异常行为预警：重复注册 / 短时间多牵线 / 退款频繁 / 被举报 / 异常行为） */
@Data
@Entity
@Table(name = "t_risk_event")
public class RiskEvent {

    @Id
    @Column(length = 64)
    private String id;

    private String memberId;

    private String memberName;

    /** 类型：重复注册 / 短时间多牵线 / 退款频繁 / 被举报 / 异常行为 */
    private String type;

    private Integer score;

    @Column(columnDefinition = "TEXT")
    private String detail;

    /** 救助引导文案（反诈类风险事件专用，被害人救助路径） */
    @Column(columnDefinition = "TEXT")
    private String guidance;

    private Boolean handled;

    private String handler;

    @Column(columnDefinition = "TEXT")
    private String handleNote;

    private LocalDateTime handledAt;

    private LocalDateTime createdAt;
}
