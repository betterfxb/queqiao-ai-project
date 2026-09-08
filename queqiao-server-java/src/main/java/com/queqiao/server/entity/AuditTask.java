package com.queqiao.server.entity;

import jakarta.persistence.*;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

/** 审核事项 */
@Data
@Entity
@Table(name = "t_audit_task")
public class AuditTask {

    @Id
    @Column(length = 64)
    private String id;

    private String type;

    private String applicant;

    private String gender;

    private Integer age;

    private String memberId;

    private String agent;

    private String urgency;

    private String status;

    private String changeField;

    private String changeFrom;

    private String changeTo;

    private String payText;

    private Boolean paid;

    @Column(columnDefinition = "TEXT")
    private String reason;

    @Column(columnDefinition = "TEXT")
    private String material;

    private String handler;

    private LocalDateTime submittedAt;

    private LocalDateTime handledAt;

}
